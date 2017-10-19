package com.yxd.live.recording.media;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemClock;

import com.yxd.live.recording.utils.GlobalUtils;
import com.yxd.live.recording.utils.JpegUtils;
import com.yxd.live.recording.utils.LogUtils;
import com.yxd.live.recording.utils.PictureUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class MediaVideoEncoderHWJpeg implements Runnable{
	private final String LOG_TAG = "MediaVideoEncoderJpeg";
	private static MediaVideoEncoderHWJpeg instance = null;
	private Thread mVideoEncoderJpegThread = null;

	private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
	private static final int IFRAME_INTERVAL = 2;           // 5 seconds between I-frames

	private int mWidth = 960;
	private int mHeight = 544;

	private ByteBuffer[] mInputBuffers  = null;
	private ByteBuffer[] mOutputBuffers = null;

	private MediaCodec  mVideoEncoder = null;
	private MediaFormat mOutputFormat = null;

	private byte[] mJpegI420Data = null;
	private byte[] mJpegNV12Data = null;
	private String mJpegFilePath = null;

	private byte[]  mSpsPpsHeadData = null;

	private long mLastTimeStampMs = 0;

	private int mCurrentAPIVersion = Build.VERSION.SDK_INT;

	private static final int kVideoControlRateConstant = 2; // Bitrate mode
	private static final int kCodecBufferDequeueTimeout = 1000000;
	private boolean m_bStopRequest = false;

	private int mColorFormat = 0;

	private long mCurrentTimestampMs = 0;
	private long mDeltaTimestampMs = 0;
	private long mAverageDeltaTimestampMs = 0;
	private long mAbsTimestampMs = 0;


	private MediaVideoEncoderHWJpeg(){
		instance = null;
		mVideoEncoderJpegThread = null;
		mInputBuffers = null;
		mOutputBuffers = null;
		mVideoEncoder = null;

		mJpegI420Data = null;
		mJpegNV12Data = null;
		mJpegFilePath = null;

		mSpsPpsHeadData = null;

		mLastTimeStampMs = 0;

		m_bStopRequest = false;

		mColorFormat = 0;

		mAbsTimestampMs = 0;
		mCurrentTimestampMs = 0;
		mDeltaTimestampMs = 0;
		mAverageDeltaTimestampMs = 0;
	}

	public static MediaVideoEncoderHWJpeg getInstance(){
		if (instance == null) {
			instance = new MediaVideoEncoderHWJpeg();
		}
		return instance;
	}

	public static void destroyInstance()
	{
		if (instance != null) {
			instance.stop();
			instance = null;
		}
	}

	public void init(int width, int height, int bitrate, int frameRate, String jpegFilePath)
	{
		LogUtils.i(LOG_TAG, "[init]: Enter init");

		if (width < 0 || width > 1920) {
			throw new IllegalArgumentException("[MediaVideoCapture] param(width) invalid");
		}
		mWidth = width;

		if (height < 0 || height > 1920) {
			throw new IllegalArgumentException("[MediaVideoCapture] param(height) invalid");
		}
		mHeight = height;

		mJpegFilePath = jpegFilePath;

		mColorFormat = GlobalUtils.getSupportedColorFormat();

		try {
			mVideoEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
		mediaFormat.setInteger("bitrate-mode", kVideoControlRateConstant);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
//		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
		mediaFormat.setInteger("stride", width);
		mediaFormat.setInteger("slice-height", height);
		int requirmentLength = width * height * 3 / 2;
		mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, requirmentLength);
		try{
			mVideoEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		}catch (Exception e){
			e.printStackTrace();
		}

		if (mJpegI420Data == null) {
			mJpegI420Data = new byte [width * height * 3 / 2];
		}
		if (mJpegNV12Data == null) {
			mJpegNV12Data = new byte [width * height * 3 / 2 + 1];
		}

//		int cpuNum = GlobalUtils.getCPUCores();
//		if (cpuNum % 4 != 0) {
//			cpuNum = (cpuNum / 4) * 4;
//		}
//		LogUtils.i(LOG_TAG, "cpu number is " + cpuNum);
//		PictureUtils.nativeInit(cpuNum);
		PictureUtils.nativeInit(1);

		mAverageDeltaTimestampMs = 1000 / frameRate;
	}


	public boolean start()
	{
		LogUtils.i(LOG_TAG, "[start]: Enter start");

		int result = JpegUtils.nativeJpegToI420Scale(mJpegFilePath, mJpegI420Data, mWidth, mHeight);
		if (result < 0) {
			LogUtils.e(LOG_TAG, "Decode Jpeg File to I420 Data error!");
		}
		PictureUtils.nativeI420ToNV12(mJpegI420Data, mWidth, mJpegNV12Data, mWidth, mWidth, mHeight);

		mVideoEncoder.start();

		// if API level <= 20, get input and output buffer arrays here
		// see http://developer.android.com/reference/android/media/MediaCodec.html for more details.
		mInputBuffers   = mVideoEncoder.getInputBuffers();
		mOutputBuffers  = mVideoEncoder.getOutputBuffers();

		mVideoEncoderJpegThread = new Thread(this);
		mVideoEncoderJpegThread.start();

		LogUtils.i(LOG_TAG, "[start]: start video encoder OK");

		return true;
	}

	public boolean stop()
	{
		LogUtils.i(LOG_TAG, "[reset] Enter reset");
		m_bStopRequest = true;

		try {
			mVideoEncoderJpegThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			mVideoEncoder.stop();
		} catch (IllegalStateException e) {
			LogUtils.e(LOG_TAG, "MediaCodec reset IllegalStateException" + e.toString());
		}
		mVideoEncoder.release();

		LogUtils.i(LOG_TAG, "[reset] Leave reset");

		return true;
	}

	@Override
	public void run() {
		while (!m_bStopRequest) {
			if (mLastTimeStampMs == 0) {
				mAbsTimestampMs = MediaVideoCaptureEgl.getInstance().getPausedTimestamp();
				LogUtils.i(LOG_TAG, "private mode start time is " + mAbsTimestampMs);
//				mAbsTimestampMs = MediaVideoEncoderHWSurface.getInstance().getPausedTimestamp();
				mLastTimeStampMs = SystemClock.elapsedRealtimeNanos() / 1000000;
			}
			mCurrentTimestampMs = SystemClock.elapsedRealtimeNanos() / 1000000;
			LogUtils.i(LOG_TAG, "mCurrentTimestampMs is " + mCurrentTimestampMs);
			mDeltaTimestampMs = mCurrentTimestampMs - mLastTimeStampMs;
			if (mDeltaTimestampMs >= mAverageDeltaTimestampMs) {
				LogUtils.i(LOG_TAG, "delta timestamp is " + mDeltaTimestampMs + " ms");
				mLastTimeStampMs = mCurrentTimestampMs;
				LogUtils.i(LOG_TAG, "mLastTimeStampMs is " + mLastTimeStampMs);
				mAbsTimestampMs += mDeltaTimestampMs;

				BufferUnit bufUnit =  new BufferUnit();
				if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
					bufUnit.setData(mJpegI420Data);
				}else if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
					bufUnit.setData(mJpegNV12Data);
				}
				bufUnit.setLength(mWidth * mHeight * 3 / 2);
				bufUnit.setPts(mAbsTimestampMs);
				setInput(bufUnit);
				BufferUnit bufUnitEncode = new BufferUnit();
				bufUnitEncode.setData(new byte[1024 * 1024]);
				getOutput(bufUnitEncode);
			}else {
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		}
	}


	private int setInput(BufferUnit bufUnit)
	{
		LogUtils.i(LOG_TAG, "[setInput]: Enter setInput()\n");
		if (!m_bStopRequest) {
			int inputBufferIndex = -1;
			try {
				inputBufferIndex = mVideoEncoder.dequeueInputBuffer(kCodecBufferDequeueTimeout);
			} catch (IllegalStateException exception) {
				LogUtils.e(LOG_TAG, "dequeueInputBuffer " + exception.toString());
			}
			if (inputBufferIndex >= 0) {

				LogUtils.d(LOG_TAG, "[setInput] dequeueInputBuffer return inputBufferIndex = " + inputBufferIndex);

				// see http://developer.android.com/reference/android/media/MediaCodec.html for more details.
				ByteBuffer inputBuffer = mInputBuffers[inputBufferIndex];
				inputBuffer.clear();
				inputBuffer.put(bufUnit.getData(), 0, bufUnit.getLength());

				LogUtils.d(LOG_TAG, "[setInput]: get video raw sample pts = " + bufUnit.getPts() + " inputBuffer position = " + inputBuffer.position() + " capacity = " + inputBuffer.capacity());

				// pts is in us(input pts is in ms, so we need to convert.)
				try {
					mVideoEncoder.queueInputBuffer(inputBufferIndex, 0, bufUnit.getLength(), bufUnit.getPts()*1000, 0);
				} catch (IllegalStateException exception) {
					LogUtils.e(LOG_TAG, "queueInputBuffer " + exception.toString());
				}

			} else {
				LogUtils.d(LOG_TAG, "[setInput] MediaCodec.dequeueInputBuffer inputBufferIndex =" + inputBufferIndex);
				return -1;
			}

			return bufUnit.getLength();
		}
		return 0;
	}

	private int getOutput(BufferUnit bufUnit)
	{
		LogUtils.i(LOG_TAG, "[getOutput]: Enter getOutput()");
		if (!m_bStopRequest) {
			MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
			int outputBufferIndex = -1;
			try {
				outputBufferIndex = mVideoEncoder.dequeueOutputBuffer(bufferInfo, kCodecBufferDequeueTimeout);
			} catch (IllegalStateException exception) {
				LogUtils.e(LOG_TAG, "dequeueOutputBuffer " + exception.toString());
			}
			if (outputBufferIndex >= 0) {
				LogUtils.d(LOG_TAG, "[getOutput]: MediaCodec.dequeueOutputBuffer() OK" +
						" offset =" +  bufferInfo.offset + " size = " + bufferInfo.size + " pts =" + (bufferInfo.presentationTimeUs/1000) );

				ByteBuffer outputBuffer = null;
				// see http://developer.android.com/reference/android/media/MediaCodec.html for more details.
				outputBuffer = mOutputBuffers[outputBufferIndex];

				outputBuffer.position(bufferInfo.offset);
				outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

				if(bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {

					LogUtils.v(LOG_TAG, "[getOutput]: bufferInfo.flags: MediaCodec.BUFFER_FLAG_CODEC_CONFIG");

					mSpsPpsHeadData = new byte[bufferInfo.size];
					outputBuffer.get(mSpsPpsHeadData, 0, bufferInfo.size);
					outputBuffer.position(bufferInfo.offset);
					outputBuffer.clear();
					outputBuffer.get(mSpsPpsHeadData, bufferInfo.offset, bufferInfo.size);
					LogUtils.i(LOG_TAG, "video config data is " + Arrays.toString(mSpsPpsHeadData));

//					MediaRtmpPublisher.getInstance().addVideoConfigData(mSpsPpsHeadData, bufferInfo.size);

					try {
						mVideoEncoder.releaseOutputBuffer(outputBufferIndex, false);
					} catch (IllegalStateException exception) {
						LogUtils.e(LOG_TAG, "releaseOutputBuffer " + exception.toString());
					}

					return 0;
				} else {

					LogUtils.v(LOG_TAG, "[getOutput]: get an encoded video data");


					// write video encoded buffer to given output.
					byte[] outData = bufUnit.getData();
					outputBuffer.get(outData, 0, bufferInfo.size);
					outputBuffer.position(bufferInfo.offset);
					outputBuffer.clear();

					// write buffer attributes using property in current mediacodec BufferInfo
					bufUnit.setLength(bufferInfo.size);
					bufUnit.setPts((bufferInfo.presentationTimeUs/1000));
					bufUnit.setFlags(bufferInfo.flags);

					long absTimeStampMs = bufferInfo.presentationTimeUs / 1000;

					LogUtils.i(LOG_TAG, "sent " + bufferInfo.size + " bytes to muxer, ts=" +
							absTimeStampMs);

					MediaRtmpPublisher.getInstance().addVideoRawData(outData, bufferInfo.size, absTimeStampMs);

					try {
						mVideoEncoder.releaseOutputBuffer(outputBufferIndex, false);
					} catch (IllegalStateException exception) {
						LogUtils.e(LOG_TAG, "releaseOutputBuffer " + exception.toString());
					}


					return bufferInfo.size;
				}

			} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

				LogUtils.d(LOG_TAG, "[getOutput]: MediaCodec.dequeueOutputBuffer() INFO_OUTPUT_BUFFERS_CHANGED\n");

				mOutputBuffers = mVideoEncoder.getOutputBuffers();

				return -1;
			} else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

				LogUtils.d(LOG_TAG, "[getOutput] MediaCodec.dequeueOutputBuffer() INFO_OUTPUT_FORMAT_CHANGED New format"
						+ mVideoEncoder.getOutputFormat());

				mOutputFormat = mVideoEncoder.getOutputFormat();

//				MediaFFmpegWriter.getInstance().addVideoTrack(mOutputFormat);

				return 0;
			} else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {

				LogUtils.d(LOG_TAG, "[getOutput] MediaCodec.dequeueOutputBuffer() INFO_TRY_AGAIN_LATER\n");

				return -1;
			} else {

				// this should not happen.
				LogUtils.d(LOG_TAG, "[getOutput] MediaCodec.dequeueOutputBuffer() ret = " + outputBufferIndex);

				throw new IllegalStateException("[getOutput] MediaCodec.dequeueOutputBuffer has encountered an error!");
			}
		}
		return 0;
	}
}
