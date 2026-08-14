package com.example.aabbg.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public class PermissionUtil {
    public static final int PERMISSION_REQUEST_CODE = 1000;

    private static final String[] REQUIRED_PERMISSIONS = new String[] {
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.READ_PHONE_STATE
    };

    private static final String[] BLUETOOTH_PERMISSIONS = new String[] {
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT"
    };

    public static List<String> getUngrantedPermissions(Activity activity) {
        List<String> needed = new ArrayList<>();
        for (String p : REQUIRED_PERMISSIONS) {
            if (activity.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            for (String p : BLUETOOTH_PERMISSIONS) {
                if (activity.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
                    needed.add(p);
            }
        }
        return needed;
    }

    public static boolean checkPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        List<String> needed = getUngrantedPermissions(activity);
        if (!needed.isEmpty()) {
            activity.requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    public static boolean hasPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (activity.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            for (String p : BLUETOOTH_PERMISSIONS) {
                if (activity.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
                    return false;
            }
        }
        return true;
    }

    public static boolean isAllPermissionsGranted(int[] grantResults) {
        if (grantResults == null || grantResults.length == 0) return false;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }
}
