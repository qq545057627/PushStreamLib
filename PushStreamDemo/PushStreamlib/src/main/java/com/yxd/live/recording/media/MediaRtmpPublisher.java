package com.yxd.live.recording.media;



import com.yxd.live.recording.utils.LogUtils;


public class MediaRtmpPublisher {

	private static final String TAG = "MediaRtmpPublisher";

	private boolean mbAudioAdded = false;
	private boolean mbVideoAdded = false;
	private boolean mPublisherStarted = false;

	private static MediaRtmpPublisher mInstance = null;
	private long mTimeIntervalMs = 6000;

	public static MediaRtmpPublisher getInstance()
	{
		if (mInstance == null) {
			mInstance = new MediaRtmpPublisher();
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
	

	private MediaRtmpPublisher()
	{
		mbAudioAdded = false;
		mbVideoAdded = false;
		mPublisherStarted = false;
	}

	public int init(String rtmp_url, int frameRate, int width, int height, int bitRate, int samplerate, int channels)
	{
		int result = nativeInit(rtmp_url, frameRate, width, height, bitRate, samplerate, channels);
		if (result != 0) {
			LogUtils.e(TAG, "[init] failed to init rtmp publisher");
		}
		return result;
	}
	
	public int reconnect()
	{
		int result = nativeReconnect();
		if (result != 0) {
			LogUtils.e(TAG, "[reconnect] failed to reconnect rtmp server");
		}
		return result;
	}

	private void stop()
	{
		if (nativeStop() != 0) {
			LogUtils.e(TAG, "[Release] failed to release rtmp publisher");
		}
	}

	public void addVideoConfigData(byte[] videoConfig, int videoConfigLen)
	{		
		if (nativeAddVideoConfigData(videoConfig, videoConfigLen) != 0) {
			LogUtils.e(TAG, "[addVideoConfigData] failed to add video config data");
		}else {
			LogUtils.i(TAG, "[addVideoConfigData] add video config data success!");
			mbVideoAdded = true;
		}

//		if (mbVideoAdded && mbAudioAdded && !mPublisherStarted) {
//			if (nativeStart() == 0) {
//				mPublisherStarted = true;
//			}
//		}
		if (mbVideoAdded && !mPublisherStarted) {
			if (nativeStart() == 0) {
				mPublisherStarted = true;
			}
		}
	}

	public void addAudioConfigData(byte[] audioConfig, int audioConfigLen)
	{
		if (!mbAudioAdded) {
			if (nativeAddAudioConfigData(audioConfig, audioConfigLen) != 0) {
				LogUtils.e(TAG, "[addAudioConfigData] failed to add audio config data");
			}else {
				LogUtils.i(TAG, "[addVideoConfigData] add audio config data success!");
				mbAudioAdded = true;
			}
			
			if(mbAudioAdded && mbVideoAdded && !mPublisherStarted)
			{
				if (nativeStart() == 0) {
					mPublisherStarted = true;
				}
			}
		}
	}
	
	public void addVideoRawData(byte[] videoData, int videoDataLen, long absTimeStampMs)
	{
		nativeAddVideoRawData(videoData, videoDataLen, absTimeStampMs);
		LogUtils.i(TAG, "[addVideoRawData]:  "  + " absTimeStampMs = " + absTimeStampMs + " length = " + videoDataLen);
	}

	public void addAudioRawData(byte[] audioData, int audioDataLen, long absTimeStampMs)
	{
		nativeAddAudioRawData(audioData, audioDataLen, absTimeStampMs);
		LogUtils.i(TAG, "[addAudioRawData]:  "  + " absTimeStampMs = " + absTimeStampMs + " length = " + audioDataLen);
	}

	public void setConnectStatus(boolean isConnected)
	{
		if (mPublisherStarted) {
			nativeSetConnectStatus(isConnected);
		}
	}

	public void setTimeIntervalMs(long timeIntervalMs)
	{
		mTimeIntervalMs = timeIntervalMs;
		nativeSetTimeIntervalMs(mTimeIntervalMs);
	}
	
	public int getUploadRate()
	{
		if (mPublisherStarted) {
			return nativeGetUploadRate();
		}
		return 0;
	}
	
	public int getRestAudioFramesNum()
	{
		if (mPublisherStarted) {
			return nativeGetRestAudioFramesNum();
		}
		return 0;
	}
	
	public int getRestVideoFramesNum()
	{
		if (mPublisherStarted) {
			return nativeGetRestVideoFramesNum();
		}
		return 0;
	}
	
	public int getDropFramesNum()
	{
		if (mPublisherStarted) {
			return nativeGetDropFramesNum();
		}
		return 0;
	}

	private static native int nativeInit(String rtmp_url, int frameRate, int width, int height, int bitRate, int samplerate, int channels);

	private static native int nativeAddVideoConfigData(byte[] videoConfig, int videoConfigLen);

	private static native int nativeAddAudioConfigData(byte[] audioConfig, int audioConfigLen);

	private static native int nativeStart();

	private static native int nativeAddVideoRawData(byte[] videoData, int videoDataLen, long absTimeStampMs);

	private static native int nativeAddAudioRawData(byte[] audioData, int audioDataLen, long absTimeStampMs);

	private static native int nativeStop();
	
	private static native int nativeReconnect();
	
	private static native int nativeSetConnectStatus(boolean isConnected);
	
	private static native int nativeGetUploadRate();
	
	private static native int nativeGetRestAudioFramesNum();
	
	private static native int nativeGetRestVideoFramesNum();
	
	private static native int nativeGetDropFramesNum();

	private static native int nativeSetTimeIntervalMs(long timeIntervalMs);
}
