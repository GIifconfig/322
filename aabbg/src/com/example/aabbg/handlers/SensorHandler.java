// 传感器功能：监听加速度传感器数据，实时显示X/Y/Z轴加速度值
package com.example.aabbg.handlers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.TextView;

import java.util.Locale;

public class SensorHandler implements SensorEventListener {
    private SensorManager sensorManager;
    private TextView textView;
    private Sensor accelerometer;
    private StringBuilder sensorData;
    private float[] lastAcceleration = new float[3];
    private static final float FILTER_FACTOR = 0.1f;

    public SensorHandler(Context context, TextView textView) {
        this.textView = textView;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.sensorData = new StringBuilder();
    }

    public void startSensorMonitoring() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void stopSensorMonitoring() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            for (int i = 0; i < 3; i++) {
                lastAcceleration[i] = lastAcceleration[i] + FILTER_FACTOR *
                    (event.values[i] - lastAcceleration[i]);
            }

            sensorData.setLength(0);
            sensorData.append("加速度传感器 (m/s²):\n");
            sensorData.append(String.format(Locale.getDefault(),
                "X: %.1f\nY: %.1f\nZ: %.1f",
                lastAcceleration[0], lastAcceleration[1], lastAcceleration[2]));

            textView.post(new Runnable() {
                @Override
                public void run() {
                    textView.setText(sensorData.toString());
                }
            });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}