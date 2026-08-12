// WiFi功能：开启WiFi后自动扫描附近网络，显示WiFi名称、信号强度、频率、加密方式、MAC地址等信息
package com.example.aabbg.handlers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;

public class WifiHandler {
    private final Context context;
    private final WifiManager wifiManager;
    private final TextView txtWifiResults;
    private final BroadcastReceiver wifiScanReceiver;
    private final BroadcastReceiver wifiStateReceiver;
    private boolean isScanning = false;
    private boolean isWaitingForWifi = false;
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;
    private Runnable wifiTimeoutRunnable;

    public WifiHandler(Context context, TextView txtWifiResults) {
        this.context = context;
        this.txtWifiResults = txtWifiResults;
        final TextView finalResults = txtWifiResults;
        this.wifiManager = (WifiManager)
            context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        this.wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    boolean success = intent.getBooleanExtra(
                        WifiManager.EXTRA_RESULTS_UPDATED, false);
                    if (success) {
                        updateWifiResults();
                    } else {
                        showToast("WiFi扫描失败，使用缓存结果");
                        updateWifiResults();
                    }
                    isScanning = false;
                }
            }
        };

        this.wifiStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN);
                if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                    timeoutHandler.removeCallbacks(wifiTimeoutRunnable);
                    isWaitingForWifi = false;
                    showToast("WiFi 已开启，开始扫描...");
                    performScan();
                } else if (wifiState == WifiManager.WIFI_STATE_UNKNOWN ||
                           wifiState == WifiManager.WIFI_STATE_DISABLED ||
                           wifiState == WifiManager.WIFI_STATE_DISABLING) {
                    timeoutHandler.removeCallbacks(wifiTimeoutRunnable);
                    isWaitingForWifi = false;
                    showToast("WiFi 开启失败");
                    finalResults.setText("WiFi 开启失败，请检查系统设置");
                }
            }
        };
    }

    private void showToast(final String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public void startWifiScan() {
        if (isScanning) {
            showToast("正在扫描中，请稍候...");
            return;
        }

        if (isWaitingForWifi) {
            showToast("正在等待WiFi开启...");
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            showToast("正在开启WIFI...");
            wifiManager.setWifiEnabled(true);
            isWaitingForWifi = true;

            context.registerReceiver(wifiStateReceiver,
                new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));

            wifiTimeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    isWaitingForWifi = false;
                    try {
                        context.unregisterReceiver(wifiStateReceiver);
                    } catch (IllegalArgumentException e) {}
                    showToast("WiFi 开启超时，请手动开启后重试");
                    txtWifiResults.setText("WiFi 开启超时，请检查WiFi设置后重试");
                }
            };
            timeoutHandler.postDelayed(wifiTimeoutRunnable, 5000);
            return;
        }

        performScan();
    }

    private void performScan() {
        // 取消之前的定时刷新
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        txtWifiResults.setText("正在扫描WIFI...\n请稍候...");

        try {
            context.registerReceiver(wifiScanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

            isScanning = true;
            if (!wifiManager.startScan()) {
                showToast("启动扫描失败，使用缓存结果");
                updateWifiResults();
                isScanning = false;
            }
        } catch (Exception e) {
            showToast("扫描出错: " + e.getMessage());
            isScanning = false;
        }
    }

    private void updateWifiResults() {
        // 5秒后自动再次扫描
        autoRefreshRunnable = new Runnable() { public void run() { performScan(); } };
        autoRefreshHandler.postDelayed(autoRefreshRunnable, 5000);
        List<ScanResult> results = wifiManager.getScanResults();
        if (results == null) {
            results = new ArrayList<>();
        }

        Collections.sort(results, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult result1, ScanResult result2) {
                return result2.level - result1.level;
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("发现 ").append(results.size()).append(" 个WIFI网络：\n\n");

        for (ScanResult result : results) {
            int level = WifiManager.calculateSignalLevel(result.level, 5);
            String security = getSecurityType(result);
            String band = result.frequency > 5000 ? "5GHz" : "2.4GHz";
            int channel = getChannel(result.frequency);

            sb.append("名称: ").append(result.SSID.isEmpty() ? "<隐藏>" : result.SSID)
              .append("\n信号强度: ").append(result.level).append(" dBm")
              .append(" (").append(getSignalStrength(level)).append(")")
              .append("\n频率: ").append(result.frequency).append(" MHz")
              .append(" (").append(band).append(", CH").append(channel).append(")")
              .append("\n加密类型: ").append(security)
              .append("\nMAC地址: ").append(result.BSSID);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                sb.append("\n运营商: ").append(result.venueName != null ?
                    result.venueName : "未知");
            }

            sb.append("\n\n");
        }

        txtWifiResults.setText(sb.toString());
    }

    private String getSecurityType(ScanResult scanResult) {
        String capabilities = scanResult.capabilities;
        if (capabilities.contains("WEP")) {
            return "WEP";
        } else if (capabilities.contains("WPA3")) {
            return "WPA3";
        } else if (capabilities.contains("WPA2")) {
            if (capabilities.contains("PSK")) {
                return "WPA2-Personal";
            } else if (capabilities.contains("EAP")) {
                return "WPA2-Enterprise";
            }
            return "WPA2";
        } else if (capabilities.contains("WPA")) {
            if (capabilities.contains("PSK")) {
                return "WPA-Personal";
            } else if (capabilities.contains("EAP")) {
                return "WPA-Enterprise";
            }
            return "WPA";
        } else {
            return "开放";
        }
    }

    private String getSignalStrength(int level) {
        switch (level) {
            case 4: return "极好 ★★★★★";
            case 3: return "良好 ★★★★☆";
            case 2: return "一般 ★★★☆☆";
            case 1: return "较差 ★★☆☆☆";
            default: return "很差 ★☆☆☆☆";
        }
    }

    private int getChannel(int frequency) {
        if (frequency >= 2412 && frequency <= 2484) {
            return (frequency - 2412) / 5 + 1;
        } else if (frequency >= 5170 && frequency <= 5825) {
            return (frequency - 5170) / 5 + 34;
        }
        return -1;
    }

    public void stopWifiScan() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        try {
            context.unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException e) {}
        try {
            context.unregisterReceiver(wifiStateReceiver);
        } catch (IllegalArgumentException e) {}
        timeoutHandler.removeCallbacks(wifiTimeoutRunnable);
        isScanning = false;
        isWaitingForWifi = false;
    }

    public boolean isScanning() {
        return isScanning;
    }
          }
