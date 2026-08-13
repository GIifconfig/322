// 位置信息功能：获取GPS和网络定位数据，显示纬度、经度、精确度、海拔、速度等信息
package com.example.aabbg.handlers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public class LocationHandler {
    private Context context;
    private TextView txtLocation;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean isUpdating = false;

    public LocationHandler(Context context, TextView txtLocation) {
        this.context = context;
        this.txtLocation = txtLocation;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        initLocationListener();
    }

    private void initLocationListener() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                updateLocationInfo(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {
                txtLocation.setText("位置服务已启用");
            }

            @Override
            public void onProviderDisabled(String provider) {
                txtLocation.setText("位置服务已禁用");
            }
        };
    }

    public void startLocationUpdates() {
        if (isUpdating) {
            return;
        }

        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            txtLocation.setText("需要定位权限");
            return;
        }

        Location lastLocation = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (lastLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        if (lastLocation != null) {
            updateLocationInfo(lastLocation);
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    1,
                    locationListener
                );
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000,
                    1,
                    locationListener
                );
            }

            isUpdating = true;
            txtLocation.setText("正在获取位置...");
        } catch (SecurityException e) {
            Toast.makeText(context, "启动定位失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public void stopLocationUpdates() {
        if (!isUpdating) {
            return;
        }

        try {
            locationManager.removeUpdates(locationListener);
            isUpdating = false;
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void updateLocationInfo(Location location) {
        if (location != null) {
            StringBuilder info = new StringBuilder();
            info.append("位置信息:\n");
            info.append("纬度: ").append(String.format("%.6f", location.getLatitude())).append("\n");
            info.append("经度: ").append(String.format("%.6f", location.getLongitude())).append("\n");
            info.append("精确度: ").append(String.format("%.1f米", location.getAccuracy())).append("\n");

            if (location.hasAltitude()) {
                info.append("海拔: ").append(String.format("%.1f米", location.getAltitude())).append("\n");
            }

            if (location.hasSpeed()) {
                info.append("速度: ").append(String.format("%.1f米/秒", location.getSpeed())).append("\n");
            }

            info.append("提供者: ").append(location.getProvider());

            txtLocation.setText(info.toString());
        }
    }
}