package com.yxd.live.recording.media;


import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import com.yxd.live.recording.bean.CallBackObject;
import com.yxd.live.recording.utils.LogUtils;

import java.io.File;


public class MediaAudioCapture implements Runnable{
	private final String TAG = "MediaAudioCapture";

	private int mSampleRate = 8000;
	private int mChannel = AudioFormat.CHANNEL_IN_MONO;
	private int mFormat = AudioFormat.ENCODING_PCM_16BIT;
	private int mBitrate = 32 * 1024;

	private AudioRecord mAudioRecorder          = null;
	private int         mAudioInputBufferSize   = 0;
	
	private boolean     m_bStopRequest = false;
	private static MediaAudioCapture mInstance = null;
	private Thread mAudioCaptureThread = null;
	
	private long mTimestampStartMs = 0;
	private long mTimestampCurrentFromStart = 0;
	private long mTimestampDeltaMs = 0;
	private long mAverageTimestampDeltaMs = 0;
	private long mTimestampCurrentMs = 0;
	private long mTimestampLastMs = 0;

	private long mStartPts = 0;
	private boolean m_bPaused = false;
	
	private byte[] mRawBuffer = null;
    private byte[] mNsOutBuffer = null;
	private byte[] mNsSpFrame = null;
	private byte[] mNsOutFrame = null;
	private byte[] mPcmBuffer = null;

	private CallBackObject<byte[]> callBackAudioData;

	public CallBackObject<byte[]> getCallBackAudioData() {
		return callBackAudioData;
	}

	public void setCallBackAudioData(CallBackObject<byte[]> callBackAudioData) {
		this.callBackAudioData = callBackAudioData;
	}

	private MediaAudioCapture()
	{
		mAudioRecorder = null;
		mAudioInputBufferSize = 0;
		m_bStopRequest = false;
		mAudioCaptureThread = null;
		m_bPaused = false;
		
		mTimestampStartMs = 0;
		mTimestampCurrentFromStart = 0;
		mTimestampDeltaMs = 0;
		mAverageTimestampDeltaMs = 0;
		mTimestampCurrentMs = 0;
		mTimestampLastMs = 0;
		mStartPts = 0;
		
		mRawBuffer = null;
		mNsOutBuffer = null;
		mNsSpFrame = null;
		mNsSpFrame = null;
	}

	public static MediaAudioCapture getInstance()
	{
		if (mInstance == null) {
			mInstance = new MediaAudioCapture();
		}
		return mInstance;
	}

	public static void destroyInstance()
	{
		if (mInstance != null) {
			mInstance.stop();
			mInstance = null;
		}
	}


	public void init(int sampleRate, int channel, int format, int bitrate) {
		if (sampleRate < 8000 || sampleRate > 48000) {
			throw new IllegalArgumentException("sample rate param is invalid!");
		}
		mSampleRate = sampleRate;

		mChannel = channel;
		
		mBitrate = bitrate;

		if (format != AudioFormat.ENCODING_PCM_8BIT && format != AudioFormat.ENCODING_PCM_16BIT) {
			throw new IllegalArgumentException("format param is invalid!");
		}
		mFormat = format;
		// set audio min buffer size.
		mAudioInputBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannel, mFormat);
		
