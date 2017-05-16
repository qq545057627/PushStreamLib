package com.yxd.live.recording.media;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import com.yxd.live.recording.interfaces.IMediaRecorder;
import com.yxd.live.recording.utils.LogUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;


public class MediaVideoEncoderHWSurfaceEgl implements Runnable{
	private static final String TAG = "MediaVideoEncoderHWSurfaceEgl";

	// TODO: these ought to be configurable as well
	private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding             // fps
//	private static final int IFRAME_INTERVAL = 2;           // 2 seconds between I-frames

	private Surface mInputSurface;
	private MediaCodec mEncoder = null;
	private MediaCodec.BufferInfo mBufferInfo;
	private boolean mMuxerStarted;

	private byte[]  mAvcHeader    = null;
	private byte[]  mAvcFrameData = null;

	private boolean m_bKeyFrame = false;
	private boolean m_bStopRequest = false;
	private Thread mVideoEncoderHWThread = null;

	private static long mStartTimeStampMs = 0;
	private static long mLastTimeStampMs = 0;
	private static long mCurrentTimeStampMs = 0;
	private static long mRelativeTimeStampMs = 0;
	private static long mAbsTimeStampMs = 0;
	private static long mPausedTimeStampMs = 0;

	private static boolean mbPaused = false;
	private static boolean mbFirstPaused = false;
	private static boolean mbFirstResumed = false;
	
	private static boolean m_bResolutionChangeFlag = false;
	private static int miFrames = 0;
	private static int miFramesPerSec = 0;
	private static long miBitrate = 0;
	private static long miBitratePerSec = 0;

	private static boolean mbEncodeSuccess = false;
	
	private LinkedBlockingQueue<Long> mCapTimeStampQueue = null;
    private long mTimeIntervalMs = 6000;


	public MediaVideoEncoderHWSurfaceEgl(int width, int height, int bitRate, int frameRate, int iFrameInterval,
										 LinkedBlockingQueue<Long> capTimesStampQueue) {
		mCapTimeStampQueue = capTimesStampQueue;
		init(width, height, bitRate, frameRate, iFrameInterval);

		mAvcHeader    = null;
		mAvcFrameData = null;

		m_bKeyFrame = false;
		m_bStopRequest = false;
		mVideoEncoderHWThread = null;

		if (!m_bResolutionChangeFlag) {
			mStartTimeStampMs = 0;
			mLastTimeStampMs = 0;
			mCurrentTimeStampMs = 0;
			mRelativeTimeStampMs = 0;
			mAbsTimeStampMs = 0;

			mbPaused = false;
			mbFirstPaused = false;
			mbFirstResumed = false;
			miFrames = 0;
			miFramesPerSec = 0;
			miBitrate = 0;
			miBitratePerSec = 0;
			mbEncodeSuccess = false;
		}else {
			LogUtils.e(TAG, "m_bBitrateChangeFlag = true;");
			m_bResolutionChangeFlag = false;
		}
		LogUtils.e(TAG, "mStartTimeStampMs = " + mStartTimeStampMs + " mLastTimeStampMs=" + mLastTimeStampMs
				+ " mRelativeTimeStampMs=" + mRelativeTimeStampMs  + " mAbsTimeStampMs=" + mAbsTimeStampMs);
	}

	public void init(int width, int height, int bitRate, int frameRate, int iFrameInterval){
		LogUtils.i(TAG, "Enter init()!");
		if (width < 0 || width > 1920) {
			throw new IllegalArgumentException("[MediaVideoCapture] param(width) invalid");
		}

		if (height < 0 || height > 1920) {
			throw new IllegalArgumentException("[MediaVideoCapture] param(height) invalid");
		}

		mBufferInfo = new MediaCodec.BufferInfo();

		MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);

		// Set some properties.  Failing to specify some of these can cause the MediaCodec
		// configure() call to throw an unhelpful exception.
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
				MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
//		format.setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate);
//		format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
		LogUtils.d(TAG, "format: " + format);

