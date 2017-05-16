package com.yxd.live.recording.media;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import android.widget.Toast;

import com.yxd.live.recording.camera.gles.FullFrameRect;
import com.yxd.live.recording.utils.CameraUtils;
import com.yxd.live.recording.utils.LogUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class MediaVideoCaptureCamera implements OnFrameAvailableListener {
	public final String TAG = "MediaVideoCaptureCamera";
	
	private Activity mActivity = null;
	private GLSurfaceView mGLView = null;
	private Camera mCamera = null;
	private CameraHandler mCameraHandler = null;
	private CameraSurfaceRenderer mRenderer = null;
	private static MediaVideoCaptureCamera mInstance = null;
	private int mCameraPreviewWidth = -1;
	private int mCameraPreviewHeight = -1;
	private int mCameraNum;
	private int mCameraId = 1;
	private int mDisplayOrientationDegrees = 0;
	private static TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();
	

	private MediaVideoCaptureCamera()
	{
		mCameraHandler = new CameraHandler(this);
	}
	public static MediaVideoCaptureCamera getInstance(){
		if (mInstance == null) {
			mInstance = new MediaVideoCaptureCamera();
		}
		return mInstance;
	}

	public static void destroyInstance(){
		if (mInstance != null) {
			mInstance.stop();
		}
		mInstance = null;
	}


	public void init(Activity activity, GLSurfaceView glview, int width, int height, int bitrate, int frameRate, int iFrameInterval) {
		mActivity = activity;
		mGLView = glview;
		mGLView.setEGLContextClientVersion(2);     // select GLES 2.0
		mRenderer = new CameraSurfaceRenderer(mCameraHandler, sVideoEncoder, width, height, bitrate, frameRate, iFrameInterval);
		mGLView.setRenderer(mRenderer);
		mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        this.mCameraPreviewWidth = width;
        this.mCameraPreviewHeight = height;
		openCamera(mCameraPreviewWidth, mCameraPreviewHeight);
	}

	public void start()
	{
		ChangeCaptureState(true);
	}

	public void stop(){
        closeFilter();
		mCameraHandler.invalidateHandler();
        mRenderer.stopRecording();
		releaseCamera();
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
	
	public void changeFlashLightState(boolean isFlashOn){
		Camera.Parameters parms = mCamera.getParameters();
		List<String> flashModes = parms.getSupportedFlashModes();
		if (flashModes != null) {
			if (isFlashOn) {
				//开启闪光灯
				isFlashOn = true;
				if (flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
					parms.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);    
					mCamera.setParameters(parms);
				}
			}
			else {
				//关闭闪光灯
				isFlashOn = false;
				if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
					parms.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);    
					mCamera.setParameters(parms);
				}
			}
		}
	}

	public void changeCameraFacing(){
		if (mCameraNum > 1) {
			mCameraId = (mCameraId + 1) % 2;
			releaseCamera();
			openCamera(mCameraPreviewWidth, mCameraPreviewHeight);
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
		if(null == mGLView){
			return;
		}
		mGLView.onResume();
		mGLView.queueEvent(new Runnable() {
			@Override public void run() {
				mRenderer.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
			}
		});
	}

    /**
	 * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
	 */
	private void handleSetSurfaceTexture(SurfaceTexture st) {
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
	 * Handles camera operation requests from other threads.  Necessary because the Camera
	 * must only be accessed from one thread.
	 * <p>
	 * The object is created on the UI thread, and all handlers run there.  Messages are
	 * sent from other threads, using sendMessage().
	 */
	static class CameraHandler extends Handler {
		public static final int MSG_SET_SURFACE_TEXTURE = 0;

		// Weak reference to the Activity; only access this from the UI thread.
		private WeakReference<MediaVideoCaptureCamera> mWeakCapture;

		public CameraHandler(MediaVideoCaptureCamera capture) {
			mWeakCapture = new WeakReference<MediaVideoCaptureCamera>(capture);
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
			MediaVideoCaptureCamera capture = mWeakCapture.get();
			if (capture == null) {

				return;
			}

			switch (what) {
			case MSG_SET_SURFACE_TEXTURE:
				capture.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj);
				break;
			default:
				throw new RuntimeException("unknown msg " + what);
			}
		}
	}

	@Override
	public void onFrameAvailable(SurfaceTexture arg0) {
		// TODO Auto-generated method stub
		mGLView.requestRender();
	}


	/**
	 * Opens a camera, and attempts to establish preview mode at the specified width and height.
	 * <p>
	 * Sets mCameraPreviewWidth and mCameraPreviewHeight to the actual width/height of the preview.
	 */
	private void openCamera(int desiredWidth, int desiredHeight) {
		if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
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
        setFocusMode(parms);
		// leave the frame rate set to default
		mCamera.setParameters(parms);

		Camera.Size mCameraPreviewSize = parms.getPreviewSize();
		mCameraPreviewWidth = mCameraPreviewSize.width;
		mCameraPreviewHeight = mCameraPreviewSize.height;
	}

    private void setFocusMode(Camera.Parameters parms){
        List<String> focusModes = parms.getSupportedFocusModes();
        if(null != focusModes && focusModes.size() > 0){
            for(String mode:focusModes){
                if(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO.equals(mode)){
                    parms.setFocusMode(mode);
                    return;
                }
            }
        }
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

    public void closeFilter(){
        if(null != mRenderer){
             mRenderer.closeFilter();
        }
    }
}

class CameraSurfaceRenderer implements GLSurfaceView.Renderer{
	private static final String TAG = "CameraSurfaceRenderer";

	private static final int RECORDING_OFF = 0;
	private static final int RECORDING_ON = 1;
	private static final int RECORDING_RESUMED = 2;
	private static final int RECORDING_RESTART = 3;

	private MediaVideoCaptureCamera.CameraHandler mCameraHandler;
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

    public void closeFilter(){
        if(null != mFullScreen){
            mFullScreen.closeFilter();
        }

        if(null != mVideoEncoder){
            mVideoEncoder.closeFilter();
        }
    }

    public void stopRecording(){
        mRecordingStatus = RECORDING_OFF;
        mVideoEncoder.stopRecording();
        notifyPausing();
    }

	/**
	 * Constructs CameraSurfaceRenderer.
	 * <p>
	 * @param cameraHandler Handler for communicating with UI thread
	 * @param movieEncoder video encoder object
	 * @param
	 */
	public CameraSurfaceRenderer(MediaVideoCaptureCamera.CameraHandler cameraHandler,
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
				MediaVideoCaptureCamera.CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
	}

	/**
	 * Records the size of the incoming camera preview frames.
	 * <p>
	 * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
	 * so we assume it could go either way.  (Fortunately they both run on the same thread,
	 * so we at least know that they won't execute concurrently.)
	 */
	public void setCameraPreviewSize(int width, int height) {
		LogUtils.d(TAG, "setCameraPreviewSize");
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

		mTextureId = FullFrameRect.createTextureObject();
        mSurfaceTexture = new SurfaceTexture(mTextureId);
	}

	@Override
	public void onSurfaceChanged(GL10 unused, int width, int height) {
		LogUtils.d(TAG, "onSurfaceChanged " + width + "x" + height);
		// Set up the texture blitter that will be used for on-screen display.  This
		// is *not* applied to the recording, because that uses a separate shader.

        if(null == mFullScreen){
            mFullScreen = new FullFrameRect(width, height);
            // Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
            // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
            // available messages will arrive on the main thread.
            // Tell the UI thread to enable the camera preview.
            mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                    MediaVideoCaptureCamera.CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
        }
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
		if(mRecordingStatus == RECORDING_ON && mSurfaceTexture != null){
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