		if (mRawBuffer == null) {
			mRawBuffer = new byte[8 * 1024];
		}
		if (mNsOutBuffer == null) {
			mNsOutBuffer = new byte[8 * 1024];
		}
		if (mNsSpFrame == null) {
			mNsSpFrame = new byte[1024];
		}
		if (mNsOutFrame == null) {
			mNsOutFrame = new byte[2 * 1024];
		}
		int channels = channel == AudioFormat.CHANNEL_IN_MONO ? 1: 2;
		nativeInit(mSampleRate, channels, mBitrate, 16);
	}

	public void start()
	{
		mAudioCaptureThread = new Thread(this);
		mAudioCaptureThread.start();
		if (mTimestampStartMs == 0) {
			mTimestampStartMs = SystemClock.elapsedRealtimeNanos() / 1000000;
			mTimestampLastMs = mTimestampStartMs;
		}
		mAverageTimestampDeltaMs = 1024 * 1000 / mSampleRate;
		LogUtils.i(TAG, "Average timestamp delta is " + mAverageTimestampDeltaMs);
		LogUtils.i(TAG, "Start timestamp delta is " + mTimestampStartMs);
		String NsPcmFilePath = (new StringBuilder((Environment.getExternalStorageDirectory().getAbsolutePath())).append(File.separator).append("ns.pcm")).toString();
		nativeWebRtcNsInit(mSampleRate, NsPcmFilePath);
	}

	public void pause()
	{
		m_bPaused = true;
	}

	public void resume()
	{
		m_bPaused = false;
	}

	private void stop()
	{
		LogUtils.i(TAG, "Enter reset()!");
		m_bStopRequest = true;

		nativeStop();

		if (mAudioCaptureThread != null) {
			try {
				mAudioCaptureThread.join(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		LogUtils.i(TAG, "reset()! 1++++++++++++");

		try {  
			mAudioRecorder.stop();  
			mAudioRecorder.release();  
			mAudioRecorder = null;  
		}catch (IllegalStateException e){  
			LogUtils.e(TAG, "AudioRecord excute reset() error: " + e.toString());
		} 

		nativeWebRtcNsUninit();
		
		LogUtils.i(TAG, "Leave reset()!");
	}
	
	public int getFramesPerSec()
	{
		return nativeGetFramesPerSec();
	}

	public long getBitratePerSec()
	{
		return nativeGetBitratePerSec();
	}

	public void setTimeIntervalMs(long timeIntervalMs)
	{
		nativeSetTimeIntervalMs(timeIntervalMs);
	}

	@Override
	public void run() {
		int audioSource = MediaRecorder.AudioSource.MIC;
		try{
			int bufferSizeInBytes = Math.max(0x10000, mAudioInputBufferSize);
			mAudioRecorder = new AudioRecord(audioSource, mSampleRate, mChannel, mFormat, bufferSizeInBytes);
			mAudioRecorder.startRecording();
		}catch (Exception e){
			LogUtils.e(TAG, "AudioRecord excute startRecording() error: " + e.toString());
		}

		if (nativeStart() != 0) {
			Log.e(TAG, "Native start faac encoder thread failed!");
		}

		byte[] cache = new byte[1024 * 16];
		int pcmSize = 1024 * 2 * (mChannel == AudioFormat.CHANNEL_IN_MONO ? 1 : 2);
		int rest = 0;
		int frameCount = 0;
		
		
		int nsProcessPcmSize = mSampleRate / 1000 * 10 * 2 /** (mChannel == AudioFormat.CHANNEL_IN_MONO ? 1 : 2)*/;
		LogUtils.i(TAG, "webrtc ns process 10ms pcm data each time, size is " + nsProcessPcmSize);
		int startPos = 0;
		int readPos = 0;
		int writePos = 0;
		boolean bFirst = false;
//		String PcmFilePath = (new StringBuilder((Environment.getExternalStorageDirectory().getAbsolutePath())).append(File.separator).append("raw.pcm")).toString();
//		
//		File pcmFile = new File(PcmFilePath);
//	
//		if(!pcmFile.exists()){
//			try {
//				pcmFile.createNewFile();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		FileOutputStream fop = null;
//		try {
//			fop = new FileOutputStream(pcmFile);
//		} catch (FileNotFoundException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}

		
		// TODO Auto-generated method stub
		while(!m_bStopRequest)
		{
			//LogUtils.i(TAG, "run() 1++++++++");
			int readSize = mAudioRecorder.read(mRawBuffer, 0, mAudioInputBufferSize);
			if(callBackAudioData!=null&&readSize>0){
				//Log.e("voice", "run: "+mRawBuffer.length+"   mAudioInputBufferSize:"+mAudioInputBufferSize );
				byte[] cacheAgo=new byte[readSize];
				System.arraycopy(mRawBuffer,0,cacheAgo,0,readSize);
				callBackAudioData.CallBackData(cacheAgo);
			}
			//LogUtils.i(TAG, "run() readSize is " + readSize);
			if (readSize <= 0) {
				if (!m_bStopRequest) {
					LogUtils.i(TAG, "AudioRecord read size error due to forbid permission of RECORD_AUDIO!");
					mTimestampCurrentMs = SystemClock.elapsedRealtimeNanos() / 1000000;
					mTimestampDeltaMs = mTimestampCurrentMs - mTimestampLastMs;
					if (mTimestampDeltaMs >= mAverageTimestampDeltaMs) {
						LogUtils.i(TAG, "delta timestamp is " + mTimestampDeltaMs + " ms");
						mTimestampLastMs = mTimestampCurrentMs;

						if (mPcmBuffer == null) {
							mPcmBuffer = new byte[pcmSize];
						}

						int deltaTimeMs = 1024 * 1000 / mSampleRate;
						mStartPts += deltaTimeMs;

						nativeAddPcmData(mPcmBuffer, mPcmBuffer.length, mStartPts);
//						BufferUnit bufUnit =  new BufferUnit();
//						bufUnit.setData(tmp);
//						bufUnit.setLength(tmp.length);

//						if (mAudioEncoder != null) {
//							mAudioEncoder.setInput(bufUnit);
//							BufferUnit bufUnitEncode = new BufferUnit();
//							bufUnitEncode.setData(new byte[1024 * 64]);
//							mAudioEncoder.getOutput(bufUnitEncode);
//						}
					}
					continue;
				}else {
					LogUtils.i(TAG, "AudioRecord read size error readsize < 0!");
					return;
				}
			}
			if (!m_bStopRequest) {
				
//				System.arraycopy(mRawBuffer, 0, cache, rest, readSize);
				
//				try {
//					fop.write(mRawBuffer);
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
			
//				rest += readSize;
				

				while (readSize - readPos >= nsProcessPcmSize) {
					if (bFirst) {
						System.arraycopy(mRawBuffer, readPos, mNsSpFrame, startPos, nsProcessPcmSize - startPos);
						readPos += nsProcessPcmSize - startPos;
						bFirst = false;
					}else {
						System.arraycopy(mRawBuffer, readPos, mNsSpFrame, 0, nsProcessPcmSize);
						readPos += nsProcessPcmSize;
					}
					
					mNsOutFrame = nativeWebRtcNsProcess(mNsSpFrame);
					System.arraycopy(mNsOutFrame, 0, mNsOutBuffer, writePos, nsProcessPcmSize);
					writePos += nsProcessPcmSize;
				}
				if (readSize - readPos != 0) {
					System.arraycopy(mRawBuffer, readPos, mNsSpFrame, 0, readSize - readPos);
					startPos = readSize - readPos;
					bFirst = true;
				}
				System.arraycopy(mNsOutBuffer, 0, cache, rest, writePos);
				rest += writePos;
				readPos = 0;
				writePos = 0;

				
				frameCount = rest / pcmSize;
				if (frameCount == 0) {
					continue;
				}

				int used = 0;
				while (rest >= pcmSize) {
					LogUtils.i(TAG, "run() 2++++++++");

					mTimestampCurrentMs = SystemClock.elapsedRealtimeNanos() / 1000000;
					mTimestampDeltaMs = mTimestampCurrentMs - mTimestampLastMs;
					mTimestampCurrentFromStart += mTimestampDeltaMs;
					LogUtils.i(TAG, "capture delta time stamp is " + mTimestampDeltaMs + " and capture absolute time time stamp is " + mTimestampCurrentFromStart);
					mTimestampLastMs = mTimestampCurrentMs;
					mPcmBuffer = new byte[pcmSize];
					if (!m_bPaused) {
						System.arraycopy(cache, used, mPcmBuffer, 0, pcmSize);
					}

					String model = Build.MODEL;
					if (model.equals("OPPO R9m")) {
						//OPPO R9m音频采集时间戳不均匀，按照采集的时间计算时间戳
						mStartPts = mTimestampCurrentFromStart;
					} else {
						int deltaTimeMs = 1024 * 1000 / mSampleRate;
						mStartPts += deltaTimeMs;
					}

					nativeAddPcmData(mPcmBuffer, mPcmBuffer.length, mStartPts);
//					BufferUnit bufUnit =  new BufferUnit();
//					bufUnit.setData(tmp);
//					bufUnit.setLength(tmp.length);
//
//					if (mAudioEncoder != null) {
//						mAudioEncoder.setInput(bufUnit);
//						BufferUnit bufUnitEncode = new BufferUnit();
//						bufUnitEncode.setData(new byte[1024 * 64]);
//						mAudioEncoder.getOutput(bufUnitEncode);
//					}

					used += pcmSize;
					rest -= pcmSize;
				}
				if (rest != 0) {
					LogUtils.i(TAG, "run() 3++++++++");
					System.arraycopy(cache, used, cache, 0, rest);
				}
				LogUtils.i(TAG, "run() 4++++++++");
			}

			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		LogUtils.i(TAG, "m_bStopRequest is " + m_bStopRequest);
		
//		try {
//			fop.flush();
//			fop.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}	
		
//		mAudioEncoder.reset();
	}
	
	public native int nativeWebRtcNsInit(int sampleRate, String path);

	public native int nativeWebRtcNsUninit();

	public native byte[] nativeWebRtcNsProcess(byte[] spFrame);

	public native int nativeInit(int sampleRate, int channels, int bitrate, int bitsPerSample);

	public native int nativeAddPcmData(byte[] pcmData, int pcmDataLen, long absTimeStampMs);

	public native int nativeStart();

	public native int nativeStop();

	public native int nativeGetFramesPerSec();

	public native int nativeGetBitratePerSec();

	public native int nativeSetTimeIntervalMs(long timeIntervalMs);
}
