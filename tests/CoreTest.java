import com.ngocloi.huaweinotify.Core;
import java.util.Arrays;

public final class CoreTest {
    public static void main(String[] args) {
        String[] unsafe = {"com.app;reboot", "com.app\nreboot", "com..app", "-com.app", "com.app$(id)", "com.app/../../", "android"};
        for (String value : unsafe) {
            if (Core.validPackage(value)) throw new AssertionError("Accepted unsafe package: " + value);
        }
        if (!Core.validPackage("com.google.android.gms")) throw new AssertionError("Rejected valid package");
        String instructions = Core.adbInstructions(Arrays.asList("com.google.android.gms", "com.google.android.gms"));
        if (!instructions.contains("--packages com.google.android.gms\n")) throw new AssertionError("Instructions not deduplicated");
        if (instructions.contains("disable-user") || instructions.contains("force-stop") || instructions.contains("settings put")) throw new AssertionError("Unexpected system mutation");
        boolean rejected = false;
        try { Core.adbInstructions(Arrays.asList("com.good", "com.bad;reboot")); }
        catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Unsafe package reached generated commands");
        System.out.println("Core validation and generated-command tests passed.");
    }
}
