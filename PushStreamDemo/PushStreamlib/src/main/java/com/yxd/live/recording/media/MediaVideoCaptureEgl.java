package com.yxd.live.recording.media;

import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.SystemClock;
import android.view.Surface;

import com.yxd.live.recording.gles.EglTask;
import com.yxd.live.recording.gles.FullFrameRect;
import com.yxd.live.recording.gles.Texture2dProgram;
import com.yxd.live.recording.gles.WindowSurface;
import com.yxd.live.recording.utils.LogUtils;

import java.util.concurrent.LinkedBlockingQueue;


public class MediaVideoCaptureEgl<E> {
	private final String TAG = "MediaVideoCaptureEgl";
	
	private MediaProjection mMediaProjection = null;
    private Surface mSurface;
//    private final Handler mHandler;
    private int mWidth = 960;
    private int mHeight = 540;
    private int mFrameRate = 25;
    private int mBitrate = 1000000;
    private int mIFrameInterval = 2;
	private int mDensityDpi = 640;

    private MediaVideoEncoderHWSurfaceEgl mVideoEncoder = null;

    private static MediaVideoCaptureEgl mInstance = null;
    
    private final DrawTask mScreenCaptureTask = new DrawTask(null, 0);
    private final Object mSync = new Object();
	private volatile boolean mIsRecording;

//	private boolean requestDraw;

    private static long mTimestampStartMs = 0;
    private static long mTimestampCurrentFromStart = 0;
    private static long mTimestampDeltaMs = 0;
    private static long mAverageTimestampDeltaMs = 0;
    private static long mTimestampCurrentMs = 0;
    private static long mTimestampLastMs = 0;

	private static boolean m_bResolutionChangeFlag = false;
    
//  private long mImageTimestampStartMs = 0;
//	private long mImageTimestampDeltaMs = 0;
//	private long mImageTimestampCurrentMs = 0;
//	private long mImageTimestampLastMs = 0;
	
	private LinkedBlockingQueue<Long> mCapTimeStampQueue = new LinkedBlockingQueue<Long>();
	
    private MediaVideoCaptureEgl()
	{
		mVideoEncoder = null;
		
		mSurface = null;
		mAverageTimestampDeltaMs = 0;

		if (!m_bResolutionChangeFlag) {
			mTimestampStartMs = 0;
			mTimestampCurrentFromStart = 0;
			mTimestampDeltaMs = 0;
			mTimestampCurrentMs = 0;
			mTimestampLastMs = 0;

//			mImageTimestampStartMs = 0;
//			mImageTimestampDeltaMs = 0;
//			mImageTimestampCurrentMs = 0;
//			mImageTimestampLastMs = 0;
		} else {
			LogUtils.e(TAG, "m_bBitrateChangeFlag = true;");
			m_bResolutionChangeFlag = false;
		}
		
//		final HandlerThread thread = new HandlerThread(TAG);
//		thread.start();
//		mHandler = new Handler(thread.getLooper());
	}

	public static MediaVideoCaptureEgl getInstance()
	{
		if (mInstance == null) {
			mInstance = new MediaVideoCaptureEgl();
		}
		return mInstance;
	}
	
	public static void destroyInstance()
	{
		try{
			if (mInstance != null) {
				mInstance.stop();
				mInstance = null;
			}
		}catch (Exception e){
			e.printStackTrace();
		}
	}
    
	public void init(int width, int height, int bitrate, int frameRate, int iFrameInterval, MediaProjection mediaProjection) {
		LogUtils.i(TAG, "Enter init()!");
		
		mMediaProjection = mediaProjection;
		
		if (width < 0 || width > 1920) {
			throw new IllegalArgumentException("[MediaVideoCapture] param(width) invalid");
		}
		mWidth = width;

		if (height < 0 || height > 1920) {
			throw new IllegalArgumentException("[MediaVideoCapture] param(height) invalid");
		}
		mHeight = height;

		mBitrate = bitrate;

		mFrameRate = frameRate;
		
		mIFrameInterval = iFrameInterval;
		if (mIFrameInterval <= 0) {
			mIFrameInterval = 2;
		}
		
		mVideoEncoder = new MediaVideoEncoderHWSurfaceEgl(mWidth, mHeight, mBitrate, mFrameRate, mIFrameInterval, mCapTimeStampQueue);
		mSurface = mVideoEncoder.getInputSurface();

		LogUtils.i(TAG, "Leave init()!");
	}
	
