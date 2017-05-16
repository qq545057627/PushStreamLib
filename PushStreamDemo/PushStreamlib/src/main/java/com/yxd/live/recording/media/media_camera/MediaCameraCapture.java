package com.yxd.live.recording.media.media_camera;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import android.widget.Toast;

import com.yxd.live.recording.media.TextureMovieEncoder;
import com.yxd.live.recording.utils.CameraUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class MediaCameraCapture implements SurfaceTexture.OnFrameAvailableListener{
    public final String TAG = "MediaCameraCapture";
    private Activity mActivity = null;
    private GLSurfaceView mGLView = null;
    private Camera mCamera = null;
    private CameraHandler mCameraHandler = null;
    private CameraSurfaceRenderer mRenderer = null;
    private int mCameraPreviewWidth = -1;
    private int mCameraPreviewHeight = -1;
    private int desiredWidth;
    private int desiredHeight;
    private int mCameraNum;
    private int mCameraId = 1;
    private int mDisplayOrientationDegrees = 0;
    private static TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();
    public void MediaCameraCapture(Activity activity, GLSurfaceView glview, int width, int height, int bitrate, int frameRate, int iFrameInterval){
        mActivity = activity;
        mGLView = glview;
        mGLView.setEGLContextClientVersion(2);     // select GLES 2.0
        this.desiredWidth = width;
        this.desiredHeight = height;

        mCameraHandler = new CameraHandler(this);
        mRenderer = new CameraSurfaceRenderer(mCameraHandler, sVideoEncoder, width, height, bitrate, frameRate, iFrameInterval);
        mGLView.setRenderer(mRenderer);
        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void start(){
        ChangeCaptureState(true);
    }

    public void stop(){
        mCameraHandler.invalidateHandler();
        ChangeCaptureState(false);
        releaseCamera();
        mRenderer.notifyPausing();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture arg0) {
        mGLView.requestRender();
    }



    public void setResolutionChangeFlag(final boolean bResolutionChangeFlag){
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.setResolutionChangeFlag(bResolutionChangeFlag);
            }
        });
    }

    public void raiseBitrate(final int width, final int height, final int bitrate){
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.raiseBitrate(width, height, bitrate);
            }
        });
    }

    public void dropBitrate(final int width, final int height, final int bitrate){
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.dropBitrate(width, height, bitrate);
            }
        });
    }

    public void ChangeCaptureState(final Boolean recordingEnabled){
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.changeRecordingState(recordingEnabled);
            }
        });
    }

    public void changeCameraFacing(){
        if (mCameraNum > 1) {
            mCameraId = (mCameraId + 1) % 2;
            releaseCamera();
            openCamera();
            mGLView.queueEvent(new Runnable() {
                @Override public void run() {
                    // notify the renderer that we want to change the camera facing
                    mRenderer.changeCameraFacing();
                }
            });
        }
        else {
            Toast.makeText(mActivity, "抱歉，您只有1个摄像头，无法切换!", Toast.LENGTH_SHORT).show();
        }
    }

    public void onResume(){
        mGLView.onResume();
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                mRenderer.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
            }
        });
    }

    public void onPause(){
        mGLView.onPause();
    }

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    private void setSurfaceTexture(SurfaceTexture st) {
        st.setOnFrameAvailableListener(this);
        try {
            mCamera.setPreviewTexture(st);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        mCamera.setDisplayOrientation(mDisplayOrientationDegrees);
        mCamera.startPreview();
    }


    /**
     * Opens a camera, and attempts to establish preview mode at the specified width and height.
     * <p>
     * Sets mCameraPreviewWidth and mCameraPreviewHeight to the actual width/height of the preview.
     */
    private void openCamera() {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        int degrees = 0;
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;

            case Surface.ROTATION_90:
                degrees = 90;
                break;

            case Surface.ROTATION_180:
                degrees = 180;
                break;

            case Surface.ROTATION_270:
                degrees = 270;
                break;

            default:
                break;
        }


        // Try to find a front-facing camera (e.g. for videoconferencing).
        mCameraNum = Camera.getNumberOfCameras();
        if (mCameraNum > 1) {
            Camera.getCameraInfo(mCameraId, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mDisplayOrientationDegrees = (info.orientation + degrees) % 360;
                mDisplayOrientationDegrees = (360 - mDisplayOrientationDegrees) % 360;
            }else{
                mDisplayOrientationDegrees = (info.orientation - degrees + 360) % 360;
            }
            mCamera = Camera.open(mCameraId);
        }else {
            mCamera = Camera.open();
            mCameraId = 0;
            mDisplayOrientationDegrees = (info.orientation - degrees + 360) % 360;
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = mCamera.getParameters();

        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);

        CameraUtils.chooseFixedPreviewFps(parms, 24000);
        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);

        // leave the frame rate set to default
        mCamera.setParameters(parms);

        Camera.Size mCameraPreviewSize = parms.getPreviewSize();
        mCameraPreviewWidth = mCameraPreviewSize.width;
        mCameraPreviewHeight = mCameraPreviewSize.height;
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    public boolean switchFilter(){
        if(null != mRenderer){
            return  mRenderer.switchFilter();
        }
        return true;
    }


    /**
     * Handles camera operation requests from other threads.  Necessary because the Camera
     * must only be accessed from one thread.
     */
    static class CameraHandler extends Handler {
        public static final int MSG_SET_OPEN_CAMERA = 0;
        public static final int MSG_SET_SURFACE_TEXTURE = 1;

        // Weak reference to the Activity; only access this from the UI thread.
        private WeakReference<MediaCameraCapture> mWeakCapture;
        CameraHandler(MediaCameraCapture capture) {
            mWeakCapture = new WeakReference<>(capture);
        }

        /**
         * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
         * attempts to access a stale Activity through a handler are caught.
         */
        public void invalidateHandler() {
            mWeakCapture.clear();
        }

        @Override  // runs on UI thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            MediaCameraCapture capture = mWeakCapture.get();
            if (capture == null) {
                return;
            }

            switch (what) {
                case MSG_SET_OPEN_CAMERA:
                    capture.openCamera();
                    break;
                case MSG_SET_SURFACE_TEXTURE:
                    capture.setSurfaceTexture((SurfaceTexture) inputMessage.obj);
                    break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }
}
