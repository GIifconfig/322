// 摄像头独立开关控制：提供独立于预览界面的摄像头打开与关闭功能
package com.example.aabbg.handlers;

import android.content.Context;
import android.view.SurfaceHolder;
import android.widget.Toast;

public class CameraSwitchHandler {
    private final CameraHandler cameraHandler;
    private final Context context;
    private boolean isCameraOpen = false;

    public CameraSwitchHandler(Context context, CameraHandler cameraHandler) {
        this.context = context;
        this.cameraHandler = cameraHandler;
    }

    public void openCamera(SurfaceHolder frontHolder, SurfaceHolder backHolder) {
        if (isCameraOpen) {
            Toast.makeText(context, "摄像头已打开", Toast.LENGTH_SHORT).show();
            return;
        }
        if (frontHolder == null || backHolder == null) {
            Toast.makeText(context, "预览未就绪，请先启动双摄", Toast.LENGTH_SHORT).show();
            return;
        }
        cameraHandler.startBothCameras(frontHolder, backHolder);
        isCameraOpen = true;
        Toast.makeText(context, "摄像头已打开", Toast.LENGTH_SHORT).show();
    }

    public void closeCamera() {
        if (!isCameraOpen) {
            Toast.makeText(context, "摄像头未打开", Toast.LENGTH_SHORT).show();
            return;
        }
        cameraHandler.closeCamera();
        isCameraOpen = false;
        Toast.makeText(context, "摄像头已关闭", Toast.LENGTH_SHORT).show();
    }

    public void toggleCamera(SurfaceHolder frontHolder, SurfaceHolder backHolder) {
        if (isCameraOpen) {
            closeCamera();
        } else {
            openCamera(frontHolder, backHolder);
        }
    }

    public boolean isCameraOpen() {
        return isCameraOpen;
    }
    public boolean wasCameraOn() { return isCameraOpen; }

    public void resetCameraState() {
        isCameraOpen = false;
    }
    public void release() {
        if (isCameraOpen) {
            closeCamera();
        }
    }
}