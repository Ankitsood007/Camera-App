package com.codewithankit.mergesample;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class VideoRecordingActivity extends AppCompatActivity {

    private TextureView mTextureView;
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int mCaptureState = STATE_PREVIEW;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height){
            setupCamera(width , height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed    (SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private CameraDevice mCameraDevie;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevie = camera;
            if(mIsRecording){
                startRecord();
                mMediaRecorder.start();
                Toast.makeText(getApplicationContext() , "Recording..."  , Toast.LENGTH_LONG).show();
            }else {
                startPreview();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevie = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevie = null;
        }
    };

    private String mCameraId;
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private Size mPreviewSize;
    private Chronometer chronometer;
    private Size mVideoSize;
    private Size mImageSize;


    private int mTotalRotation;
    private MediaRecorder mMediaRecorder;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private ImageButton mRecordImageButton;
    private boolean mIsRecording = false;
    private File mVideoFolder;
    private String mVideoFileName;
    private File mImageFolder;
    private String mImageFileName;
    private ImageButton mStillImageCaptureButton;


    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0 , 0);
        ORIENTATIONS.append(Surface.ROTATION_90 , 90);
        ORIENTATIONS.append(Surface.ROTATION_180 , 180);
        ORIENTATIONS.append(Surface.ROTATION_270 , 270);
    }

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size o1, Size o2) {
            return Long.signum((long)o1.getWidth() * o1.getHeight() / (long) o2.getHeight() * o2.getWidth());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_recording);

        createVideoFolder();

        mMediaRecorder = new MediaRecorder();
        chronometer = findViewById(R.id.timerID);

        mTextureView = (TextureView) findViewById(R.id.textureView);
        mRecordImageButton = (ImageButton)findViewById(R.id.videoBtnID);
        mRecordImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mIsRecording){
                    chronometer.stop();
                    chronometer.setVisibility(View.INVISIBLE);
                    mIsRecording = false;
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    startPreview();
                    Toast.makeText(getApplicationContext() , "Recorded!" , Toast.LENGTH_LONG).show();
                    Snackbar.make(mTextureView , "Saved to : fileExplorer/movies/FinalAppMovies" , Snackbar.LENGTH_LONG).show();
                }else{
                    checkWriteStoragePermission();
                }
            }
        });
    }

    @Override
    protected void onResume(){
        super.onResume();

        startBackgroundThread();

        if(mTextureView.isAvailable()){
            setupCamera(mTextureView.getWidth() , mTextureView.getHeight());
            connectCamera();
        }
        else{
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext() , "This application will not run without the camera permission." , Toast.LENGTH_SHORT).show();
            }

