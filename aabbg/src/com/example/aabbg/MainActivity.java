package com.example.aabbg;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StatFs;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.aabbg.handlers.BluetoothHandler;
import com.example.aabbg.handlers.CameraHandler;
import com.example.aabbg.handlers.CameraSwitchHandler;
import com.example.aabbg.handlers.LocationHandler;
import com.example.aabbg.handlers.SensorHandler;
import com.example.aabbg.handlers.VideoPlayerHandler;
import com.example.aabbg.handlers.WifiHandler;
import com.example.aabbg.utils.PermissionUtil;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private TextView txtLocation;
    private TextView txtSystemInfo;
    private TextView txtSensorData;
    private TextView txtWifiResults;
    private TextView txtBluetoothResults;
    private TextView txtRecordStatus;
    private SurfaceView surfaceViewFront;
    private SurfaceView surfaceViewBack;
    private EditText vibrateInput;
    private EditText brightnessInput;
    private EditText frontZoomInput;
    private EditText backZoomInput;
    private Button btnVibrateToggle;

    private LocationHandler locationHandler;
    private SensorHandler sensorHandler;
    private WifiHandler wifiHandler;
    private BluetoothHandler bluetoothHandler;
    private CameraHandler cameraHandler;
    private CameraSwitchHandler cameraSwitchHandler;
    private VideoPlayerHandler videoPlayerHandler;
    private Handler infoRefreshHandler = new Handler();
    private Runnable infoRefreshRunnable;

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private String audioFilePath;
    private boolean isRecording = false;
    private Vibrator vibrator;
    private boolean isVibrating = false;

    private static final String SAVE_PATH = "/storage/emulated/0/11/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createSaveDirectory();

        // Initialize UI regardless of permission state
        initViews();
        initHandlers();
        setupAudioPath();
        // Request permissions if needed (non-blocking)
        PermissionUtil.checkPermissions(this);
    }

    private void createSaveDirectory() {
        File directory = new File(SAVE_PATH);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    @SuppressWarnings("deprecation")
    private void startVibrating(long intensity) {
        if (vibrator != null && !isVibrating) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                    new long[]{0, intensity},
                    new int[]{0, VibrationEffect.DEFAULT_AMPLITUDE},
                    0));
            } else {
                @SuppressWarnings("deprecation")
                Vibrator v = vibrator;
                v.vibrate(new long[]{0, intensity}, 0);
            }
            isVibrating = true;
            btnVibrateToggle.setText("停止振动");
        }
    }

    private void stopVibrating() {
        if (vibrator != null && isVibrating) {
            vibrator.cancel();
            isVibrating = false;
            btnVibrateToggle.setText("开始振动");
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Surface重建后直接重开相机，不经过isCameraOpen检查
        if (cameraHandler != null && cameraSwitchHandler != null && cameraSwitchHandler.wasCameraOn()) {
            cameraHandler.startBothCameras(surfaceViewFront.getHolder(),
                surfaceViewBack.getHolder());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (cameraHandler != null) {
            // cameraHandler.releaseCamera(); - moved to onPause
        }
    }

    private void initViews() {
        txtLocation = findViewById(R.id.txtLocation);
        txtSystemInfo = findViewById(R.id.txtSystemInfo);
        txtSensorData = findViewById(R.id.txtSensorData);
        txtWifiResults = findViewById(R.id.txtWifiResults);
        txtBluetoothResults = findViewById(R.id.txtBluetoothResults);
        txtRecordStatus = findViewById(R.id.txtRecordStatus);
        surfaceViewFront = findViewById(R.id.surfaceViewFront);
        surfaceViewBack = findViewById(R.id.surfaceViewBack);

        vibrateInput = findViewById(R.id.vibrateInput);
        brightnessInput = findViewById(R.id.brightnessInput);
        frontZoomInput = findViewById(R.id.frontZoomInput);
        backZoomInput = findViewById(R.id.backZoomInput);
        btnVibrateToggle = findViewById(R.id.btnVibrateToggle);

        surfaceViewFront.getHolder().addCallback(this);
        surfaceViewBack.getHolder().addCallback(this);

        // 摄像头开关按钮
        findViewById(R.id.btnOpenCamera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraSwitchHandler.openCamera(surfaceViewFront.getHolder(),
                    surfaceViewBack.getHolder());
            }
        });

        findViewById(R.id.btnCloseCamera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraSwitchHandler.closeCamera();
            }
        });

        // 振动控制
        btnVibrateToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isVibrating) {
                    try {
                        long intensity = Long.parseLong(vibrateInput.getText().toString());
                        startVibrating(intensity);
                    } catch (NumberFormatException e) {
                        Toast.makeText(MainActivity.this, "请输入有效的振动强度",
                            Toast.LENGTH_SHORT).show();
                    }
                } else {
                    stopVibrating();
                }
            }
        });

        // 亮度控制
        findViewById(R.id.btnSetBrightness).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int brightness = Integer.parseInt(brightnessInput.getText().toString());
                    setScreenBrightness(brightness);
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "请输入有效的亮度值(0-255)",
                        Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 前摄像头变焦
        findViewById(R.id.btnSetFrontZoom).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    float zoom = Float.parseFloat(frontZoomInput.getText().toString());
                    cameraHandler.setFrontZoom(zoom);
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "请输入有效的变焦倍数",
                        Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 后摄像头变焦
        findViewById(R.id.btnSetBackZoom).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    float zoom = Float.parseFloat(backZoomInput.getText().toString());
                    cameraHandler.setBackZoom(zoom);
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "请输入有效的变焦倍数",
                        Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 相机控制
        findViewById(R.id.btnStartBothCameras).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraHandler.startBothCameras(surfaceViewFront.getHolder(),
                    surfaceViewBack.getHolder());
            }
        });

        findViewById(R.id.btnTakePhotoFront).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraHandler.takeFrontPhoto();
            }
        });

        findViewById(R.id.btnTakePhotoBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraHandler.takeBackPhoto();
            }
        });

        findViewById(R.id.btnTakeBothPhotos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraHandler.takeBothPhotos();
            }
        });

        findViewById(R.id.btnFlash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraHandler.toggleFlash();
            }
        });

        // 视频播放器初始化
        FrameLayout videoContainer = findViewById(R.id.videoContainer);
        SeekBar videoSeekBar = findViewById(R.id.videoSeekBar);
        TextView txtVideoInfo = findViewById(R.id.txtVideoInfo);
        Button btnToggleOrientation = findViewById(R.id.btnToggleOrientation);
        final EditText videoPathInput = findViewById(R.id.videoPathInput);

        videoPlayerHandler = new VideoPlayerHandler(this, videoContainer,
            videoSeekBar, txtVideoInfo, btnToggleOrientation);

        findViewById(R.id.btnPlayVideo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = videoPathInput.getText().toString().trim();
                videoPlayerHandler.playVideo(path);
            }
        });

        findViewById(R.id.btnStartRecord).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });

        findViewById(R.id.btnStopRecord).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });

        findViewById(R.id.btnPlayRecord).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playRecording();
            }
        });

        findViewById(R.id.btnGetLocation).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                locationHandler.startLocationUpdates();
            }
        });

        findViewById(R.id.btnWifiScan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wifiHandler.startWifiScan();
            }
        });

        findViewById(R.id.btnBluetoothScan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothHandler.startBluetoothScan();
            }
        });

        findViewById(R.id.btnRefreshInfo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshSystemInfo();
            }
        });
    }

    private void initHandlers() {
        locationHandler = new LocationHandler(this, txtLocation);
        sensorHandler = new SensorHandler(this, txtSensorData);
        wifiHandler = new WifiHandler(this, txtWifiResults);
        bluetoothHandler = new BluetoothHandler(this, txtBluetoothResults);
        cameraHandler = new CameraHandler(this);
        cameraSwitchHandler = new CameraSwitchHandler(this, cameraHandler);
    }

    private void setupAudioPath() {
        audioFilePath = SAVE_PATH + "record_" + getCurrentTimeString() + ".mp3";
    }

    private void refreshSystemInfo() {
        StringBuilder info = new StringBuilder();
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        try {
            info.append("===== 系统信息 =====\n");
            info.append("制造商: ").append(Build.MANUFACTURER).append("\n");
            info.append("型号: ").append(Build.MODEL).append("\n");
            info.append("Android版本: ").append(Build.VERSION.RELEASE).append("\n");
            info.append("API级别: ").append(Build.VERSION.SDK_INT).append("\n\n");

            info.append("===== 电量信息 =====\n");
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int batteryPct = (int) ((float)level / (float)scale * 100);
                int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);

                info.append("电量: ").append(batteryPct).append("%\n");
                info.append("电压: ").append(voltage).append(" mV\n");
                info.append("温度: ").append((float)temperature / 10).append("°C\n");

                String statusStr;
                switch (status) {
                    case BatteryManager.BATTERY_STATUS_CHARGING: statusStr = "充电中"; break;
                    case BatteryManager.BATTERY_STATUS_DISCHARGING: statusStr = "放电中"; break;
                    case BatteryManager.BATTERY_STATUS_NOT_CHARGING: statusStr = "未充电"; break;
                    case BatteryManager.BATTERY_STATUS_FULL: statusStr = "已充满"; break;
                    default: statusStr = "未知";
                }
                info.append("状态: ").append(statusStr).append("\n");

                String healthStr;
                switch (health) {
                    case BatteryManager.BATTERY_HEALTH_GOOD: healthStr = "良好"; break;
                    case BatteryManager.BATTERY_HEALTH_OVERHEAT: healthStr = "过热"; break;
                    case BatteryManager.BATTERY_HEALTH_DEAD: healthStr = "损坏"; break;
                    case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: healthStr = "过压"; break;
                    default: healthStr = "未知";
                }
                info.append("电池健康: ").append(healthStr).append("\n\n");
            }

            info.append("===== 内存信息 =====\n");
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            MemoryInfo mi = new MemoryInfo();
            am.getMemoryInfo(mi);
            long totalMem = mi.totalMem;
            long availMem = mi.availMem;
            long usedMem = totalMem - availMem;
            info.append("总内存: ").append(formatSize(totalMem)).append("\n");
            info.append("已用内存: ").append(formatSize(usedMem)).append("\n");
            info.append("可用内存: ").append(formatSize(availMem)).append("\n");
            info.append("使用率: ").append(String.format("%.1f%%", (float)usedMem / totalMem * 100)).append("\n\n");

            info.append("===== 存储空间 =====\n");
            StatFs internalStat = new StatFs("/data");
            long internalTotal = (long) internalStat.getBlockCountLong() * internalStat.getBlockSizeLong();
            long internalAvail = (long) internalStat.getAvailableBlocksLong() * internalStat.getBlockSizeLong();
            long internalUsed = internalTotal - internalAvail;
            info.append("内部存储:\n");
            info.append("  总容量: ").append(formatSize(internalTotal)).append("\n");
            info.append("  已用: ").append(formatSize(internalUsed)).append("\n");
            info.append("  可用: ").append(formatSize(internalAvail)).append("\n");
            info.append("  使用率: ").append(String.format("%.1f%%", (float)internalUsed / internalTotal * 100)).append("\n");

            info.append("\n===== 网络信息 =====\n");
            if (tm != null) {
                if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                    info.append("运营商: ").append(tm.getNetworkOperatorName()).append("\n");
                    info.append("网络类型: ").append(getNetworkType(tm.getNetworkType())).append("\n");
                    info.append("SIM卡状态: ").append(getSimState(tm.getSimState())).append("\n");
                } else {
                    info.append("需要READ_PHONE_STATE权限\n");
                }
            }

            txtSystemInfo.setText(info.toString());
            // 5秒后自动刷新
            infoRefreshHandler.removeCallbacks(infoRefreshRunnable);
            infoRefreshRunnable = new Runnable() { public void run() { refreshSystemInfo(); } };
            infoRefreshHandler.postDelayed(infoRefreshRunnable, 5000);
        } catch (SecurityException e) {
            Toast.makeText(this, "需要系统权限", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatSize(long size) {
        if (size <= 0) return "0B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.2f%s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String getNetworkType(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            case 20:
                return "5G";
            default:
                return "未知";
        }
    }

    private String getSimState(int state) {
        switch (state) {
            case TelephonyManager.SIM_STATE_ABSENT:
                return "无SIM卡";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                return "需要网络PIN解锁";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                return "需要SIM卡PIN解锁";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                return "需要SIM卡PUK解锁";
            case TelephonyManager.SIM_STATE_READY:
                return "就绪";
            case TelephonyManager.SIM_STATE_UNKNOWN:
                return "未知状态";
            default:
                return "状态码: " + state;
        }
    }

    private void startRecording() {
        if (isRecording) {
            Toast.makeText(this, "正在录音中", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            txtRecordStatus.setText("录音中...");
            Toast.makeText(this, "开始录音", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "录音失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (!isRecording) {
            return;
        }

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            txtRecordStatus.setText("录音已保存: " + audioFilePath);
            Toast.makeText(this, "录音已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "停止录音失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void playRecording() {
        if (isRecording) {
            Toast.makeText(this, "请先停止录音", Toast.LENGTH_SHORT).show();
            return;
        }

        // Release previous player if exists
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception e) {}
            mediaPlayer = null;
        }

        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            Toast.makeText(this, "没有可播放的录音文件", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFilePath);
            mediaPlayer.prepare();
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // Reset the player but keep it reusable
                    try {
                        mp.reset();
                    } catch (Exception e) {}
                    mediaPlayer = null;
                    txtRecordStatus.setText("播放完成");
                    Toast.makeText(MainActivity.this, "播放完成", Toast.LENGTH_SHORT).show();
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    try {
                        mp.reset();
                    } catch (Exception e) {}
                    mediaPlayer = null;
                    Toast.makeText(MainActivity.this, "播放错误", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

            txtRecordStatus.setText("正在播放录音...");
        } catch (IOException e) {
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private String getCurrentTimeString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        return sdf.format(new Date());
    }

    private void setScreenBrightness(int brightness) {
        if (!Settings.System.canWrite(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            Toast.makeText(this, "请开启修改系统设置权限", Toast.LENGTH_LONG).show();
            startActivity(intent);
            return;
        }

        try {
            brightness = Math.max(0, Math.min(255, brightness));
            Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, brightness);

            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.screenBrightness = brightness / 255f;
            getWindow().setAttributes(params);
        } catch (Exception e) {
            Toast.makeText(this, "设置亮度失败: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionUtil.hasPermissions(this)) {
            if (sensorHandler != null) {
                sensorHandler.startSensorMonitoring();
            }
            if (cameraHandler != null) {
                cameraHandler.onResume();
            }
        }
        if (videoPlayerHandler != null) {
            videoPlayerHandler.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorHandler != null) {
            sensorHandler.stopSensorMonitoring();
        }
        if (cameraHandler != null) {
            cameraHandler.onPause();
        }
        if (cameraSwitchHandler != null) {
            cameraSwitchHandler.resetCameraState();
        }
        if (videoPlayerHandler != null) {
            videoPlayerHandler.pause();
        }
        if (isRecording) {
            stopRecording();
        }
        stopVibrating();
    }

    @Override
    protected void onDestroy() {
        if (infoRefreshHandler != null) {
            infoRefreshHandler.removeCallbacks(infoRefreshRunnable);
        }
        super.onDestroy();
        if (locationHandler != null) {
            locationHandler.stopLocationUpdates();
        }
        if (wifiHandler != null) {
            wifiHandler.stopWifiScan();
        }
        if (bluetoothHandler != null) {
            bluetoothHandler.stopBluetoothScan();
        }
        if (videoPlayerHandler != null) {
            videoPlayerHandler.stopAndRelease();
        }
        if (cameraHandler != null) {
            // cameraHandler.releaseCamera(); - moved to onPause
        }
        if (cameraSwitchHandler != null) {
            cameraSwitchHandler.release();
        }

        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }

        stopVibrating();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PermissionUtil.PERMISSION_REQUEST_CODE) {
            if (PermissionUtil.isAllPermissionsGranted(grantResults)) {
                initViews();
                initHandlers();
                setupAudioPath();
            } else {
                Toast.makeText(this, "需要相关权限才能正常运行", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}