	public void start()
	{
		LogUtils.i(TAG, "Enter start()!");
		mIsRecording = true;
		new Thread(mScreenCaptureTask, "ScreenCaptureThread").start();
		if (mVideoEncoder != null) {
			mVideoEncoder.start();
		}
		if (mTimestampStartMs == 0) {
			mTimestampStartMs = SystemClock.elapsedRealtimeNanos() / 1000000;
			mTimestampLastMs = mTimestampStartMs;
		}
//		if (mImageTimestampStartMs == 0) {
//			mImageTimestampStartMs = SystemClock.elapsedRealtimeNanos() / 1000000;
//			mImageTimestampLastMs = mImageTimestampStartMs;
//		}
		mAverageTimestampDeltaMs = 1000 / mFrameRate;
		LogUtils.i(TAG, "Leave start()!");
	}
	
	public void pause()
	{
		if (mVideoEncoder != null)
			mVideoEncoder.pause();
	}
	
	public void resume()
	{
		if (mVideoEncoder != null)
			mVideoEncoder.resume();
	}

	public synchronized void setResolutionChangeFlag(boolean bResolutionChangeFlag)
	{
		m_bResolutionChangeFlag = true;
		if (mVideoEncoder != null){
			mVideoEncoder.setResolutionChangeFlag(bResolutionChangeFlag);
		}

	}

	private void stop()
	{
		LogUtils.i(TAG, "Enter reset()!");
		synchronized (mSync) {
			mIsRecording = false;
			mSync.notifyAll();
		}
//		release();

		LogUtils.i(TAG, "reset()! 1++++++++++++");
		
		if (mVideoEncoder != null) {
			mVideoEncoder.stop();
			mVideoEncoder = null;
		}
		
		LogUtils.i(TAG, "Leave reset()!");
	}
	
//	protected void release() {
//		mHandler.getLooper().quit();
//	}
	
	public long getPausedTimestamp() {
		if (mVideoEncoder != null)
			return mVideoEncoder.getPausedTimestamp();
		return 0;
	}
	
	public int getFramesPerSec()
	{
		if (mVideoEncoder != null)
			return mVideoEncoder.getFramesPerSec();
		return 0;
	}
	
	public long getBitratePerSec()
	{
		if (mVideoEncoder != null)
			return mVideoEncoder.getBitratePerSec();
		return 0;
	}

	public void setTimeIntervalMs(long timeIntervalMs)
	{
		mVideoEncoder.setTimeIntervalMs(timeIntervalMs);
	}

	public boolean getIsEncodeSuccess() {
		if (mVideoEncoder != null)
			return mVideoEncoder.getIsEncodeSuccess();
		return false;
	}
	private final class DrawTask extends EglTask {
		private int mTexId;
		private SurfaceTexture mSourceTexture;
		private Surface mSourceSurface;
    	private WindowSurface mEncoderSurface;
    	private FullFrameRect mDrawer;
    	private final float[] mTexMatrix = new float[16];
    	private VirtualDisplay mVirtualDisplay;

    	public DrawTask(final EGLContext shared_context, final int flags) {
    		super(shared_context, flags);
    	}
    	
		@Override
		protected void onStart() {
		    LogUtils.d(TAG, "mScreenCaptureTask: onStart");
			mDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
			mTexId = mDrawer.createTextureObject();
			mSourceTexture = new SurfaceTexture(mTexId);
			mSourceTexture.setDefaultBufferSize(mWidth, mHeight);	
			mSourceSurface = new Surface(mSourceTexture);
			LogUtils.d(TAG, "mScreenCaptureTask mSourceSurface is " + mSourceSurface);
			//mSourceTexture.setOnFrameAvailableListener(mOnFrameAvailableListener, mHandler);
			mEncoderSurface = new WindowSurface(getEglCore(), mSurface);
			mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
					mWidth, mHeight, mDensityDpi, 
					DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, 
					mSourceSurface, null, null);
			queueEvent(mDrawTask);
		}

