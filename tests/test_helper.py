import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("helper", Path(__file__).parents[1] / "tools/huawei_notify.py")
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class FakeAdb:
    serial = "TEST-DEVICE"

    def __init__(self):
        self.entries = {"com.huawei.hwid": {"system"}, "com.keep.existing": {"user"}}
        self.installed_packages = {"com.huawei.hwid", "com.google.android.gms", "com.example.chat"}
        self.commands = []
        self.fail_add = False

    def whitelist(self):
        return {p: set(kinds) for p, kinds in self.entries.items()}

    def installed(self, package):
        return package in self.installed_packages

    def shell(self, *args):
        if args == ("getprop", "ro.product.manufacturer"):
            return "HUAWEI"
        self.commands.append(args)
        target = args[-1]
        if args[:3] != ("cmd", "deviceidle", "whitelist"):
            raise AssertionError(args)
        if target.startswith("+"):
            self.entries.setdefault(target[1:], set()).add("user")
            if self.fail_add:
                raise helper.GuardError("Connection lost after mutation")
        else:
            kinds = self.entries.get(target[1:], set())
            kinds.discard("user")
            if not kinds:
                self.entries.pop(target[1:], None)
        return "ok"


class HelperTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "state.json"
        self.adb = FakeAdb()

    def test_apply_twice_then_restore_preserves_existing_and_system_entries(self):
        baseline = self.adb.whitelist()
        packages = ["com.google.android.gms", "com.huawei.hwid", "com.example.chat"]
        helper.apply(self.adb, packages, self.path)
        first = list(self.adb.commands)
        helper.apply(self.adb, packages, self.path)
        self.assertEqual(first, self.adb.commands)
        self.assertEqual(["com.google.android.gms", "com.example.chat"], json.loads(self.path.read_text())["added"])
        helper.restore(self.adb, self.path)
        self.assertEqual(baseline, self.adb.whitelist())

    def test_crash_after_device_mutation_can_be_restored(self):
        baseline = self.adb.whitelist()
        self.adb.fail_add = True
        with self.assertRaises(helper.GuardError):
            helper.apply(self.adb, ["com.google.android.gms"], self.path)
        self.assertEqual(["com.google.android.gms"], json.loads(self.path.read_text())["pending"])
        helper.restore(self.adb, self.path)
        self.assertEqual(baseline, self.adb.whitelist())

    def test_missing_package_aborts_before_any_mutation(self):
        with self.assertRaises(helper.GuardError):
            helper.apply(self.adb, ["com.google.android.gms", "com.not.installed"], self.path)
        self.assertEqual([], self.adb.commands)

    def test_state_for_another_device_is_refused(self):
        helper.save_state(self.path, {"version": 1, "serial": "ANOTHER", "added": ["com.keep.existing"], "pending": []})
        with self.assertRaises(helper.GuardError):
            helper.restore(self.adb, self.path)
        self.assertEqual([], self.adb.commands)

    def test_command_injection_and_malformed_names_are_refused(self):
        for value in ["com.ok;reboot", "com.ok && reboot", "com.ok\nreboot", "-com.ok", "com..ok", "com.ok/../x", "$(reboot)", "", "android"]:
            with self.subTest(value=value), self.assertRaises(helper.GuardError):
                helper.apply(self.adb, [value], self.path)
        self.assertEqual([], self.adb.commands)

    def test_restore_without_state_does_not_remove_anything(self):
        with self.assertRaises(helper.GuardError):
            helper.restore(self.adb, self.path)
        self.assertEqual([], self.adb.commands)

    def test_interrupted_apply_can_resume_and_still_restore_every_change(self):
        baseline = self.adb.whitelist()
        self.adb.fail_add = True
        with self.assertRaises(helper.GuardError):
            helper.apply(self.adb, ["com.google.android.gms"], self.path)
        self.adb.fail_add = False
        helper.apply(self.adb, ["com.google.android.gms", "com.example.chat"], self.path)
        helper.restore(self.adb, self.path)
        self.assertEqual(baseline, self.adb.whitelist())

    def test_parse_mixed_whitelist_entries(self):
        parsed = helper.parse_whitelist("system,com.huawei.hwid,1000\nuser,com.google.android.gms,10001\nsystem-excidle,com.huawei.hwid,1000\n")
        self.assertEqual({"system", "system-excidle"}, parsed["com.huawei.hwid"])

    def test_installed_check_ignores_substring_matches_and_missing_services(self):
        class PackageQuery:
            def shell(self, *args):
                return "package:com.google.android.gms.other\npackage:com.example.chat"
        query = PackageQuery()
        self.assertFalse(helper.Adb.installed(query, "com.google.android.gms"))
        self.assertFalse(helper.Adb.installed(query, "com.gbox.android"))
        self.assertTrue(helper.Adb.installed(query, "com.example.chat"))


if __name__ == "__main__":
    unittest.main()
