// 蓝牙功能：开启蓝牙后自动显示设备连接状态，扫描附近蓝牙设备并显示名称、MAC地址、类型、信号强度等信息
package com.example.aabbg.handlers;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothClass;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;
import java.util.HashSet;
import java.util.Set;

public class BluetoothHandler {
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final TextView txtBluetoothResults;
    private final BroadcastReceiver bluetoothReceiver;
    private final BroadcastReceiver btStateReceiver;
    private final Set<String> discoveredDevices;
    private boolean isScanning = false;
    private boolean isWaitingForBluetooth = false;
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;
    private Runnable btTimeoutRunnable;

    public BluetoothHandler(Context context, TextView txtBluetoothResults) {
        this.context = context;
        this.txtBluetoothResults = txtBluetoothResults;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.discoveredDevices = new HashSet<>();

        final TextView finalTxtBluetoothResults = txtBluetoothResults;
        final Set<String> finalDiscoveredDevices = discoveredDevices;

        this.bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && !finalDiscoveredDevices.contains(device.getAddress())) {
                        finalDiscoveredDevices.add(device.getAddress());
                        updateBluetoothResults(device, intent);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    isScanning = false;
                    finalTxtBluetoothResults.append("\n扫描完成，共发现 " +
                        finalDiscoveredDevices.size() + " 个设备");
                    // 5秒后自动再次扫描
                    autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
                    autoRefreshRunnable = new Runnable() { public void run() { if (!isScanning && !isWaitingForBluetooth) { startBluetoothScan(); } } };
                    autoRefreshHandler.postDelayed(autoRefreshRunnable, 5000);
                }
            }
        };

        this.btStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.STATE_OFF);
                if (state == BluetoothAdapter.STATE_ON) {
                    timeoutHandler.removeCallbacks(btTimeoutRunnable);
                    isWaitingForBluetooth = false;
                    showToast("蓝牙已开启，开始扫描...");
                    performBluetoothScan();
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    timeoutHandler.removeCallbacks(btTimeoutRunnable);
                    isWaitingForBluetooth = false;
                    showToast("蓝牙开启失败");
                }
            }
        };
    }

    private void showToast(final String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public void startBluetoothScan() {
        if (isScanning) {
            showToast("正在扫描中，请稍候...");
            return;
        }

        if (isWaitingForBluetooth) {
            showToast("正在等待蓝牙开启...");
            return;
        }

        if (bluetoothAdapter == null) {
            showToast("设备不支持蓝牙");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            showToast("正在开启蓝牙，请稍候...");
            txtBluetoothResults.setText("等待蓝牙开启...\n");
            bluetoothAdapter.enable();
            isWaitingForBluetooth = true;

            context.registerReceiver(btStateReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

            btTimeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    isWaitingForBluetooth = false;
                    try {
                        context.unregisterReceiver(btStateReceiver);
                    } catch (IllegalArgumentException e) {}
                    showToast("蓝牙开启超时，请手动开启后重试");
                    txtBluetoothResults.setText("蓝牙开启超时，请检查蓝牙设置后重试");
                }
            };
            timeoutHandler.postDelayed(btTimeoutRunnable, 10000);
            return;
        }

        performBluetoothScan();
    }

    private void performBluetoothScan() {
        discoveredDevices.clear();
        txtBluetoothResults.setText("正在扫描蓝牙设备...\n\n");

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(bluetoothReceiver, filter);

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (!pairedDevices.isEmpty()) {
            txtBluetoothResults.append("已配对设备：\n\n");
            for (BluetoothDevice device : pairedDevices) {
                discoveredDevices.add(device.getAddress());
                updateBluetoothResults(device, null);
            }
            txtBluetoothResults.append("\n正在搜索新设备...\n\n");
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        isScanning = bluetoothAdapter.startDiscovery();
        if (!isScanning) {
            showToast("启动扫描失败");
        }
    }

    private void updateBluetoothResults(BluetoothDevice device, Intent intent) {
        StringBuilder deviceInfo = new StringBuilder();

        String deviceName = device.getName();
        deviceInfo.append("设备名称: ")
                 .append(deviceName != null ? deviceName : "未知设备")
                 .append("\n");

        deviceInfo.append("MAC地址: ")
                 .append(device.getAddress())
                 .append("\n");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            deviceInfo.append("设备类型: ")
                     .append(getDeviceType(device))
                     .append("\n");
        }

        deviceInfo.append("配对状态: ")
                 .append(getBondState(device))
                 .append("\n");

        if (intent != null) {
            int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
            if (rssi != Short.MIN_VALUE) {
                deviceInfo.append("信号强度: ")
                         .append(rssi)
                         .append(" dBm (")
                         .append(getRssiLevel(rssi))
                         .append(")\n");
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            int deviceClass = device.getBluetoothClass().getMajorDeviceClass();
            deviceInfo.append("设备类别: ")
                     .append(getDeviceClass(deviceClass))
                     .append("\n");
        }

        deviceInfo.append("\n");
        txtBluetoothResults.append(deviceInfo.toString());
    }

    private String getDeviceType(BluetoothDevice device) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            switch (device.getType()) {
                case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                    return "传统蓝牙";
                case BluetoothDevice.DEVICE_TYPE_LE:
                    return "低功耗蓝牙(BLE)";
                case BluetoothDevice.DEVICE_TYPE_DUAL:
                    return "双模蓝牙";
                default:
                    return "未知类型";
            }
        }
        return "未知类型";
    }

    private String getBondState(BluetoothDevice device) {
        switch (device.getBondState()) {
            case BluetoothDevice.BOND_BONDED:
                return "已配对";
            case BluetoothDevice.BOND_BONDING:
                return "正在配对";
            case BluetoothDevice.BOND_NONE:
                return "未配对";
            default:
                return "未知状态";
        }
    }

    private String getRssiLevel(int rssi) {
        if (rssi >= -50) return "信号极好 ★★★★★";
        else if (rssi >= -60) return "信号良好 ★★★★☆";
        else if (rssi >= -70) return "信号一般 ★★★☆☆";
        else if (rssi >= -80) return "信号较差 ★★☆☆☆";
        else return "信号很差 ★☆☆☆☆";
    }

    private String getDeviceClass(int deviceClass) {
        switch (deviceClass) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO:
                return "音频设备";
            case BluetoothClass.Device.Major.COMPUTER:
                return "电脑";
            case BluetoothClass.Device.Major.HEALTH:
                return "健康设备";
            case BluetoothClass.Device.Major.IMAGING:
                return "图像设备";
            case BluetoothClass.Device.Major.MISC:
                return "其他设备";
            case BluetoothClass.Device.Major.NETWORKING:
                return "网络设备";
            case BluetoothClass.Device.Major.PERIPHERAL:
                return "外围设备";
            case BluetoothClass.Device.Major.PHONE:
                return "手机";
            case BluetoothClass.Device.Major.TOY:
                return "玩具";
            case BluetoothClass.Device.Major.WEARABLE:
                return "可穿戴设备";
            default:
                return "未知类别";
        }
    }

    public void stopBluetoothScan() {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        try {
            context.unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException e) {}
        try {
            context.unregisterReceiver(btStateReceiver);
        } catch (IllegalArgumentException e) {}
        timeoutHandler.removeCallbacks(btTimeoutRunnable);

        isScanning = false;
        isWaitingForBluetooth = false;
    }

    public boolean isScanning() {
        return isScanning;
    }
                      }
