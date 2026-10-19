#!/usr/bin/env python3
"""Build a signed test APK with Android SDK Build Tools and javac or ECJ.

No third-party app libraries are downloaded by this script.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def run(*command):
    subprocess.run([str(x) for x in command], check=True)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--android-jar", required=True, type=Path)
    p.add_argument("--build-tools", required=True, type=Path)
    p.add_argument("--ecj", type=Path, help="ECJ compiler JAR, if javac is not installed")
    p.add_argument("--keystore", type=Path, help="Existing Android debug keystore (standard android password/alias)")
    args = p.parse_args()
    android = args.android_jar.resolve()
    bt = args.build_tools.resolve()
    build = ROOT / "app/build/manual"
    dist = ROOT / "dist"
    if build.exists():
        shutil.rmtree(build)
    for path in [build / "gen", build / "classes", build / "dex", dist]:
        path.mkdir(parents=True, exist_ok=True)
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
    manifest.getroot().set("package", "com.ngocloi.huaweinotify")
    manifest.write(build / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
    executable = lambda name: bt / (name + (".exe" if sys.platform == "win32" and name in ("aapt2", "zipalign") else ".bat" if sys.platform == "win32" else ""))
    run(executable("aapt2"), "compile", "--dir", ROOT / "app/src/main/res", "-o", build / "resources.zip")
    run(executable("aapt2"), "link", "-o", build / "base.apk", "--manifest", build / "AndroidManifest.xml", "-I", android,
        "--java", build / "gen", "--min-sdk-version", "23", "--target-sdk-version", "35", build / "resources.zip")
    sources = sorted((ROOT / "app/src/main/java").rglob("*.java")) + sorted((build / "gen").rglob("*.java"))
    response = build / "sources.txt"
    response.write_text("\n".join('"' + str(x) + '"' for x in sources), encoding="utf-8")
    bootclasspath = str(android) + os.pathsep + str(bt / "core-lambda-stubs.jar")
    if args.ecj:
        run("java", "-jar", args.ecj.resolve(), "-source", "1.8", "-target", "1.8", "-encoding", "UTF-8", "-bootclasspath", bootclasspath,
            "-d", build / "classes", "@" + str(response))
    else:
        run("javac", "-source", "8", "-target", "8", "-encoding", "UTF-8", "-bootclasspath", bootclasspath,
            "-d", build / "classes", "@" + str(response))
    with zipfile.ZipFile(build / "classes.jar", "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted((build / "classes").rglob("*.class")):
            z.write(path, path.relative_to(build / "classes").as_posix())
    run("java", "-cp", bt / "lib/d8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "23", "--lib", android,
        "--output", build / "dex", build / "classes.jar")
    shutil.copyfile(build / "base.apk", build / "unsigned.apk")
    with zipfile.ZipFile(build / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as z:
        for path in sorted((build / "dex").glob("*.dex")):
            z.write(path, path.name)
    run(executable("zipalign"), "-f", "4", build / "unsigned.apk", build / "aligned.apk")
    key = args.keystore.resolve() if args.keystore else Path.home() / ".android/debug.keystore"
    if not key.exists():
        if args.keystore:
            raise SystemExit("Requested keystore does not exist")
        key.parent.mkdir(parents=True, exist_ok=True)
        run("keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey",
            "-dname", "CN=Android Debug,O=Android,C=US", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000")
    apk = dist / "HuaweiNotifyBeta.apk"
    run("java", "-jar", bt / "lib/apksigner.jar", "sign", "--ks", key, "--ks-pass", "pass:android", "--key-pass", "pass:android",
        "--ks-key-alias", "androiddebugkey", "--out", apk, build / "aligned.apk")
    run("java", "-jar", bt / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", apk)
    print("APK:", apk)


if __name__ == "__main__":
    main()