		// Create a MediaCodec encoder, and configure it with our format.  Get a Surface
		// we can use for input and wrap it with a class that handles the EGL work.
		try {
			mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
		} catch (IOException e) {
			LrMediaRecorder.getInstance().postEvent(LrMediaRecorder.MEDIA_ERROR, IMediaRecorder.MEDIA_ERROR_CREATE_VIDEO_ENCODER_FAILED, 0);
			return;
		}
		mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mInputSurface = mEncoder.createInputSurface();
		mEncoder.start();
		mMuxerStarted = false;
	}

	/**
	 * Returns the encoder's input surface.
	 */
	public Surface getInputSurface() {
		return mInputSurface;
	}

	public void start(){
		mVideoEncoderHWThread = new Thread(this);
		mVideoEncoderHWThread.start();
	}
	
	public void pause(){
		mbPaused = true;
		mbFirstPaused = true;
	}
	
	public void resume(){
		mbPaused = false;
		mbFirstResumed = true;
	}

	public void stop(){
		LogUtils.d(TAG, "Enter reset()");
		m_bStopRequest = true;
		mEncoder.signalEndOfInputStream();
		try {
			mVideoEncoderHWThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		release();
		mbEncodeSuccess = false;
		LogUtils.d(TAG, "Leave reset()");
	}

	/**
	 * Releases encoder resources.
	 */
	public void release() {
		LogUtils.d(TAG, "releasing encoder objects");
		if (mEncoder != null) {
			try {
				mEncoder.stop();
				mEncoder.release();
				mEncoder = null;
			} catch (IllegalStateException e) {
				LogUtils.e(TAG, "MediaCodec reset() " + e.toString());
			}
		}
	}

	public long getPausedTimestamp() {
		return mPausedTimeStampMs;
	}

	public void setResolutionChangeFlag(boolean bResolutionChangeFlag)
	{
		LogUtils.i(TAG, "[setBitrateChangeFlag] Enter setBitrateChangeFlag()");
		m_bResolutionChangeFlag = bResolutionChangeFlag;
	}
	
	public int getFramesPerSec()
	{
		return miFramesPerSec;
	}
	
	public long getBitratePerSec()
	{
		return miBitratePerSec;
	}

	public void setTimeIntervalMs(long timeIntervalMs)
	{
		mTimeIntervalMs = timeIntervalMs;
	}

	public boolean getIsEncodeSuccess() {
		return mbEncodeSuccess;
	}

	@Override
	public void run() {
		final int TIMEOUT_USEC = 10000;
//		mPresentationTimeStartMs = (long)(System.nanoTime()/1000000); 
		ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
		while (!m_bStopRequest) {
			LogUtils.i(TAG, "--------in while loop-------");
			int encoderStatus = 0;
			try {
				encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
			} catch (IllegalStateException e) {
				e.printStackTrace();
			}
			if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
				LogUtils.d(TAG, "[getOutput] MediaCodec.dequeueOutputBuffer() INFO_TRY_AGAIN_LATER\n");
				continue;
			}else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				// not expected for an encoder
				encoderOutputBuffers = mEncoder.getOutputBuffers();
			}else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				// should happen before receiving buffers, and should only happen once
				if (mMuxerStarted) {
					throw new RuntimeException("format changed twice");
				}
				MediaFormat newFormat = mEncoder.getOutputFormat();
				LogUtils.d(TAG, "encoder output format changed: " + newFormat);

				// now that we have the Magic Goodies, start the muxer
//				mTrackIndex = mMuxer.addTrack(newFormat);
//				mMuxer.start();
				LogUtils.e(TAG, "Meida Muxer started\n" );
//				MediaFFmpegWriter.getInstance().addVideoTrack(newFormat);
//				MediaMp4Muxer.getInstance().addVideoTracker(newFormat);
				mMuxerStarted = true;
			}else if (encoderStatus < 0) {
				LogUtils.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
						encoderStatus);
				// let's ignore it
			}else {
				ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
				if (encodedData == null) {
					throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
							" was null");
				}

				if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					// The codec config data was pulled out and fed to the muxer when we got
					// the INFO_OUTPUT_FORMAT_CHANGED status.  

					mAvcHeader = new byte[mBufferInfo.size];
					try {
						encodedData.get(mAvcHeader, mBufferInfo.offset, mBufferInfo.size);
						LogUtils.i(TAG, "video config data is " + Arrays.toString(mAvcHeader));
					}catch (Exception e){
						e.printStackTrace();
					}

					MediaRtmpPublisher.getInstance().addVideoConfigData(mAvcHeader, mBufferInfo.size);
					
					mBufferInfo.size = 0;

					mbEncodeSuccess = true;
				}

				if (mBufferInfo.size != 0) {
					
					if (!mMuxerStarted) {
						throw new RuntimeException("muxer hasn't started");
					}
					
					miFrames++;
					miBitrate += mBufferInfo.size / 1024 * 8;
					if(mCapTimeStampQueue!=null&&!mCapTimeStampQueue.isEmpty()){
						mAbsTimeStampMs = mCapTimeStampQueue.poll();
					}
                    if (mLastTimeStampMs == 0) {
                    	mStartTimeStampMs = mBufferInfo.presentationTimeUs / 1000;
                    	mRelativeTimeStampMs = 0;
					}else {
						mCurrentTimeStampMs = mBufferInfo.presentationTimeUs / 1000;
						if (mCurrentTimeStampMs - mStartTimeStampMs >= mTimeIntervalMs) {
							int deltaTimeStamp = (int)((mCurrentTimeStampMs - mStartTimeStampMs) / 1000);
							miFramesPerSec = miFrames / deltaTimeStamp;
							miBitratePerSec = miBitrate / deltaTimeStamp;
							LogUtils.i(TAG, "Delta time is " + deltaTimeStamp + " Framerate is " + miFramesPerSec + " Bitrate is " + miBitratePerSec + " kbps");
							mStartTimeStampMs = mCurrentTimeStampMs;
	                    	miFrames = 0;
	                    	miBitrate = 0;
						}
						mRelativeTimeStampMs = mAbsTimeStampMs - mLastTimeStampMs;	
					}
                    mLastTimeStampMs = mAbsTimeStampMs;
					                    
					mBufferInfo.presentationTimeUs = mAbsTimeStampMs * 1000;
							
					if (!mbPaused) {
						// adjust the ByteBuffer values to match BufferInfo (not needed?)
						encodedData.position(mBufferInfo.offset);
						encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
						if (mAvcFrameData == null) {
							mAvcFrameData = new byte[1024 * 1024];
						}
						try{
							encodedData.get(mAvcFrameData, mBufferInfo.offset, mBufferInfo.size);
							parseAvcRawData(mAvcFrameData, mBufferInfo.size);
						}catch (Exception e){
							e.printStackTrace();
						}
						int flags = m_bKeyFrame ? 1: 0;

						//drop non key frame after close private mode
						if (mbFirstResumed) {
							if (flags == 0) {
								try {
									mEncoder.releaseOutputBuffer(encoderStatus, false);
								} catch (IllegalStateException exception) {
									LogUtils.e(TAG, "releaseOutputBuffer " + exception.toString());
								}
								continue;
							}else if (flags == 1) {
								mbFirstResumed = false;
							}
						}
						MediaRtmpPublisher.getInstance().addVideoRawData(mAvcFrameData, mBufferInfo.size, mAbsTimeStampMs);

						LogUtils.i(TAG, "sent " + mBufferInfo.size + " bytes to muxer, absolute ts=" +
								mAbsTimeStampMs + " relativeLayout ts =" + mRelativeTimeStampMs +  " flag is " + flags);
					}else {
						if (mbFirstPaused) {
							mPausedTimeStampMs = mAbsTimeStampMs;
							LogUtils.e(TAG, "Paused timestamp is " + mPausedTimeStampMs + " ms");
							mbFirstPaused = false;
						}
						LogUtils.i(TAG, "private mode is open!");
					}			
				}

				try {
					mEncoder.releaseOutputBuffer(encoderStatus, false);
				} catch (IllegalStateException exception) {
					LogUtils.e(TAG, "releaseOutputBuffer " + exception.toString());
				}
				
			}

		}
	}
	
	private void parseAvcRawData(byte[] avcRawData, int avcRawDataLen)
	{
		if (avcRawData == null || avcRawDataLen <= 0) {
			return;
		}

		int iPos = 0;

		if (avcRawData[iPos] == 0x00 && avcRawData[iPos + 1] == 0x00 
				&& avcRawData[iPos + 2] == 0x01) {
			iPos += 3;
		}
		else if (avcRawData[iPos] == 0x00 && avcRawData[iPos + 1] == 0x00
				&& avcRawData[iPos + 2] == 0x00 && avcRawData[iPos + 3] == 0x01) {
			iPos += 4;
		}
		int iType = avcRawData[iPos] & 0x1f;
		if (iType == 0x05) {
			m_bKeyFrame = true;
		}else {
			m_bKeyFrame = false;
		}
	}
}
