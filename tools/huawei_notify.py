#!/usr/bin/env python3
"""Read device information or manage per-package Doze exemptions through ADB.

Run without arguments for read-only diagnosis. State stays on the computer.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone

PACKAGE = re.compile(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+")


class GuardError(Exception):
    pass


def validate_packages(packages):
    clean = []
    for package in packages:
        if not package or len(package) > 240 or not PACKAGE.fullmatch(package):
            raise GuardError("Invalid package name: " + repr(package))
        if package not in clean:
            clean.append(package)
    return clean


def parse_whitelist(output):
    result = {}
    for line in output.splitlines():
        fields = line.strip().split(",")
        if len(fields) >= 2 and fields[0] in ("system", "system-excidle", "user") and PACKAGE.fullmatch(fields[1]):
            result.setdefault(fields[1], set()).add(fields[0])
    return result


class Adb:
    def __init__(self, executable="adb", serial=None):
        self.executable = shutil.which(executable)
        if not self.executable:
            raise GuardError("ADB not found. Install official Android SDK Platform Tools and add them to PATH.")
        rows = self.run("devices", targeted=False).splitlines()[1:]
        devices = [line.split()[0] for line in rows if len(line.split()) >= 2 and line.split()[1] == "device"]
        if serial is not None:
            if serial not in devices:
                raise GuardError("Requested device is not connected/authorized. Check the RSA prompt on the phone.")
            self.serial = serial
        elif len(devices) == 1:
            self.serial = devices[0]
        else:
            raise GuardError("Connect exactly one authorized Android device, or specify --serial. No changes were made.")

    def run(self, *args, targeted=True):
        command = [self.executable]
        if targeted:
            command += ["-s", self.serial]
        command += list(args)
        try:
            result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20, shell=False)
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise GuardError("ADB failed: " + str(exc)) from exc
        if result.returncode:
            raise GuardError("ADB failed: " + (result.stderr or result.stdout).strip())
        return result.stdout.strip()

    def shell(self, *args):
        return self.run("shell", *args)

    def whitelist(self):
        raw = self.shell("cmd", "deviceidle", "whitelist")
        if any(marker in raw.lower() for marker in ("unknown command", "not found", "permission denial", "exception")):
            raise GuardError("This Android environment does not expose deviceidle whitelist. No further changes.")
        parsed = parse_whitelist(raw)
        if raw and not parsed:
            raise GuardError("Unrecognized whitelist response. No changes were made.")
        return parsed

    def installed(self, package):
        # pm path can exit with an error for missing optional services, which
        # would otherwise abort read-only diagnosis on phones without GBox/GMS.
        return ("package:" + package) in self.shell("pm", "list", "packages", package).splitlines()


def save_state(path, state):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    try:
        temporary.chmod(0o600)
    except OSError:
        pass
    os.replace(temporary, path)


def read_state(path, serial):
    path = Path(path)
    if not path.exists():
        return {"version": 1, "serial": serial, "added": [], "pending": [], "created": datetime.now(timezone.utc).isoformat()}
    try:
        state = json.loads(path.read_text(encoding="utf-8"))
        if state.get("serial") != serial or state.get("version") != 1:
            raise GuardError("State belongs to another device or unsupported version. No changes were made.")
        state["added"] = validate_packages(state["added"])
        state["pending"] = validate_packages(state.get("pending", []))
        return state
    except (ValueError, TypeError, KeyError) as exc:
        raise GuardError("Invalid state file; do not overwrite it. No changes were made.") from exc


def apply(adb, packages, path):
    packages = validate_packages(packages)
    if not packages:
        raise GuardError("Specify at least one installed package using --packages.")
    manufacturer = adb.shell("getprop", "ro.product.manufacturer").lower()
    if "huawei" not in manufacturer:
        raise GuardError("Environment does not identify as Huawei. This helper cannot configure the host outside an Android container.")
    state = read_state(path, adb.serial)
    for package in packages:
        if not adb.installed(package):
            raise GuardError("Package is not installed in this environment: " + package + ". No new changes were made.")
    current = adb.whitelist()
    # Recover the journal after an interrupted run before doing any new work.
    for package in state["pending"]:
        if "user" in current.get(package, set()) and package not in state["added"]:
            state["added"].append(package)
    state["pending"] = []
    save_state(path, state)
    messages = []
    for package in packages:
        if package in current:
            messages.append("Already exempt; preserved: " + package)
            continue
        state["pending"] = [package]
        save_state(path, state)
        adb.shell("cmd", "deviceidle", "whitelist", "+" + package)
        current = adb.whitelist()
        if "user" not in current.get(package, set()):
            raise GuardError("System did not confirm the exemption for " + package + ". Keep the state file and run restore if needed.")
        if package not in state["added"]:
            state["added"].append(package)
        state["pending"] = []
        save_state(path, state)
        messages.append("Added and verified: " + package)
    return messages


def restore(adb, path):
    if not Path(path).exists():
        raise GuardError("No saved state for this device. Nothing was removed.")
    state = read_state(path, adb.serial)
    owned = validate_packages(state["added"] + state["pending"])
    messages = []
    for package in owned:
        current = adb.whitelist()
        if "user" in current.get(package, set()):
            adb.shell("cmd", "deviceidle", "whitelist", "-" + package)
            if "user" in adb.whitelist().get(package, set()):
                raise GuardError("System did not confirm removal of " + package + ". State was retained.")
        state["added"] = [p for p in state["added"] if p != package]
        state["pending"] = [p for p in state["pending"] if p != package]
        save_state(path, state)
        messages.append("Restored helper-owned entry: " + package)
    return messages or ["No helper-owned changes remain. Existing exemptions were preserved."]


def diagnose(adb):
    values = {}
    for label, prop in [("manufacturer", "ro.product.manufacturer"), ("model", "ro.product.model"),
                        ("android", "ro.build.version.release"), ("build", "ro.build.display.id"),
                        ("emui_property", "ro.build.version.emui"), ("harmony_property", "hw_sc.build.platform.version")]:
        values[label] = adb.shell("getprop", prop)
    values["note"] = "Read-only. Android environment only; host OS of a container is not verified."
    values["services"] = {p: adb.installed(p) for p in ("com.google.android.gms", "com.huawei.hwid", "com.gbox.android", "com.gspace.android")}
    values["doze_exemptions"] = sorted(adb.whitelist())
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["diagnose", "apply", "restore"], nargs="?", default="diagnose")
    parser.add_argument("--packages", nargs="*", default=[])
    parser.add_argument("--serial", help="ADB serial; local use only")
    parser.add_argument("--adb", default="adb", help="ADB executable or full path")
    parser.add_argument("--state", type=Path, help="Local rollback journal path")
    args = parser.parse_args()
    try:
        adb = Adb(args.adb, args.serial)
        suffix = hashlib.sha256(adb.serial.encode()).hexdigest()[:12]
        path = args.state or Path(__file__).parent / ("huawei-state-" + suffix + ".json")
        if args.action == "diagnose":
            print(json.dumps(diagnose(adb), ensure_ascii=False, indent=2))
        else:
            messages = apply(adb, args.packages, path) if args.action == "apply" else restore(adb, path)
            print("\n".join(messages))
            print("State saved locally: " + str(path))
            print("Huawei App launch settings and actual FCM/HMS delivery still need to be checked on the phone.")
    except GuardError as exc:
        print("ERROR: " + str(exc), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
