package com.yxd.live.recording.media.media_camera;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Build;

import com.yxd.live.recording.camera.gles.FullFrameRect;
import com.yxd.live.recording.media.TextureMovieEncoder;
import com.yxd.live.recording.utils.LogUtils;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class CameraSurfaceRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "CameraSurfaceRenderer";

    private static final int RECORDING_OFF = 0;
    private static final int RECORDING_ON = 1;
    private static final int RECORDING_RESUMED = 2;
    private static final int RECORDING_RESTART = 3;

    private MediaCameraCapture.CameraHandler mCameraHandler;
    private TextureMovieEncoder mVideoEncoder;

    private FullFrameRect mFullScreen;

    private final float[] mSTMatrix = new float[16];
    private int mTextureId;

    private SurfaceTexture mSurfaceTexture;
    private boolean mRecordingEnabled;
    private boolean mIsEncoding;
    private int mRecordingStatus;

    // width/height of the incoming camera preview frames
    private boolean mIncomingSizeUpdated;
    private int mIncomingWidth;
    private int mIncomingHeight;

    private int mCurrentFilter;
    private int mNewFilter;

    private int mWidth = 540;
    private int mHeight = 960;
    private int mBitrate = 950000;
    private int mFrameRate = 25;
    private int mIFrameInterval = 2;
    public boolean switchFilter(){
        if(null != mFullScreen){
            if(null != mVideoEncoder){
                mVideoEncoder.switchFilter();
            }
            return  mFullScreen.switchFilter();
        }
        return true;
    }

    /**
     * Constructs CameraSurfaceRenderer.
     * <p>
     * @param cameraHandler Handler for communicating with UI thread
     * @param movieEncoder video encoder object
     * @param
     */
    public CameraSurfaceRenderer(MediaCameraCapture.CameraHandler cameraHandler,
                                 TextureMovieEncoder movieEncoder, int width, int height, int bitrate, int frameRate, int iFrameInterval) {
        mCameraHandler = cameraHandler;
        mVideoEncoder = movieEncoder;

        mWidth = width;
        mHeight = height;
        mBitrate = bitrate;
        mFrameRate = frameRate;
        mIFrameInterval = iFrameInterval;

        mTextureId = -1;

        mRecordingStatus = -1;
        mRecordingEnabled = false;
        mIsEncoding = false;


        mIncomingSizeUpdated = false;
        mIncomingWidth = mIncomingHeight = -1;

        // We could preserve the old filter mode, but currently not bothering.
        mCurrentFilter = -1;
        // mNewFilter = CameraCaptureActivity.FILTER_NONE;
    }

    public void setResolutionChangeFlag(boolean bResolutionChangeFlag){
        mVideoEncoder.setResolutionChangeFlag(bResolutionChangeFlag);
    }

    public void raiseBitrate(int width, int height, int bitrate) {
        mWidth = width;
        mHeight = height;
        mBitrate = bitrate;
        mRecordingStatus = RECORDING_RESTART;
    }

    public void dropBitrate(int width, int height, int bitrate) {
        mWidth = width;
        mHeight = height;
        mBitrate = bitrate;
        mRecordingStatus = RECORDING_RESTART;
    }

    /**
     * Notifies the renderer thread that the activity is pausing.
     * <p>
     * For best results, call this *after* disabling Camera preview.
     */
    public void notifyPausing() {
        if (mSurfaceTexture != null) {
            LogUtils.d(TAG, "renderer pausing -- releasing SurfaceTexture");
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release();     // assume the GLSurfaceView EGL context is about
            mFullScreen = null;             //  to be destroyed
        }
        mIncomingWidth = mIncomingHeight = -1;
    }

    /**
     * Notifies the renderer that we want to reset or start recording.
     */
    public void changeRecordingState(boolean isRecording) {
        LogUtils.d(TAG, "changeRecordingState: was " + mRecordingEnabled + " now " + isRecording);
        mRecordingEnabled = isRecording;
    }

    /**
     * Changes the filter that we're applying to the camera preview.
     */
    public void changeFilterMode(int filter) {
        mNewFilter = filter;
    }


    public void changeCameraFacing(){
        mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                MediaCameraCapture.CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
    }


    public void setCameraPreviewSize(int width, int height) {
        mIncomingWidth = width;
        mIncomingHeight = height;
        mIncomingSizeUpdated = true;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        LogUtils.d(TAG, "onSurfaceCreated");

        // We're starting up or coming back.  Either way we've got a new EGLContext that will
        // need to be shared with the video encoder, so figure out if a recording is already
        // in progress.
        mIsEncoding = mVideoEncoder.isRecording();
        LogUtils.i(TAG, "mIsEncoding is " + mIsEncoding);
        if (mIsEncoding) {
            mRecordingStatus = RECORDING_RESUMED;
        } else {
            mRecordingStatus = RECORDING_OFF;
        }
        LogUtils.i(TAG, "mRecordingStatus is " + mRecordingStatus);
        // mRecordingEnabled = true;


    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        LogUtils.d(TAG, "onSurfaceChanged " + width + "x" + height);
        // Set up the texture blitter that will be used for on-screen display.  This
        // is *not* applied to the recording, because that uses a separate shader.
        mFullScreen = new FullFrameRect(width, height);
        mTextureId = mFullScreen.createTextureObject();
        // Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
        // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
        // available messages will arrive on the main thread.
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        // Tell the UI thread to enable the camera preview.
        mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                MediaCameraCapture.CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onDrawFrame(GL10 unused) {
        LogUtils.d(TAG, "onDrawFrame tex=" + mTextureId);

        // Latch the latest frame.  If there isn't anything new, we'll just re-use whatever
        // was there before.
        if(null != mSurfaceTexture){
            mSurfaceTexture.updateTexImage();
        }

        // If the recording state is changing, take care of it here.  Ideally we wouldn't
        // be doing all this in onDrawFrame(), but the EGLContext sharing with GLSurfaceView
        // makes it hard to do elsewhere.
        if (mRecordingEnabled) {
            switch (mRecordingStatus) {
                case RECORDING_OFF:
                    LogUtils.d(TAG, "START recording");
                    // start recording
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        mVideoEncoder.startRecording(new TextureMovieEncoder.EncoderConfig(
                                mWidth, mHeight, mBitrate, mFrameRate, mIFrameInterval, EGL14.eglGetCurrentContext()));
                    }
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_RESUMED:
                    LogUtils.d(TAG, "RESUME recording");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                    }
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_ON:
                    // yay
                    break;
                case RECORDING_RESTART:
                    LogUtils.d(TAG, "RESTART recording");
                    mVideoEncoder.restartRecording(new TextureMovieEncoder.EncoderConfig(
                            mWidth, mHeight, mBitrate, mFrameRate, mIFrameInterval, EGL14.eglGetCurrentContext()));
                    mRecordingStatus = RECORDING_ON;
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        } else {
            switch (mRecordingStatus) {
                case RECORDING_ON:
                case RECORDING_RESUMED:
                    // reset recording
                    LogUtils.d(TAG, "STOP recording");
                    mVideoEncoder.stopRecording();
                    mRecordingStatus = RECORDING_OFF;
                    break;
                case RECORDING_OFF:
                    // yay
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        }

        // Set the video encoder's texture name.  We only need to do this once, but in the
        // current implementation it has to happen after the video encoder is started, so
        // we just do it here.
        if(mRecordingStatus == RECORDING_ON){
            // TODO: be less lame.
            mVideoEncoder.setTextureId(mTextureId);

            // Tell the video encoder thread that a new frame is available.
            // This will be ignored if we're not actually recording.
            mVideoEncoder.frameAvailable(mSurfaceTexture);
        }


        if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
            // Texture size isn't set yet.  This is only used for the filters, but to be
            // safe we can just skip drawing while we wait for the various races to resolve.
            // (This seems to happen if you toggle the screen off/on with power button.)
            LogUtils.i(TAG, "Drawing before incoming texture size set; skipping");
            return;
        }

        if(null != mSurfaceTexture){
            // Draw the video frame.
            mSurfaceTexture.getTransformMatrix(mSTMatrix);
            mFullScreen.drawFrame(mTextureId, mSTMatrix);
        }
    }
}
