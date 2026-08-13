// 摄像头功能：管理前后双摄预览、拍照、变焦和闪光灯独立控制
package com.example.aabbg.handlers;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"unused", "resource"})
public class CameraHandler {
    private static final String TAG = "CameraHandler";
    private Context context;
    private CameraManager cameraManager;
    private String frontCameraId;
    private String backCameraId;
    private CameraDevice frontCamera;
    private CameraDevice backCamera;
    private CameraCaptureSession frontSession;
    private CameraCaptureSession backSession;
    private ImageReader frontImageReader;
    private ImageReader backImageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Handler mainHandler = new Handler(android.os.Looper.getMainLooper());
    private boolean isFlashOn = false;

    private float frontCurrentZoom = 1.0f;
    private float backCurrentZoom = 1.0f;
    private float frontMaxZoom = 1.0f;
    private float backMaxZoom = 1.0f;
    private Rect frontSensorSize;
    private Rect backSensorSize;
    private Surface frontPreviewSurface;
    private Surface backPreviewSurface;
    private boolean hasFlash = false;

    private static final int IMAGE_WIDTH = 1920;
    private static final int IMAGE_HEIGHT = 1080;
    private static final String SAVE_PATH = "/storage/emulated/0/11/";
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);

    private boolean isCameraInitialized = false;
    private SurfaceHolder cachedFrontHolder;
    private SurfaceHolder cachedBackHolder;

    private CaptureRequest.Builder frontPreviewBuilder;
    private CaptureRequest.Builder backPreviewBuilder;
    private CaptureRequest mPreviewRequest;

    private CameraDevice flashOnlyCamera;
    private CameraCaptureSession flashOnlySession;
    private boolean wasActive = false;

    private void showToast(final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }


    public CameraHandler(Context context) {
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        createSaveDirectory();
        startBackgroundThread();
        findCameraIds();
        setupImageReaders();
    }

    private void findCameraIds() {
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        frontCameraId = cameraId;
                    } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        backCameraId = cameraId;
                        Boolean flashAvailable = characteristics.get(
                            CameraCharacteristics.FLASH_INFO_AVAILABLE);
                        hasFlash = flashAvailable != null && flashAvailable;
                    }
                }
            }
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "查找相机ID失败", e);
        }
    }

    private void setupImageReaders() {
        if (frontImageReader != null) {
            frontImageReader.close();
            frontImageReader = null;
        }
        if (backImageReader != null) {
            backImageReader.close();
            backImageReader = null;
        }
        frontImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,
            ImageFormat.JPEG, 2);
        backImageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,
            ImageFormat.JPEG, 2);

        ImageReader.OnImageAvailableListener imageListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                final Image image = reader.acquireNextImage();
                if (image != null) {
                    backgroundHandler.post(new ImageSaver(image, reader == frontImageReader));
                }
            }
        };

        frontImageReader.setOnImageAvailableListener(imageListener, backgroundHandler);
        backImageReader.setOnImageAvailableListener(imageListener, backgroundHandler);
    }

    private class ImageSaver implements Runnable {
        private final Image image;
        private final boolean isFront;

        ImageSaver(Image image, boolean isFront) {
            this.image = image;
            this.isFront = isFront;
        }

        @Override
        public void run() {
            if (image == null) return;

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            save(bytes, isFront);
            image.close();
        }
    }

    private void createSaveDirectory() {
        File directory = new File(SAVE_PATH);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    private void startBackgroundThread() {
        if (backgroundThread != null && backgroundThread.isAlive()) {
            return;
        }
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "停止后台线程失败", e);
            }
        }
    }

    public void startBothCameras(final SurfaceHolder frontHolder, final SurfaceHolder backHolder) {
        cachedFrontHolder = frontHolder;
        cachedBackHolder = backHolder;
        wasActive = true;
        initializeCameras();
    }

    public void openCamera() {
        if (isCameraInitialized) {
            showToast("摄像头已开启");
            return;
        }
        if (cachedFrontHolder != null || cachedBackHolder != null) {
            initializeCameras();
            showToast("摄像头已打开");
        } else {
            showToast("请先启动双摄预览");
        }
    }

    public void closeCamera() {
        if (isCameraInitialized) {
            releaseCameraResources();
            isCameraInitialized = false;
            showToast("摄像头已关闭");
        } else {
            showToast("摄像头未开启");
        }
    }

    private void initializeCameras() {
        if (isCameraInitialized) {
            releaseCameraResources();
        }

        try {
            if (frontCameraId != null && cachedFrontHolder != null) {
                frontPreviewSurface = cachedFrontHolder.getSurface();
                CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(frontCameraId);
                frontSensorSize = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                Float maxZoom = characteristics.get(
                    CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                if (maxZoom != null) {
                    frontMaxZoom = maxZoom;
                }
                setupCamera(frontCameraId, cachedFrontHolder, true);
            }

            if (backCameraId != null && cachedBackHolder != null) {
                backPreviewSurface = cachedBackHolder.getSurface();
                CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(backCameraId);
                backSensorSize = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                Float maxZoom = characteristics.get(
                    CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                if (maxZoom != null) {
                    backMaxZoom = maxZoom;
                }
                setupCamera(backCameraId, cachedBackHolder, false);
            }

            isCameraInitialized = true;
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "初始化相机失败", e);
            showToast("相机初始化失败");
        }
    }

    private void setupCamera(final String cameraId, final SurfaceHolder holder,
            final boolean isFront) {
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("相机打开超时");
            }

            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraOpenCloseLock.release();
                    if (isFront) {
                        frontCamera = camera;
                    } else {
                        backCamera = camera;
                    }
                    createCameraPreviewSession(camera, holder, isFront);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    cameraOpenCloseLock.release();
                    camera.close();
                    if (isFront) {
                        frontCamera = null;
                    } else {
                        backCamera = null;
                    }
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    cameraOpenCloseLock.release();
                    camera.close();
                    if (isFront) {
                        frontCamera = null;
                    } else {
                        backCamera = null;
                    }
                    String errorMsg = "相机错误: ";
                    switch (error) {
                        case ERROR_CAMERA_DEVICE:
                            errorMsg += "设备错误";
                            break;
                        case ERROR_CAMERA_DISABLED:
                            errorMsg += "设备被禁用";
                            break;
                        case ERROR_CAMERA_IN_USE:
                            errorMsg += "设备正在使用";
                            break;
                        case ERROR_CAMERA_SERVICE:
                            errorMsg += "服务错误";
                            break;
                        case ERROR_MAX_CAMERAS_IN_USE:
                            errorMsg += "达到最大相机使用数";
                            break;
                        default:
                            errorMsg += "未知错误: " + error;
                    }
                    showToast(errorMsg);
                }
            }, backgroundHandler);
        } catch (CameraAccessException | InterruptedException | SecurityException e) {
            Log.e(TAG, "设置相机失败", e);
            cameraOpenCloseLock.release();
        }
    }

    private void createCameraPreviewSession(final CameraDevice camera, SurfaceHolder holder,
            final boolean isFront) {
        try {
            final Surface previewSurface = holder.getSurface();
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);

            final ImageReader imageReader = isFront ? frontImageReader : backImageReader;
            surfaces.add(imageReader.getSurface());

            final CaptureRequest.Builder previewRequestBuilder =
                camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);

            if (!isFront) {
                backPreviewBuilder = previewRequestBuilder;
                if (hasFlash && isFlashOn) {
                    backPreviewBuilder.set(CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_TORCH);
                }
            }

            camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        if (isFront) {
                            frontSession = session;
                        } else {
                            backSession = session;
                        }

                        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                            CaptureRequest.CONTROL_MODE_AUTO);

                        final Rect sensorSize = isFront ? frontSensorSize : backSensorSize;
                        final float currentZoom = isFront ? frontCurrentZoom : backCurrentZoom;

                        if (sensorSize != null) {
                            previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION,
                                getZoomRect(sensorSize, currentZoom));
                        }

                        mPreviewRequest = previewRequestBuilder.build();
                        session.setRepeatingRequest(mPreviewRequest, null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "设置预览失败", e);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    showToast("创建相机预览失败");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "创建预览会话失败", e);
        }
    }

    private void updatePreview() {
        if (backCamera == null || backSession == null) return;

        try {
            if (hasFlash && backPreviewBuilder != null) {
                backPreviewBuilder.set(CaptureRequest.FLASH_MODE,
                    isFlashOn ? CaptureRequest.FLASH_MODE_TORCH :
                               CaptureRequest.FLASH_MODE_OFF);
            }
            if (backPreviewBuilder != null) {
                mPreviewRequest = backPreviewBuilder.build();
                backSession.setRepeatingRequest(mPreviewRequest, null, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "更新预览失败", e);
        }
    }

    public void setFlashIndependent(boolean on) {
        if (!hasFlash) {
            showToast("此设备不支持闪光灯");
            return;
        }

        isFlashOn = on;

        if (backCamera != null && backSession != null) {
            updatePreview();
            showToast(on ? "闪光灯已开启" : "闪光灯已关闭");
            return;
        }

        if (on) {
            openFlashOnly();
        } else {
            closeFlashOnly();
        }
    }

    private void openFlashOnly() {
        if (flashOnlyCamera != null) {
            return;
        }

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("相机打开超时");
            }

            cameraManager.openCamera(backCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraOpenCloseLock.release();
                    flashOnlyCamera = camera;
                    createFlashOnlySession(camera);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    cameraOpenCloseLock.release();
                    camera.close();
                    flashOnlyCamera = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    cameraOpenCloseLock.release();
                    camera.close();
                    flashOnlyCamera = null;
                }
            }, backgroundHandler);
        } catch (CameraAccessException | InterruptedException e) {
            cameraOpenCloseLock.release();
            Log.e(TAG, "打开闪光灯专用相机失败", e);
            showToast("闪光灯开启失败");
        }
    }

    private void createFlashOnlySession(final CameraDevice camera) {
        try {
            final CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);

            camera.createCaptureSession(new ArrayList<Surface>(),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        flashOnlySession = session;
                        try {
                            flashOnlySession.setRepeatingRequest(
                                builder.build(), null, backgroundHandler);
                            showToast("闪光灯已开启");
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "设置独立闪光灯请求失败", e);
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                        showToast("闪光灯开启失败");
                    }
                }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "创建闪光灯会话失败", e);
        }
    }

    private void closeFlashOnly() {
        try {
            if (flashOnlySession != null) {
                flashOnlySession.close();
                flashOnlySession = null;
            }
            if (flashOnlyCamera != null) {
                flashOnlyCamera.close();
                flashOnlyCamera = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭独立闪光灯失败", e);
        }
        showToast("闪光灯已关闭");
    }

    public void setFrontZoom(float zoom) {
        if (frontCamera == null || frontSensorSize == null || frontSession == null) {
            showToast("前置相机未就绪");
            return;
        }

        frontCurrentZoom = Math.max(1.0f, Math.min(zoom, frontMaxZoom));
        updateZoom(true);
    }

    public void setBackZoom(float zoom) {
        if (backCamera == null || backSensorSize == null || backSession == null) {
            showToast("后置相机未就绪");
            return;
        }

        backCurrentZoom = Math.max(1.0f, Math.min(zoom, backMaxZoom));
        updateZoom(false);
    }

    private void updateZoom(final boolean isFront) {
        try {
            final CameraDevice camera = isFront ? frontCamera : backCamera;
            final CameraCaptureSession session = isFront ? frontSession : backSession;
            final Rect sensorSize = isFront ? frontSensorSize : backSensorSize;
            final float currentZoom = isFront ? frontCurrentZoom : backCurrentZoom;
            final Surface previewSurface = isFront ? frontPreviewSurface : backPreviewSurface;

            if (camera == null || session == null || sensorSize == null ||
                    previewSurface == null) {
                showToast((isFront ? "前置" : "后置") + "相机未就绪");
                return;
            }

            CaptureRequest.Builder builder = camera.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            builder.addTarget((isFront ? frontImageReader : backImageReader).getSurface());

            Rect zoomRect = getZoomRect(sensorSize, currentZoom);
            builder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);

            if (!isFront && hasFlash) {
                builder.set(CaptureRequest.FLASH_MODE,
                    isFlashOn ? CaptureRequest.FLASH_MODE_TORCH :
                               CaptureRequest.FLASH_MODE_OFF);
            }

            session.setRepeatingRequest(builder.build(), null, backgroundHandler);

            showToast(String.format(Locale.getDefault(),
                "%s摄像头变焦: %.1f", isFront ? "前置" : "后置", currentZoom));
        } catch (CameraAccessException e) {
            Log.e(TAG, "设置变焦失败", e);
            showToast("设置变焦失败");
        }
    }

    private Rect getZoomRect(Rect sensorSize, float zoomLevel) {
        int centerX = sensorSize.width() / 2;
        int centerY = sensorSize.height() / 2;
        int deltaX = (int)((0.5f * sensorSize.width()) / zoomLevel);
        int deltaY = (int)((0.5f * sensorSize.height()) / zoomLevel);

        return new Rect(
            centerX - deltaX,
            centerY - deltaY,
            centerX + deltaX,
            centerY + deltaY
        );
    }

    public void takeFrontPhoto() {
        if (frontCamera == null) {
            showToast("前置相机未就绪");
            return;
        }
        takePicture(frontCamera, true);
    }

    public void takeBackPhoto() {
        if (backCamera == null) {
            showToast("后置相机未就绪");
            return;
        }
        takePicture(backCamera, false);
    }

    public void takeBothPhotos() {
        if (frontCamera != null) {
            takePicture(frontCamera, true);
        } else {
            showToast("前置相机未就绪");
        }

        if (backCamera != null) {
            takePicture(backCamera, false);
        } else {
            showToast("后置相机未就绪");
        }
    }

    private void takePicture(final CameraDevice camera, final boolean isFront) {
        final ImageReader reader = isFront ? frontImageReader : backImageReader;
        final Surface previewSurface = isFront ? frontPreviewSurface : backPreviewSurface;
        final CameraCaptureSession captureSession = isFront ? frontSession : backSession;
        final Rect sensorSize = isFront ? frontSensorSize : backSensorSize;
        final float currentZoom = isFront ? frontCurrentZoom : backCurrentZoom;

        if (camera == null || captureSession == null || reader == null ||
            previewSurface == null || sensorSize == null) {
            showToast((isFront ? "前置" : "后置") + "相机未就绪");
            return;
        }

        try {
            final CaptureRequest.Builder captureBuilder =
                camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            if (!isFront && hasFlash) {
                captureBuilder.set(CaptureRequest.FLASH_MODE,
                    isFlashOn ? CaptureRequest.FLASH_MODE_TORCH :
                               CaptureRequest.FLASH_MODE_OFF);
            }
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION,
                getZoomRect(sensorSize, currentZoom));

            // Gracefully stop preview before capture
            try {
                captureSession.stopRepeating();
            } catch (Exception e) {
                Log.e(TAG, "停止预览失败", e);
            }

            captureSession.capture(captureBuilder.build(),
                new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(CameraCaptureSession session,
                            CaptureRequest request, TotalCaptureResult result) {
                        try {
                            final CaptureRequest.Builder previewBuilder =
                                camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            previewBuilder.addTarget(previewSurface);
                            previewBuilder.set(CaptureRequest.SCALER_CROP_REGION,
                                getZoomRect(sensorSize, currentZoom));
                            if (!isFront && hasFlash) {
                                previewBuilder.set(CaptureRequest.FLASH_MODE,
                                    isFlashOn ? CaptureRequest.FLASH_MODE_TORCH :
                                               CaptureRequest.FLASH_MODE_OFF);
                            }
                            session.setRepeatingRequest(previewBuilder.build(),
                                null, backgroundHandler);
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "恢复预览失败", e);
                        }
                    }

                    @Override
                    public void onCaptureFailed(CameraCaptureSession session,
                            CaptureRequest request, CaptureFailure failure) {
                        String reason = "";
                        switch (failure.getReason()) {
                            case CaptureFailure.REASON_ERROR:
                                reason = "发生错误";
                                break;
                            case CaptureFailure.REASON_FLUSHED:
                                reason = "请求被取消";
                                break;
                            default:
                                reason = "未知原因";
                        }
                        showToast("拍照失败: " + reason);
                        // 失败也要恢复预览，否则相机卡死
                        try {
                            session.setRepeatingRequest(mPreviewRequest, null, backgroundHandler);
                        } catch (Exception e) {
                            Log.e(TAG, "恢复预览失败", e);
                        }
                    }
                },
                backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "拍照失败", e);
            showToast("拍照失败");
        }
    }

    private void save(byte[] bytes, boolean isFront) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
            Locale.getDefault()).format(new Date());
        String filename = (isFront ? "front_" : "back_") + timestamp + ".jpg";
        File file = new File(SAVE_PATH + filename);

        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            showToast("照片已保存: " + file.getPath());
        } catch (IOException e) {
            Log.e(TAG, "保存照片失败", e);
            showToast("保存照片失败: " + e.getMessage());
        }
    }

    public void toggleFlash() {
        if (!hasFlash) {
            showToast("此设备不支持闪光灯");
            return;
        }
        setFlashIndependent(!isFlashOn);
    }

    private void releaseCameraResources() {
        try {
            cameraOpenCloseLock.acquire();

            if (frontSession != null) {
                frontSession.close();
                frontSession = null;
            }
            if (backSession != null) {
                backSession.close();
                backSession = null;
            }
            if (frontCamera != null) {
                frontCamera.close();
                frontCamera = null;
            }
            if (backCamera != null) {
                backCamera.close();
                backCamera = null;
            }
            if (frontImageReader != null) {
                frontImageReader.close();
                frontImageReader = null;
            }
            if (backImageReader != null) {
                backImageReader.close();
                backImageReader = null;
            }
            closeFlashOnly();
        } catch (InterruptedException e) {
            Log.e(TAG, "相机资源释放中断", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    public void releaseCamera() {
        isCameraInitialized = false;
        releaseCameraResources();
    }

    public void onResume() {
        startBackgroundThread();
        setupImageReaders();
        if (wasActive && cachedFrontHolder != null && cachedFrontHolder.getSurface().isValid()) {
            initializeCameras();
        }
    }

    public void onPause() {
        releaseCameraResources();
        // Keep background thread alive for faster resume
        // stopBackgroundThread();
    }
}