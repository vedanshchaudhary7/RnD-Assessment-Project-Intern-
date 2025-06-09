package com.example.edgedetectionviewer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {
    private CameraGLSurfaceView glSurfaceView;
    private Button toggleButton;
    private TextView fpsTextView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Size previewSize;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private boolean showEdges = false;
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private float fps = 0;

    // Native methods
    private native void initOpenCV();
    private native void processFrame(long texIn, long texOut, boolean applyEdges);
    private native void releaseOpenCV();

    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        glSurfaceView = findViewById(R.id.glSurfaceView);
        toggleButton = findViewById(R.id.toggleButton);
        fpsTextView = findViewById(R.id.fpsTextView);

        toggleButton.setText("Show Edges");
        toggleButton.setOnClickListener(v -> {
            showEdges = !showEdges;
            toggleButton.setText(showEdges ? "Show Raw" : "Show Edges");
            glSurfaceView.setApplyEdges(showEdges);
        });

        // Initialize OpenCV
        initOpenCV();

        // Request camera permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
        }
    }

    private void startCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            previewSize = new Size(1280, 720); // Fixed preview size for simplicity
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    startPreview();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        try {
            SurfaceTexture texture = glSurfaceView.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);

            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            startBackgroundThread();
            cameraDevice.createCaptureSession(
                    java.util.Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                    },
                    null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void updateFPS() {
        frameCount++;
        long currentTime = System.nanoTime();
        if (lastFrameTime == 0) {
            lastFrameTime = currentTime;
            frameCount = 0;
            return;
        }
        long elapsed = currentTime - lastFrameTime;
        if (elapsed >= 1_000_000_000L) { // 1 second
            fps = frameCount * 1_000_000_000L / (float) elapsed;
            runOnUiThread(() -> fpsTextView.setText(String.format("FPS: %.2f", fps)));
            frameCount = 0;
            lastFrameTime = currentTime;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
        if (cameraDevice != null) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        stopBackgroundThread();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseOpenCV();
    }
}