		@Override
		protected void onStop() {
			LogUtils.d(TAG, "mScreenCaptureTask: Enter onStop");
			if (mDrawer != null) {
				mDrawer.release();
				mDrawer = null;
			}
			if (mSourceSurface != null) {
				mSourceSurface.release();
				mSourceSurface = null;
			}
			if (mSourceTexture != null) {
				mSourceTexture.release();
				mSourceTexture = null;
			}
			if (mEncoderSurface != null) {
				mEncoderSurface.release();
				mEncoderSurface = null;
			}
			if (mVirtualDisplay != null) {
				mVirtualDisplay.release();
				mVirtualDisplay = null;
			}
			makeCurrent();
			LogUtils.d(TAG, "mScreenCaptureTask: Leave onStop");
		}

		@Override
		protected boolean onError(final Exception e) {
			LogUtils.w(TAG, "mScreenCaptureTask: ", e);
			return false;
		}

		@Override
		protected boolean processRequest(final int request, final int arg1, final Object arg2) {
			return false;
		}
		
//		private final OnFrameAvailableListener mOnFrameAvailableListener = new OnFrameAvailableListener() {
//			@Override
//			public void onFrameAvailable(final SurfaceTexture surfaceTexture) {
//				if (mIsRecording) {
//					synchronized (mSync) {
//						mImageTimestampCurrentMs = SystemClock.elapsedRealtimeNanos() / 1000000;
//						mImageTimestampDeltaMs = mImageTimestampCurrentMs - mImageTimestampLastMs;
//						LogUtils.i(TAG, "image delta timestamp is " + mImageTimestampDeltaMs + " ms");
//
//						if (mImageTimestampDeltaMs >= mAverageTimestampDeltaMs) {
//							mImageTimestampLastMs = mImageTimestampCurrentMs;
//							requestDraw = true;
//						}else {
//							LogUtils.i(TAG, "image delta timestamp " + mImageTimestampDeltaMs + " ms is shorter than " + mAverageTimestampDeltaMs + " ms, ignore it");
//						}
//						mSync.notifyAll();
//					}
//				}
//			}
//		};

		private final Runnable mDrawTask = new Runnable() {
			@Override
			public void run() {
				boolean local_request_draw;
//				synchronized (mSync) {
//					local_request_draw = requestDraw;
//				}
				if (mIsRecording) {
					mTimestampCurrentMs = SystemClock.elapsedRealtimeNanos() / 1000000;
					mTimestampDeltaMs = mTimestampCurrentMs - mTimestampLastMs;
					LogUtils.d(TAG, "mTimestampDeltaMs is " + mTimestampDeltaMs);
					if (mTimestampDeltaMs >= mAverageTimestampDeltaMs) {
						mTimestampCurrentFromStart += mTimestampDeltaMs;
						mCapTimeStampQueue.offer(mTimestampCurrentFromStart);
						
						LogUtils.d(TAG, "draw mTimestampDeltaMs is " + mTimestampDeltaMs);
		
						mTimestampLastMs = mTimestampCurrentMs;
//						LogUtils.i(TAG, "local_request_draw is : " + local_request_draw);
//						if (local_request_draw) {
//							LogUtils.d(TAG, "draw mTimestampDeltaMs is " + mTimestampDeltaMs);
							mSourceTexture.updateTexImage();
							mSourceTexture.getTransformMatrix(mTexMatrix);
//						}
						mEncoderSurface.makeCurrent();
						long timeStart = SystemClock.elapsedRealtimeNanos() / 1000000;
						mDrawer.drawFrame(mTexId, mTexMatrix);
						long timeEnd = SystemClock.elapsedRealtimeNanos() / 1000000;
						long deltaTime = timeEnd - timeStart;
						LogUtils.e(TAG, "Draw frame time is " + deltaTime + " ms.");
				    	mEncoderSurface.swapBuffers();
				    	
						makeCurrent();
					}else {
						try {
							Thread.sleep(5);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
					GLES20.glFlush();
					queueEvent(this);
				} else {
					releaseSelf();
				}
			}
		};
	}
}
