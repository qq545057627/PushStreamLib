package com.yxd.live.recording.interfaces;

import android.app.Activity;
import android.media.projection.MediaProjection;
import android.opengl.GLSurfaceView;


public interface IMediaRecorder {
	
	int MEDIA_PREPARED_SETUP_URL_SUCCESS = 100;

	int MEDIA_PREPARED_CONNECT_SUCCESS = 101;

	int MEDIA_PREPARED_CONNECT_STREAM_SUCCESS = 102;

	int	MEDIA_ERROR_UNKNOWN = 2;

	int MEDIA_ERROR_SETUP_URL_FAILED = 200;

	int	MEDIA_ERROR_CONNECT_FAILED = 201;

	int	MEDIA_ERROR_CONNECT_STREAM_FAILED = 202;

	int MEDIA_ERROR_CONNECT_BREAK = 203;

	int	MEDIA_ERROR_SEND_PACKER_FAILED = 204;

	int MEDIA_ERROR_FRAME_DATA_SEND_SLOW = 205;

	int MEDIA_ERROR_CREATE_VIDEO_ENCODER_FAILED = 206;

	int MEDIA_ERROR_CREATE_AUDIO_ENCODER_FAILED = 207;

	int MEDIA_ERROR_VIDEO_ENCODER_BITRATE_NULL = 208;

	int MEDIA_INFO_UNKNOWN = 3;

	int	MEDIA_INFO_BITRATE_RAISE = 300;

	int	MEDIA_INFO_BITRATE_DROP = 301;

	int MEDIA_INFO_RESTART_ENCODER = 302;

	void setMediaProjection(MediaProjection mediaProjection);
	
	void setVideoResolution(int width, int height);

	void setVideoBitrate(int bitrate);
	
	void setFrameRate(int frameRate);
	
	void setIFrameInterval(int iFrameInterval);
	
	void setAudioBitrate(int bitrate);

	void setRtmpUrl(String rtmpUrl);

	void setPrivateJpegPath(String jpegPath);
	
	int getVideoWidth();
	
	int getVideoHeight();

	boolean init();

	boolean start();

	boolean openPrivateMode();

	boolean closePrivateMode();

	boolean restartPrivateMode();

	boolean isPrivateModeOpen();
	
	boolean pauseAudio();

	boolean resumeAudio();
	
	boolean dropBitrate();
	
	boolean raiseBitrate();
	
	void changeVideoResolution(int width, int height);

	boolean stop();

	boolean restartEncoder();
	
	int reconnectRtmpServer();
	
	void setConnectStatus(boolean isConnected);

	void setTimeIntervalMs(long timeIntervalMs);
	
	String getStatisticalInfo();

	long getVideoEncodeBitrate();

	long getAudioEncodeBitrate();

	String getLivingLotOfData();

	boolean getIsEncodeSuccess();

	void setOnPreparedListener(OnPreparedListener listener);

	void setOnStopListener(OnStopListener listener);

	void setOnErrorListener(OnErrorListener listener);

	void setOnInfoListener(OnInfoListener listener);


	/*--------------------
	 * Listeners
	 */
	interface OnPreparedListener {
		boolean onPrepared(IMediaRecorder mr, int what, int extra);
	}

	interface OnStopListener {
		void onStop(IMediaRecorder mr);
	}

	interface OnErrorListener {
		boolean onError(IMediaRecorder mr, int what, int extra);
	}

	interface OnInfoListener {
		boolean onInfo(IMediaRecorder mr, int what, int extra);
	}
	
	//Camera
	boolean initCameraRecording(Activity activity, GLSurfaceView glSurfaceView);
	
	void onCameraResume();
	
	boolean startCameraRecording();
	
	boolean stopCameraRecording();
	
	void changeFlashLightState(boolean isFlashOn);
	
	void changeCameraFacing();

	boolean dropBitrateCamera();
	
	boolean raiseBitrateCamera();
}
