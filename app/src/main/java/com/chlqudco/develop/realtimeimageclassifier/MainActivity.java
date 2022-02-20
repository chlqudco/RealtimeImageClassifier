package com.chlqudco.develop.realtimeimageclassifier;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "[IC]MainActivity";

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private TextView textView;
    private Classifier cls;

    private int previewWidth = 0;
    private int previewHeight = 0;
    private int sensorOrientation = 0;

    private Bitmap rgbFrameBitmap = null;

    private HandlerThread handlerThread;
    private Handler handler;

    private boolean isProcessingFrame = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //프래그먼트 기본 생성자 불리지 않도록 null 전달
        super.onCreate(null);
        setContentView(R.layout.activity_main);

        //화면이 꺼지지 않도록 유지
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        textView = findViewById(R.id.textView);

        //모델 초기화
        cls = new Classifier(this);
        try {
            cls.init();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        //카메라 권한 확인 후 프래그먼트 생성
        if(checkSelfPermission(CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            setFragment();
        } else {
            requestPermissions(new String[]{CAMERA_PERMISSION}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected synchronized void onDestroy() {
        cls.finish();
        super.onDestroy();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        handlerThread = new HandlerThread("InferenceThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        super.onPause();
    }

    @Override
    public synchronized void onStart() {
        super.onStart();
    }

    @Override
    public synchronized void onStop() {
        super.onStop();
    }

    //권한 확인 작업
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == PERMISSION_REQUEST_CODE) {
            if(grantResults.length > 0 && allPermissionsGranted(grantResults)) {
                setFragment();
            } else {
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    //모든 권한 잘 받았는지 확인
    private boolean allPermissionsGranted(final int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    //카메라 프래그 먼트 생성
    protected void setFragment() {
        Size inputSize = cls.getModelInputSize();
        String cameraId = chooseCamera();

        if(inputSize.getWidth() > 0 && inputSize.getHeight() > 0 && !cameraId.isEmpty()) {
            Fragment fragment = CameraFragment.newInstance((size, rotation) -> {
                        previewWidth = size.getWidth();
                        previewHeight = size.getHeight();
                        sensorOrientation = rotation - getScreenOrientation();
                    },
                    reader->processImage(reader),
                    inputSize,
                    cameraId);

            Log.d(TAG, "inputSize : " + cls.getModelInputSize() + "sensorOrientation : " + sensorOrientation);
            getFragmentManager().beginTransaction().replace(R.id.fragment, fragment).commit();
        } else {
            Toast.makeText(this, "Can't find camera", Toast.LENGTH_SHORT).show();
        }
    }

    //적절한 카메라 선택
    private String chooseCamera() {
        final CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return "";
    }

    //카메라 회전 각 알아내기
    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270: return 270;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_90: return 90;
            default: return 0;
        }
    }

    //이미지 처리 시작
    protected void processImage(ImageReader reader) {
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }

        if(rgbFrameBitmap == null) {
            rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        }

        if (isProcessingFrame) {
            return;
        }

        isProcessingFrame = true;

        final Image image = reader.acquireLatestImage();
        if (image == null) {
            isProcessingFrame = false;
            return;
        }

        YuvToRgbConverter.yuvToRgb(this, image, rgbFrameBitmap);

        runInBackground(() -> {
            if (cls != null && cls.isInitialized()) {
                final Pair<String, Float> output = cls.classify(rgbFrameBitmap, sensorOrientation);

                runOnUiThread(() -> {
                    String resultStr = String.format(Locale.ENGLISH, "class : %s, prob : %.2f%%", output.first, output.second * 100);
                    textView.setText(resultStr);
                });
            }
            image.close();
            isProcessingFrame = false;
        });
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }
}