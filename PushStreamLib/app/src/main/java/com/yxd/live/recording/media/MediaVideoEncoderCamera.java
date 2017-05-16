package com.yxd.live.recording.media;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.yxd.live.recording.utils.LogUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;


public class MediaVideoEncoderCamera {
	private static final String TAG = "MediaVideoEncoderCamera";

	// TODO: these ought to be configurable as well
	private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
	private static final int IFRAME_INTERVAL = 2;           // 5 seconds between I-frames

	private Surface mInputSurface;
	private MediaCodec mEncoder;
	private MediaCodec.BufferInfo mBufferInfo;
	private boolean mMuxerStarted;

	private byte[]  mAvcHeader    = null;
	private byte[]  mAvcFrameData = null;

	private byte[] mAvcConfigData = new byte[256];
	private int mAvcConfigDataLen = 0;
	private byte[] mAvcRawData = new byte[1024 * 1024];
	private int mAvcRawDataLen = 0;

	private boolean m_bKeyFrame = false;
	
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
	
	private LinkedBlockingQueue<Long> mCapTimeStampQueue = null;

	/**
	 * Configures encoder and muxer state, and prepares the input Surface.
	 */
	public MediaVideoEncoderCamera(int width, int height, int bitRate, int frameRate, int iFrameInterval, LinkedBlockingQueue<Long> capTimesStampQueue)
			throws IOException {
		mCapTimeStampQueue = capTimesStampQueue;
		
		init(width, height, bitRate, frameRate, iFrameInterval);
		
		mAvcHeader    = null;
		mAvcFrameData = null;

		m_bKeyFrame = false;
		
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
		}else {
			LogUtils.e(TAG, "m_bBitrateChangeFlag = true;");
			m_bResolutionChangeFlag = false;
		}
	}

	public void init(int width, int height, int bitRate, int frameRate, int iFrameInterval){
		LogUtils.i(TAG, "Enter init()!");
		if (width < 0 || width > 1920) {
			throw new IllegalArgumentException("[MediaVideoCapture] param(width) invalid");
		}

		if (height < 0 || height > 1920) {
			throw new IllegalArgumentException("[MediaVideoCapture] param(height) invalid");
		}

		if (iFrameInterval <= 0) {
			iFrameInterval = 2;
		}

		mBufferInfo = new MediaCodec.BufferInfo();

		MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);

		// Set some properties.  Failing to specify some of these can cause the MediaCodec
		// configure() call to throw an unhelpful exception.
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
				MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
		format.setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate);
		format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
		LogUtils.d(TAG, "format: " + format);

		// Create a MediaCodec encoder, and configure it with our format.  Get a Surface
		// we can use for input and wrap it with a class that handles the EGL work.
		try {
			mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
		} catch (IOException e) {
			e.printStackTrace();
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
	
	public void setResolutionChangeFlag(boolean bResolutionChangeFlag)
	{
		LogUtils.i(TAG, "[setBitrateChangeFlag] Enter setBitrateChangeFlag()");
		m_bResolutionChangeFlag = bResolutionChangeFlag;
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

	/**
	 * Extracts all pending data from the encoder and forwards it to the muxer.
	 * <p>
	 * If endOfStream is not set, this returns when there is no more data to drain.  If it
	 * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
	 * Calling this with endOfStream set should be done once, right before stopping the muxer.
	 * <p>
	 * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
	 * not recording audio.
	 */
	public void drainEncoder(boolean endOfStream) {
		LogUtils.setEnabled(false);
		final int TIMEOUT_USEC = 10000;
		LogUtils.d(TAG, "drainEncoder(" + endOfStream + ")");

		if (endOfStream) {
			LogUtils.d(TAG, "sending EOS to encoder");
			mEncoder.signalEndOfInputStream();
			return;
		}

		ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
		while (true) {
			int outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
			if (outputBufferIndex >= 0) {
				ByteBuffer encodedData = encoderOutputBuffers[outputBufferIndex];
				if (encodedData == null) {
					throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex +
							" was null");
				}

				if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
					// The codec config data was pulled out and fed to the muxer when we got
					// the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
					Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG");

					mAvcHeader = new byte[mBufferInfo.size];
					encodedData.get(mAvcHeader, mBufferInfo.offset, mBufferInfo.size);
					LogUtils.i(TAG, "video config data is " + Arrays.toString(mAvcHeader));

					MediaRtmpPublisher.getInstance().addVideoConfigData(mAvcHeader, mBufferInfo.size);

					mBufferInfo.size = 0;
				}

				if (mBufferInfo.size != 0) {
					if (!mMuxerStarted) {
						throw new RuntimeException("muxer hasn't started");
					}
					LogUtils.i(TAG, "Got a video packet!");

					miFrames++;
                    mAbsTimeStampMs = mCapTimeStampQueue.poll();
                    if (mLastTimeStampMs == 0) {
                    	mStartTimeStampMs = mBufferInfo.presentationTimeUs / 1000;
                    	mRelativeTimeStampMs = 0;
					}else {
						mCurrentTimeStampMs = mBufferInfo.presentationTimeUs / 1000;
						if (mCurrentTimeStampMs - mStartTimeStampMs >= 1000) {
							miFramesPerSec = miFrames;
							LogUtils.i(TAG, "encode "+ miFramesPerSec+" frames per sencond");
							mStartTimeStampMs = mCurrentTimeStampMs;
	                    	miFrames = 0;
						}
						mRelativeTimeStampMs = mAbsTimeStampMs - mLastTimeStampMs;	
					}
                    mLastTimeStampMs = mAbsTimeStampMs;
					                    
					mBufferInfo.presentationTimeUs = mAbsTimeStampMs * 1000;
					
					// adjust the ByteBuffer values to match BufferInfo (not needed?)
					encodedData.position(mBufferInfo.offset);
					encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
					if (mAvcFrameData == null) {
						mAvcFrameData = new byte[1024 * 1024];
					}
					encodedData.get(mAvcFrameData, mBufferInfo.offset, mBufferInfo.size);
					parseAvcRawData(mAvcFrameData, mBufferInfo.size);
					int flags = m_bKeyFrame ? 1: 0;

					MediaRtmpPublisher.getInstance().addVideoRawData(mAvcFrameData, mBufferInfo.size, mAbsTimeStampMs);

					LogUtils.i(TAG, "sent " + mBufferInfo.size + " bytes to muxer, absolute ts=" +
							mAbsTimeStampMs + " relativeLayout ts =" + mRelativeTimeStampMs +  " flag is " + flags);
				}

				mEncoder.releaseOutputBuffer(outputBufferIndex, false);

				if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					if (!endOfStream) {
						LogUtils.w(TAG, "reached end of stream unexpectedly");
					} else {
						LogUtils.d(TAG, "end of stream reached");
					}
					break;      // out of while
				}
			}else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
				// no output available yet
				if (!endOfStream) {
					break;      // out of while
				} else {
					LogUtils.d(TAG, "no output available, spinning to await EOS");
				}
			} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
				// not expected for an encoder
				encoderOutputBuffers = mEncoder.getOutputBuffers();
			} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				// should happen before receiving buffers, and should only happen once
				if (mMuxerStarted) {
					throw new RuntimeException("format changed twice");
				}
				MediaFormat newFormat = mEncoder.getOutputFormat();
				LogUtils.d(TAG, "encoder output format changed: " + newFormat);
				mMuxerStarted = true;
			} else if (outputBufferIndex < 0) {
				LogUtils.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
						outputBufferIndex);
				// let's ignore it
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
