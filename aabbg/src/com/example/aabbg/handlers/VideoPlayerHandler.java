// 视频播放器：支持横竖屏切换播放，进度条外置于视频下方，提取并显示视频的全部元信息（编码方式、分辨率、帧率、时长、比特率等）
package com.example.aabbg.handlers;

import android.app.Activity;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class VideoPlayerHandler implements SurfaceHolder.Callback {
    // 旧版 SDK 可能没有这两个常量，自行定义
    private static final int KEY_VIDEO_CODEC = 7;
    private static final int KEY_AUDIO_CODEC = 5;

    private final Context context;
    // private final Activity activity;
    private final FrameLayout videoContainer;
    private final SeekBar videoSeekBar;
    private final TextView txtVideoInfo;
    private final Button btnToggleOrientation;

    private SurfaceView videoSurfaceView;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private Handler seekHandler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBarRunnable;
    private int videoDuration = 0;
    private int videoWidth = 1920;
    private int videoHeight = 1080;

    public VideoPlayerHandler(Activity activity, FrameLayout videoContainer,
            SeekBar videoSeekBar, TextView txtVideoInfo, Button btnToggleOrientation) {
        this.context = activity;
        this.videoContainer = videoContainer;
        this.videoSeekBar = videoSeekBar;
        this.txtVideoInfo = txtVideoInfo;
        this.btnToggleOrientation = btnToggleOrientation;

        setupVideoSurface();
        setupSeekBar();
        setupOrientationButton();
    }

    private void setupVideoSurface() {
        videoSurfaceView = new SurfaceView(context);
        videoSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        videoSurfaceView.getHolder().addCallback(this);
        videoContainer.addView(videoSurfaceView);
    }

    private void setupSeekBar() {
        videoSeekBar.setEnabled(false);
        videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupOrientationButton() {
        btnToggleOrientation.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                toggleFullscreen();
            }
        });
    }

    public void playVideo(String videoPath) {
        if (videoPath == null || videoPath.trim().isEmpty()) {
            Toast.makeText(context, "请输入视频路径", Toast.LENGTH_SHORT).show();
            return;
        }

        File videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            Toast.makeText(context, "文件不存在: " + videoPath, Toast.LENGTH_SHORT).show();
            return;
        }

        stopAndRelease();

        try {
            // Read video metadata for resolution and rotation BEFORE preparing
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoPath);
            String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            int videoWidth = 1920;
            int videoHeight = 1080;
            int rotation = 0;
            try {
                if (widthStr != null) videoWidth = Integer.parseInt(widthStr);
                if (heightStr != null) videoHeight = Integer.parseInt(heightStr);
            // Store as fields for later use
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
                if (rotationStr != null) rotation = Integer.parseInt(rotationStr);
            } catch (NumberFormatException e) {}
            retriever.release();

            // Apply video rotation to the SurfaceView
            videoSurfaceView.setRotation((float) rotation);

            // Adjust container size based on video aspect ratio
            adjustVideoContainerSize(false);

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDisplay(videoSurfaceView.getHolder());
            mediaPlayer.setDataSource(videoPath);
            mediaPlayer.prepare();
            mediaPlayer.start();

            videoDuration = mediaPlayer.getDuration();
            videoSeekBar.setMax(videoDuration);
            videoSeekBar.setEnabled(true);
            isPlaying = true;

            updateSeekBarRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mediaPlayer != null && isPlaying) {
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        videoSeekBar.setProgress(currentPosition);
                        seekHandler.postDelayed(this, 300);
                    }
                }
            };
            seekHandler.postDelayed(updateSeekBarRunnable, 300);

            displayVideoMetadata(videoPath);

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    isPlaying = false;
                    videoSeekBar.setProgress(0);
                    seekHandler.removeCallbacks(updateSeekBarRunnable);
                    Toast.makeText(context, "播放完成", Toast.LENGTH_SHORT).show();
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Toast.makeText(context, "播放错误: what=" + what + " extra=" + extra,
                        Toast.LENGTH_LONG).show();
                    return false;
                }
            });

            Toast.makeText(context, "正在播放: " + videoFile.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "播放失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void displayVideoMetadata(String videoPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoPath);

            StringBuilder info = new StringBuilder();
            info.append("===== 视频信息 =====\n\n");

            String mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            info.append("MIME类型: ").append(mimeType != null ? mimeType : "未知").append("\n");

            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (width != null && height != null) {
                info.append("分辨率: ").append(width).append("x").append(height).append("\n");
            }

            String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (bitrate != null) {
                long bitrateLong = Long.parseLong(bitrate);
                info.append("比特率: ").append(formatBitrate(bitrateLong)).append("\n");
            }

            String videoCodec = retriever.extractMetadata(KEY_VIDEO_CODEC);
            info.append("视频编码: ").append(videoCodec != null ? videoCodec : "未知").append("\n");

            String audioCodec = retriever.extractMetadata(KEY_AUDIO_CODEC);
            info.append("音频编码: ").append(audioCodec != null ? audioCodec : "未知").append("\n");

            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                long ms = Long.parseLong(duration);
                info.append("时长: ").append(formatDuration(ms)).append("\n");
            }

            String frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            if (frameRate != null) {
                info.append("帧率: ").append(frameRate).append(" fps\n");
            }

            String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            info.append("旋转角度: ").append(rotation != null ? rotation : "0").append("°\n");

            File videoFile = new File(videoPath);
            info.append("文件大小: ").append(formatFileSize(videoFile.length())).append("\n");
            info.append("文件路径: ").append(videoPath).append("\n");

            info.append("\n===== 设备信息 =====\n");
            info.append("手机型号: ").append(Build.MODEL).append("\n");
            info.append("制造商: ").append(Build.MANUFACTURER).append("\n");
            info.append("Android版本: ").append(Build.VERSION.RELEASE).append("\n");

            txtVideoInfo.setText(info.toString());
        } catch (Exception e) {
            txtVideoInfo.setText("无法读取视频元信息: " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {}
        }
    }

    public void toggleFullscreen() {
        // Toggle rotation of the video view itself (not the entire activity)
        if (videoSurfaceView == null) return;
        float currentRotation = videoSurfaceView.getRotation();
        if (currentRotation == 0f) {
            videoSurfaceView.setRotation(90f);
            btnToggleOrientation.setText("竖屏显示");
            adjustVideoContainerSize(true);
        } else {
            videoSurfaceView.setRotation(0f);
            btnToggleOrientation.setText("横屏显示");
            adjustVideoContainerSize(false);
        }
    }

    public void pause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
        }
    }

    public void resume() {
        if (mediaPlayer != null && !isPlaying) {
            mediaPlayer.start();
            isPlaying = true;
            if (updateSeekBarRunnable != null) {
                seekHandler.postDelayed(updateSeekBarRunnable, 300);
            }
        }
    }

    public void stopAndRelease() {
        seekHandler.removeCallbacks(updateSeekBarRunnable);
        if (mediaPlayer != null) {
            try {
                if (isPlaying) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {}
            mediaPlayer = null;
        }
        isPlaying = false;
        videoSeekBar.setProgress(0);
        videoSeekBar.setEnabled(false);
        videoDuration = 0;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mediaPlayer != null) {
            mediaPlayer.setDisplay(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    private void adjustVideoContainerSize(boolean isRotated) {
        if (videoSurfaceView == null) return;

        int containerWidth = videoContainer.getWidth();
        int containerHeight = videoContainer.getHeight();
        if (containerWidth <= 0 || containerHeight <= 0) return;

        int displayW = videoWidth;
        int displayH = videoHeight;
        if (isRotated) {
            displayW = videoHeight;
            displayH = videoWidth;
        }
        if (displayW <= 0 || displayH <= 0) {
            displayW = 16;
            displayH = 9;
        }

        float aspect = (float) displayW / displayH;
        android.view.ViewGroup.LayoutParams params = videoSurfaceView.getLayoutParams();

        // Fit within container while keeping aspect ratio
        int wByH = (int) (containerHeight * aspect);
        int hByW = (int) (containerWidth / aspect);

        if (wByH <= containerWidth) {
            // Height limited, letterbox left/right
            params.width = wByH;
            params.height = containerHeight;
        } else {
            // Width limited, letterbox top/bottom
            params.width = containerWidth;
            params.height = hByW;
        }
        videoSurfaceView.setLayoutParams(params);
    }

    private String formatDuration(long milliseconds) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private String formatBitrate(long bitrate) {
        if (bitrate >= 1000000) {
            return String.format(Locale.getDefault(), "%.1f Mbps", bitrate / 1000000.0);
        } else if (bitrate >= 1000) {
            return String.format(Locale.getDefault(), "%.1f Kbps", bitrate / 1000.0);
        }
        return bitrate + " bps";
    }

    private String formatFileSize(long size) {
        if (size >= 1073741824) {
            return String.format(Locale.getDefault(), "%.2f GB", size / 1073741824.0);
        } else if (size >= 1048576) {
            return String.format(Locale.getDefault(), "%.2f MB", size / 1048576.0);
        } else if (size >= 1024) {
            return String.format(Locale.getDefault(), "%.2f KB", size / 1024.0);
        }
        return size + " B";
    }
}