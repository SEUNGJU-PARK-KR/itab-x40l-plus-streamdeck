package com.itab.deck;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

public class LinuxController {
    private static final String PREFS = "linux_server_state";
    private static final String KEY_STATE = "state";

    public static boolean isTermuxInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static String getState(Context context) {
        if (!isTermuxInstalled(context)) return "termux-missing";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_STATE, "ready");
    }

    public static String perform(Context context, String action) {
        if ("termux.open".equals(action)) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.termux");
            if (launch == null) return "Termux가 설치되어 있지 않습니다.";
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
            return "Termux 실행";
        }
        if ("linux.install".equals(action)) {
            return runTermuxShell(context, installCommand(), "install-requested");
        }
        if ("linux.start".equals(action)) {
            return runTermuxShell(context, startCommand(), "start-requested");
        }
        if ("linux.stop".equals(action)) {
            return runTermuxShell(context, stopCommand(), "stop-requested");
        }
        if ("linux.bootstrap".equals(action)) {
            return runTermuxShell(context, bootstrapCommand(), "bootstrap-requested");
        }
        return "unknown action: " + action;
    }

    private static String installCommand() {
        return "pkg update -y && pkg install -y proot-distro openssh ttyd iproute2 curl && "
                + "(proot-distro install alpine || true) && "
                + "proot-distro login alpine -- sh -c '/sbin/apk add --no-cache python3 bash' && echo ITAB_INSTALL_DONE";
    }

    private static String bootstrapCommand() {
        return "mkdir -p ~/.termux/boot && "
                + "printf '#!/data/data/com.termux/files/usr/bin/sh\\n/data/data/com.termux/files/home/start-itab.sh\\n' > ~/.termux/boot/start-itab.sh && "
                + "chmod +x ~/.termux/boot/start-itab.sh && "
                + "echo ITAB_BOOTSTRAP_DONE";
    }

    private static String startCommand() {
        return "/data/data/com.termux/files/home/start-itab.sh";
    }

    private static String stopCommand() {
        return "/data/data/com.termux/files/home/stop-itab.sh";
    }

    private static String runTermuxShell(Context context, String command, String state) {
        if (!isTermuxInstalled(context)) {
            setState(context, "termux-missing");
            return "Termux가 설치되어 있지 않습니다. 먼저 Termux를 설치하세요.";
        }
        try {
            Intent intent = new Intent();
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.setAction("com.termux.RUN_COMMAND");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            context.startService(intent);
            setState(context, state);
            return "Termux 명령 전송 완료";
        } catch (SecurityException e) {
            setState(context, "permission-missing");
            return "RUN_COMMAND 권한 또는 Termux allow-external-apps 설정이 필요합니다.";
        } catch (Exception e) {
            setState(context, "error");
            return "명령 전송 실패: " + e.getMessage();
        }
    }

    private static void setState(Context context, String state) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_STATE, state).apply();
    }
}