//            if(grantResults[1] != PackageManager.PERMISSION_GRANTED){
//                Toast.makeText(getApplicationContext() , "Application will not have Audio in the recording." , Toast.LENGTH_SHORT).show();
//            }
        }

        if(requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                mIsRecording = true;
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                Toast.makeText(this , "App needs to save video to run" , Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        closeCamera();

        stopBackgroundThread();

        super.onPause();
    }

    @Override
    //just a utility function to make the camera app in a full screen mode.
    public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if(hasFocus){
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    private void setupCamera(int width , int height){

        Bundle extras = getIntent().getExtras();
        String whichCamera = extras.getString("Id");

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cameraID : cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraID);
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                mTotalRotation = SensorToDeviceRotation(cameraCharacteristics , deviceOrientation);
//
                boolean swapRotation = mTotalRotation == 90 || mTotalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
//
                if(swapRotation){
                    rotatedWidth  = height;
                    rotatedHeight = width;
                }
//
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class) , rotatedWidth , rotatedHeight);
                mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class ) , rotatedWidth , rotatedHeight);
                mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG) , rotatedWidth , rotatedHeight);
                mCameraId = cameraID;
                return ;
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void connectCamera(){
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){

                if(ContextCompat.checkSelfPermission(this , Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                    cameraManager.openCamera(mCameraId , mCameraDeviceStateCallback , mBackgroundHandler);
                }else{
                    //this is when the user denies the access to his camera.
                    if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                        Toast.makeText(this , "Video App requires access to the camera." , Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {android.Manifest.permission.CAMERA} , REQUEST_CAMERA_PERMISSION_RESULT);
                }
            }else{
                cameraManager.openCamera(mCameraId , mCameraDeviceStateCallback , mBackgroundHandler);
            }
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void startRecord(){
        try {
            //step1. setting up the surface
            setUpMediaRecorder();
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth() , mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            Surface recordSurface = mMediaRecorder.getSurface();
            //step2.setting up the captureBuilder for the captureRequest;

            mCaptureRequestBuilder = mCameraDevie.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(recordSurface);

            mCameraDevie.createCaptureSession(Arrays.asList(previewSurface, recordSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(mCaptureRequestBuilder.build() , null , null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            } , null);
        } catch (IOException | CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview(){
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth() , mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCameraDevie.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCameraDevie.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                //here we'll set up a setRepeatingRequest() , means we always want to update the things since its a video after all
                //so the surface needs to be updated every once in a while.

                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(mCaptureRequestBuilder.build() , null ,mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(getApplicationContext() , "Unable to set up Camera preview" , Toast.LENGTH_SHORT).show();
                }
            } , null);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }


    private void closeCamera(){
        if(mCameraDevie != null){
            mCameraDevie.close();
            mCameraDevie = null;
        }
    }

    private void startBackgroundThread(){
        mBackgroundHandlerThread = new HandlerThread("Camera2VideoImage");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread(){
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int SensorToDeviceRotation(CameraCharacteristics cameraCharacteristics , int deviceOrientation){
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices , int w , int h){
        List<Size> bigEnough = new ArrayList<Size>();
        for(Size option : choices){
            if(option.getHeight() == option.getWidth() * h / w && option.getWidth() >= w && option.getHeight()>=h){
                bigEnough.add(option);
            }
        }
        if(bigEnough.size() > 0){
            return Collections.min(bigEnough , new CompareSizeByArea());
        }else{
            return choices[0];
        }
    }

    private void createVideoFolder(){
        File movieFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        mVideoFolder = new File(movieFile , "FinalAppMovies");
        if(!mVideoFolder.exists()){
            mVideoFolder.mkdirs();
        }
    }

    private File createVideoFileName() throws IOException{
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "VIDEO_" + timestamp + "_";
        File videoFile = File.createTempFile(prepend , ".mp4" , mVideoFolder);
        mVideoFileName = videoFile.getAbsolutePath();
        return videoFile;
    }

    private void checkWriteStoragePermission(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){

            if(ContextCompat.checkSelfPermission(this , Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
                mIsRecording = true;
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                startRecord();
                mMediaRecorder.start();
                chronometer.setBase(SystemClock.elapsedRealtime());
                chronometer.setVisibility(View.VISIBLE);
                chronometer.start();
                Toast.makeText(getApplicationContext() , "Recording..." , Toast.LENGTH_LONG).show();
            }else{
                if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                    Toast.makeText(this , "This Application needs to be able to save videos." , Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE} , REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT);
            }
        }
        else
        {
            mIsRecording = true;
            try {
                createVideoFileName();
            } catch (IOException e) {
                e.printStackTrace();
            }
            startRecord();
            mMediaRecorder.start();
            chronometer.setBase(SystemClock.elapsedRealtime());
            chronometer.setVisibility(View.VISIBLE);
            chronometer.start();
            Toast.makeText(getApplicationContext() , "Recording has been started !!"  , Toast.LENGTH_LONG).show();
        }
    }

    private void setUpMediaRecorder() throws IOException{

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
//        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mVideoFileName);
        mMediaRecorder.setVideoEncodingBitRate(1000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth() , mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOrientationHint(mTotalRotation);
        mMediaRecorder.prepare();
    }

}
