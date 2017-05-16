package com.yxd.live.recording.media;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.projection.MediaProjection;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.yxd.live.recording.annotations.CalledByNative;
import com.yxd.live.recording.bean.BitratesDta;
import com.yxd.live.recording.utils.GlobalUtils;
import com.yxd.live.recording.utils.LogUtils;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class LrMediaRecorder extends AbstractMediaRecoder {

	static{
		try{
			System.loadLibrary("screencapture");
			System.loadLibrary("rtmp-0");
		}catch (Exception e){
			e.printStackTrace();
		}

	}

	public final static String TAG = "LrMediaRecorder";
	private static LrMediaRecorder instance = null;

	public static final int MEDIA_NOP = 0; // interface test message
    public static final int MEDIA_PREPARED = 1;
    public static final int MEDIA_ERROR = 100;
    public static final int MEDIA_INFO = 200;

    private static final int STATUS_UNINITIALIZE = 0;
    private static final int STATUS_INITIALIZE = 1;
    private static final int STATUS_START = 2;
    private static final int STATUS_PAUSE = 3;
    private static final int STATUS_RESUME = 4;
    private static final int STATUS_STOP = 5;

	private static int status = STATUS_UNINITIALIZE;

	private EventHandler mEventHandler;

	private MediaProjection mMediaProjection = null;
	private int mWidth = 540;
	private int mHeight = 960;
	private int mVideoBitrate = 950000;
	private int mFrameRate = 30;
	private int mIFrameInterval = 2;
	private int mSampleRate = 16000;
	private int mAudioBitrate = 64 * 1024;
	private int mChannel = AudioFormat.CHANNEL_IN_STEREO;
//	private int mChannel = AudioFormat.CHANNEL_IN_MONO;
	private int mFormat = AudioFormat.ENCODING_PCM_16BIT;
	private String mPrivateJpegPath = "";
	private String mRtmpUrl = "";
	private boolean mIsPrivateModeOpen = false;
	private long mTimeIntervalMs = 6000;
	
	private LrMediaRecorder()
	{
        status = STATUS_UNINITIALIZE;
	}

	public static LrMediaRecorder getInstance()
	{
		if (instance == null) {
			instance = new LrMediaRecorder();
		}
		return instance;
	}


	public static void destroyInstance()
	{
		LogUtils.i(TAG, "LrMediaRecorder destroyInstance()");
		if (instance != null) {
			instance = null;
		}
	}

	@Override
	public void setMediaProjection(MediaProjection mediaProjection) {
		this.mMediaProjection = mediaProjection;
	}
	
	@Override
	public void setVideoResolution(int width, int height)
	{
		this.mWidth = width;
		this.mHeight = height;
	}

	@Override
	public void setVideoBitrate(int bitrate)
	{
		this.mVideoBitrate = bitrate;
	}
	
	@Override
	public void setFrameRate(int frameRate) {
		this.mFrameRate = frameRate;
	}

	@Override
	public void setIFrameInterval(int iFrameInterval) {
		this.mIFrameInterval = iFrameInterval;
	}

	@Override
	public void setAudioBitrate(int bitrate) {
		this.mAudioBitrate = bitrate;
	}

	@Override
	public void setRtmpUrl(String rtmpUrl) {
		this.mRtmpUrl = rtmpUrl;
	}

	@Override
	public void setPrivateJpegPath(String jpegPath) {
		this.mPrivateJpegPath = jpegPath;
	}
	
	@Override
	public int getVideoWidth() {
		return mWidth;
	}
	
	@Override
	public int getVideoHeight() {
		return mHeight;
	}

	@Override
	public boolean init() {
		if (status != STATUS_UNINITIALIZE) {
			LogUtils.e(TAG, "Invalid status for init()");
            return false;
		} else {
			LogUtils.i(TAG, "init() status is " + status);
			nativeSetJNIEnv();
			Looper looper;
			if ((looper = Looper.myLooper()) != null) {
				mEventHandler = new EventHandler(this, looper);
			} else if ((looper = Looper.getMainLooper()) != null) {
				mEventHandler = new EventHandler(this, looper);
			} else {
				mEventHandler = null;
			}
			MediaRtmpPublisher.getInstance().init(mRtmpUrl, mFrameRate, mWidth, mHeight, mVideoBitrate/1000, mSampleRate, 1);
			status = STATUS_INITIALIZE;
            return true;
		}
	}

	@Override
	public boolean start() {
		if (status != STATUS_INITIALIZE) {
			LogUtils.e(TAG, "Invalid status for start()");
            return false;
		} else {
			try{
				LogUtils.i(TAG, "start() status is " + status);
				MediaVideoCaptureEgl.getInstance().init(mWidth, mHeight, mVideoBitrate, mFrameRate, mIFrameInterval, mMediaProjection);
				MediaVideoCaptureEgl.getInstance().start();

				MediaAudioCapture.getInstance().init(mSampleRate, mChannel, mFormat, mAudioBitrate);
				MediaAudioCapture.getInstance().start();


				status = STATUS_START;
				return true;
			}catch (Exception e){
				e.printStackTrace();
			}
			return false;
		}
	}

	@Override
	public boolean openPrivateMode() {
		if (status != STATUS_START && status != STATUS_RESUME) {
			LogUtils.e(TAG, "Invalid status for openPrivateMode()");
            return false;
		} else {
			LogUtils.i(TAG, "openPrivateMode() status is " + status);
			MediaVideoCaptureEgl.getInstance().pause();
			MediaVideoEncoderHWJpeg.getInstance().init(mWidth, mHeight, mVideoBitrate, mFrameRate, mPrivateJpegPath);
			MediaVideoEncoderHWJpeg.getInstance().start();
			status = STATUS_PAUSE;
			mIsPrivateModeOpen = true;
            return true;
		}
	}

	@Override
	public boolean closePrivateMode() {
		if (status != STATUS_PAUSE) {
			LogUtils.e(TAG, "Invalid status for closePrivateMode()");
            return false;
		} else {
			LogUtils.i(TAG, "closePrivateMode() status is " + status);
			MediaVideoCaptureEgl.getInstance().resume();
			MediaVideoEncoderHWJpeg.destroyInstance();
			status = STATUS_RESUME;
			mIsPrivateModeOpen = false;
            return true;
		}	
	}

	@Override
	public boolean restartPrivateMode() {
		if (status != STATUS_PAUSE) {
			LogUtils.e(TAG, "Invalid status for restartPrivateMode()");
			return false;
		} else {
			LogUtils.i(TAG, "restartPrivateMode() status is " + status);
			MediaVideoEncoderHWJpeg.destroyInstance();
			MediaVideoCaptureEgl.getInstance().pause();
			MediaVideoEncoderHWJpeg.getInstance().init(mWidth, mHeight, mVideoBitrate, mFrameRate, mPrivateJpegPath);
			MediaVideoEncoderHWJpeg.getInstance().start();
			status = STATUS_PAUSE;
			mIsPrivateModeOpen = true;
			return true;
		}
	}

	@Override
	public boolean isPrivateModeOpen() {
		return mIsPrivateModeOpen;
	}

	@Override
	public boolean pauseAudio() {
		if (status != STATUS_START && status != STATUS_PAUSE && status != STATUS_RESUME){
			LogUtils.e(TAG, "Invalid status for pauseAudio()");
            return false;
		}else {

			MediaAudioCapture.getInstance().pause();
            return true;
		}
	}

	@Override
	public boolean resumeAudio() {
		if (status != STATUS_START && status != STATUS_PAUSE && status != STATUS_RESUME){
			LogUtils.e(TAG, "Invalid status for pauseAudio()");
            return false;
		}else {
			MediaAudioCapture.getInstance().resume();
            return true;
		}
	}

	private List<BitratesDta> listBit;

	public List<BitratesDta> getListBit() {
		return listBit;
	}

	public void setListBit(List<BitratesDta> listBit) {
		this.listBit = listBit;
	}

	@Override
	public boolean dropBitrate() {
		if (status != STATUS_START && status != STATUS_PAUSE && status != STATUS_RESUME) {
			LogUtils.e(TAG, "Invalid status for dropBitrate()");
            return false;
		} else {
			Log.i(TAG, "dropBitrate() status is " + status);
			if (status == STATUS_PAUSE) {
				//ignore when opening private mode
				return true;
			}
			if (mVideoBitrate == 450000) {
				//ignore when current definition is already the lowest
				return true;
			}
			//set the definition and resolution to the lower level
			if (listBit != null)
			{
				int index = -1;
				for (int i = 0; i < listBit.size(); i++)
				{
					Log.i(TAG,  "Bitrate at " + i + " is " + listBit.get(i).vbr); //bitrate升序排列
					if (listBit.get(i).vbr == mVideoBitrate)
						index = i;
				}
				Log.i(TAG, "Current bitrate is " + mVideoBitrate +" and index is " + index);
				if (index != -1 && index -1 >= 0)
				{
					BitratesDta bitratesDta = listBit.get(index - 1);
					mVideoBitrate= bitratesDta.vbr;
					mWidth = bitratesDta.w;
					mHeight = bitratesDta.h;
					mIFrameInterval = bitratesDta.gop;
					mFrameRate = bitratesDta.fps;
					Log.i(TAG, "Drop bitrate is " + mVideoBitrate + " width is " + mWidth + " height is " + mHeight + " Gop is " + mIFrameInterval + " Frame rate is " + mFrameRate);
				}
				else
				{
					return true;
				}
			}
			else
			{
				switch (mVideoBitrate) {
					case 1500000:
						mVideoBitrate = 1150000;
						if (mWidth > mHeight) {
							mWidth = 1024;
							mHeight = 576;
						} else {
							mWidth = 576;
							mHeight = 1024;
						}
						break;

					case 1150000:
						mVideoBitrate = 950000;
						if (mWidth > mHeight) {
							mWidth = 960;
							mHeight = 540;
						} else {
							mWidth = 540;
							mHeight = 960;
						}
						break;

					case 950000:
						String model = Build.MODEL;
						if (model.equals("MHA-AL00")) {
							//HUAWEI MATE9 don't support the resolution of 854*480
							return true;
						}
						mVideoBitrate = 700000;
						if (mWidth > mHeight) {
							mWidth = 854;
							mHeight = 480;
						} else {
							mWidth = 480;
							mHeight = 854;
						}
						mFrameRate = 25;
						break;

					case 700000:
						mVideoBitrate = 450000;
						if (mWidth > mHeight) {
							mWidth = 640;
							mHeight = 360;
						} else {
							mWidth = 360;
							mHeight = 640;
						}
						mFrameRate = 20;
						break;

					default:
						break;
				}
			}

			MediaVideoCaptureEgl.getInstance().setResolutionChangeFlag(true);
			MediaVideoCaptureEgl.destroyInstance();
			MediaVideoCaptureEgl.getInstance().init(mWidth, mHeight, mVideoBitrate, mFrameRate, mIFrameInterval, mMediaProjection);
			MediaVideoCaptureEgl.getInstance().start();
            return true;
		}
	}

	@Override
	public boolean raiseBitrate() {
		if (status != STATUS_START && status != STATUS_PAUSE && status != STATUS_RESUME) {
			LogUtils.e(TAG, "Invalid status for raiseBitrate()");
            return false;
		} else {
			LogUtils.i(TAG, "raiseBitrate() status is " + status);
			if (status == STATUS_PAUSE) {
				//ignore when opening private mode
				return true;
			}
			if (mVideoBitrate == 1500000) {
				//ignore when current definition is already the highest
				return true;
			}
			//set the definition and resolution to the higher level
			if (listBit != null)
			{
				int index = -1;
				for (int i = 0; i < listBit.size(); i++)
				{
					Log.i(TAG,  "Bitrate at " + i + " is " + listBit.get(i).vbr); //bitrate升序排列
					if (listBit.get(i).vbr == mVideoBitrate)
						index = i;
				}
				Log.i(TAG, "Current bitrate is " + mVideoBitrate +" and index is " + index);
				if (index != -1 && index + 1 < listBit.size())
				{
					BitratesDta bitratesDta = listBit.get(index + 1);
					mVideoBitrate= bitratesDta.vbr;
					mWidth = bitratesDta.w;
					mHeight = bitratesDta.h;
					mIFrameInterval = bitratesDta.gop;
					mFrameRate = bitratesDta.fps;
					Log.i(TAG, "Raise bitrate is " + mVideoBitrate + " width is " + mWidth + " height is " + mHeight + " Gop is " + mIFrameInterval + " Frame rate is " + mFrameRate);
				}
				else
				{
					return true;
				}
			}
			else
			{
				switch (mVideoBitrate) {
					case 450000:
						String model = Build.MODEL;
						if (model.equals("MHA-AL00")) {
							//HUAWEI MATE9 don't support the resolution of 854*480
							return true;
						}
						mVideoBitrate = 700000;
						if (mWidth > mHeight) {
							mWidth = 854;
							mHeight = 480;
						} else {
							mWidth = 480;
							mHeight = 854;
						}
						mFrameRate = 25;
						break;

					case 700000:
						mVideoBitrate = 950000;
						if (mWidth > mHeight) {
							mWidth = 960;
							mHeight = 540;
						} else {
							mWidth = 540;
							mHeight = 960;
						}
						mFrameRate = 30;
						break;

					case 950000:
						mVideoBitrate = 1150000;
						if (mWidth > mHeight) {
							mWidth = 1024;
							mHeight = 576;
						} else {
							mWidth = 576;
							mHeight = 1024;
						}
						break;

					case 1150000:
						mVideoBitrate = 1500000;
						if (mWidth > mHeight) {
							mWidth = 1280;
							mHeight = 720;
						} else {
							mWidth = 720;
							mHeight = 1280;
						}
						break;

					default:
						break;
				}
			}

			MediaVideoCaptureEgl.getInstance().setResolutionChangeFlag(true);
			MediaVideoCaptureEgl.destroyInstance();
			MediaVideoCaptureEgl.getInstance().init(mWidth, mHeight, mVideoBitrate, mFrameRate, mIFrameInterval, mMediaProjection);
			MediaVideoCaptureEgl.getInstance().start();
            return true;
		}
	}
	
	@Override
	public void changeVideoResolution(int width, int height) {
		Log.i(TAG, "changeVideoResolution");
		this.mWidth = width;
		this.mHeight = height;
		MediaVideoCaptureEgl.getInstance().setResolutionChangeFlag(true);
		MediaVideoCaptureEgl.destroyInstance();
		MediaVideoCaptureEgl.getInstance().init(mWidth, mHeight, mVideoBitrate, mFrameRate, mIFrameInterval, mMediaProjection);
		MediaVideoCaptureEgl.getInstance().start();
	}

	@Override
	public boolean stop() {
		if (status != STATUS_START && status != STATUS_PAUSE && status != STATUS_RESUME) {
			LogUtils.e(TAG, "Invalid status for reset()");
			Log.e(TAG, "reset: ABC  Invalid status for reset()");
            return false;
		} else {
			LogUtils.i(TAG, " reset() status is " + status);
			//Log.e(TAG, "reset: ABC  reset() status is " + status);
			if (status == STATUS_PAUSE) {
				MediaVideoEncoderHWJpeg.destroyInstance();
			}
			MediaVideoCaptureEgl.destroyInstance();
			MediaAudioCapture.destroyInstance();
			MediaRtmpPublisher.destroyInstance();
			LogUtils.i(TAG, " reset() finished");
			//Log.e(TAG, "reset: ABC  finished");
			status = STATUS_STOP;
            return true;
		}
	}

	@Override
	public boolean restartEncoder() {
		if (status != STATUS_START && status != STATUS_PAUSE && status != STATUS_RESUME) {
			LogUtils.e(TAG, "Invalid status for restartEncoder()");
            return false;
		} else {
			LogUtils.i(TAG, "restartEncoder() status is " + status);
			if (status == STATUS_PAUSE) {
				//ignore when opening private mode
				return true;
			}
			MediaVideoCaptureEgl.getInstance().setResolutionChangeFlag(true);
			MediaVideoCaptureEgl.destroyInstance();
			MediaVideoCaptureEgl.getInstance().init(mWidth, mHeight, mVideoBitrate, mFrameRate, mIFrameInterval, mMediaProjection);
			MediaVideoCaptureEgl.getInstance().start();
            return true;
		}
	}

	@Override
	public int reconnectRtmpServer() {
		return MediaRtmpPublisher.getInstance().reconnect();
	}
	
	@Override
	public void setConnectStatus(boolean isConnected) {
		MediaRtmpPublisher.getInstance().setConnectStatus(isConnected);
	}

	@Override
	public void setTimeIntervalMs(long timeIntervalMs) {
		mTimeIntervalMs = timeIntervalMs;
		MediaAudioCapture.getInstance().setTimeIntervalMs(mTimeIntervalMs);
		MediaVideoCaptureEgl.getInstance().setTimeIntervalMs(mTimeIntervalMs);
		MediaRtmpPublisher.getInstance().setTimeIntervalMs(mTimeIntervalMs);
	}

	@Override
	public String getStatisticalInfo() {
		if (status != STATUS_START && status != STATUS_PAUSE && status != STATUS_RESUME) {
			LogUtils.e(TAG, "Invalid status for getStatisticalInfo()");
		} else {
			int iVideoFramesPerSec = MediaVideoCaptureEgl.getInstance().getFramesPerSec();
			int iAudioFramesPerSec = MediaAudioCapture.getInstance().getFramesPerSec();
			int iUploadRate = MediaRtmpPublisher.getInstance().getUploadRate();
			int iRestAudioFramesNum = MediaRtmpPublisher.getInstance().getRestAudioFramesNum();
			int iRestVideoFramesNum = MediaRtmpPublisher.getInstance().getRestVideoFramesNum();
			int iDropFramesNum = MediaRtmpPublisher.getInstance().getDropFramesNum();
			int processCpuRate = (int) GlobalUtils.getProcessCpuRate();
			int totalMemory = (int) (Runtime.getRuntime().totalMemory() / 1024 / 1024);
			long iVideoBitratePerSec = MediaVideoCaptureEgl.getInstance().getBitratePerSec();
			long iAudioBitratePerSec = MediaAudioCapture.getInstance().getBitratePerSec();
			String strStatisticalInfo = (new StringBuilder("Video fps is ").append(iVideoFramesPerSec).append(" and audio fps is ").append(iAudioFramesPerSec).append(".\r\n")
					.append("video encoder bitrate is ").append(iVideoBitratePerSec).append(" Kbps").append("\r\n")
					.append("audio encoder bitrate is ").append(iAudioBitratePerSec).append(" Kbps").append("\r\n")
					.append("Upload rate is ").append(iUploadRate).append(" Kbps").append("\r\n")
					.append("Video queue remains ").append(iRestVideoFramesNum).append(" frames and audio queue remains ").append(iRestAudioFramesNum).append(" frames.\r\n")
					.append("Drop ").append(iDropFramesNum).append(" frames per second.\r\n")
					.append("Cpu utilization rate is ").append(processCpuRate).append("%.\r\n"))
					.append("Total memory is ").append(totalMemory).append(" MB.\r\n").toString();
			LogUtils.i(TAG, "Statistical information is " + strStatisticalInfo);
			return strStatisticalInfo;
		}	
		return null;
	}

	@Override
	public String getLivingLotOfData() {
		if (status != STATUS_START && status != STATUS_PAUSE && status != STATUS_RESUME) {
			LogUtils.e(TAG, "Invalid status for getStatisticalInfo()");
		} else {
			int iVideoFramesPerSec = MediaVideoCaptureEgl.getInstance().getFramesPerSec();
			int iAudioFramesPerSec = MediaAudioCapture.getInstance().getFramesPerSec();
			int iUploadRate = MediaRtmpPublisher.getInstance().getUploadRate();
			int iRestAudioFramesNum = MediaRtmpPublisher.getInstance().getRestAudioFramesNum();
			int iRestVideoFramesNum = MediaRtmpPublisher.getInstance().getRestVideoFramesNum();
			int iDropFramesNum = MediaRtmpPublisher.getInstance().getDropFramesNum();
			int processCpuRate = (int) GlobalUtils.getProcessCpuRate();
			int totalMemory = (int) (Runtime.getRuntime().totalMemory() / 1024 / 1024);
			long iVideoBitratePerSec = MediaVideoCaptureEgl.getInstance().getBitratePerSec();
			long iAudioBitratePerSec = MediaAudioCapture.getInstance().getBitratePerSec();
			SimpleDateFormat simple = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			String format = simple.format(new Date(System.currentTimeMillis()));
			String strStatisticalInfo = (
					new StringBuilder("VFPS:").append(iVideoFramesPerSec)
							.append("  VBR:").append(iVideoBitratePerSec)
							.append("  ABR:").append(iAudioBitratePerSec)
							.append("  AFPS:").append(iAudioFramesPerSec)
							.append("  URATE:").append(iUploadRate)
							.append("  V_Q_R:").append(iRestVideoFramesNum)
							.append("  A_Q_R:").append(iRestAudioFramesNum)
							.append("  DROP:").append(iDropFramesNum)
							.append("  CPU:").append(processCpuRate)
					        .append("  U_RAM:").append(totalMemory)
							.append("  TIME:").append(format)
			).toString();
//			LogUtils.i(TAG, "Statistical information is " + strStatisticalInfo);
			return strStatisticalInfo;
		}
		return null;
	}


	@Override
	public long getVideoEncodeBitrate() {
		return MediaVideoCaptureEgl.getInstance().getBitratePerSec();
	}

	@Override
	public long getAudioEncodeBitrate() {
		return MediaAudioCapture.getInstance().getBitratePerSec();
	}

	@Override
	public boolean getIsEncodeSuccess() {
		return MediaVideoCaptureEgl.getInstance().getIsEncodeSuccess();
	}

	@Override
	public boolean initCameraRecording(Activity activity, GLSurfaceView glSurfaceView) {
		if (status != STATUS_UNINITIALIZE) {
			LogUtils.e(TAG, "Invalid status for init()");
            return false;
		} else {
			LogUtils.i(TAG, "init() status is " + status);
			nativeSetJNIEnv();
			Looper looper;
			if ((looper = Looper.myLooper()) != null) {
				mEventHandler = new EventHandler(this, looper);
			} else if ((looper = Looper.getMainLooper()) != null) {
				mEventHandler = new EventHandler(this, looper);
			} else {
				mEventHandler = null;
			}
			MediaVideoCaptureCamera.getInstance().init(activity, glSurfaceView, mWidth, mHeight, mVideoBitrate, mFrameRate, mIFrameInterval);
			MediaAudioCapture.getInstance().init(mSampleRate, mChannel, mFormat, mAudioBitrate);

			MediaRtmpPublisher.getInstance().init(mRtmpUrl, mFrameRate, mWidth, mHeight, mVideoBitrate/1000, mSampleRate, 1);

			status = STATUS_INITIALIZE;
            return true;
		}	
	}
	
	@Override
	public void onCameraResume() {
		MediaVideoCaptureCamera.getInstance().onResume();
	}
	
	@Override
	public boolean startCameraRecording() {
		if (status != STATUS_INITIALIZE) {
			LogUtils.e(TAG, "Invalid status for start()");
            return false;
		} else {
			LogUtils.i(TAG, "start() status is " + status);
			MediaAudioCapture.getInstance().start();
			MediaVideoCaptureCamera.getInstance().start();
			status = STATUS_START;
            return true;
		}
		
	}
	
	@Override
	public boolean stopCameraRecording() {
		if (status != STATUS_START) {
			LogUtils.e(TAG, "Invalid status for reset()");
            return false;
		} else {
			LogUtils.i(TAG, "reset() status is " + status);
			MediaVideoCaptureCamera.destroyInstance();
			LogUtils.i(TAG, "reset()1+++++++++++");
			MediaAudioCapture.destroyInstance();
			LogUtils.i(TAG, "reset()2+++++++++++");
			LogUtils.i(TAG, "reset()3+++++++++++");
			MediaRtmpPublisher.destroyInstance();
			LogUtils.i(TAG, "reset() finished");
			status = STATUS_STOP;
            return true;
		}
	}
	
	@Override
	public void changeFlashLightState(boolean isFlashOn) {
		MediaVideoCaptureCamera.getInstance().changeFlashLightState(isFlashOn);
	}
	
	@Override
	public void changeCameraFacing() {
		MediaVideoCaptureCamera.getInstance().changeCameraFacing();
	}
	
	@Override
	public boolean raiseBitrateCamera() {
        if (status != STATUS_START) {
            LogUtils.e(TAG, "Invalid status for raiseBitrate()");
            return false;
        } else {
            LogUtils.i(TAG, "raiseBitrate() status is " + status);
            if (mVideoBitrate == 1500000) {
                //ignore when current definition is already the highest
                return true;
            }
            //set the definition and resolution to the higher level
            switch (mVideoBitrate) {
                case 450000:
                    mVideoBitrate = 700000;
                    if (mWidth > mHeight) {
                        mWidth = 854;
                        mHeight = 480;
                    } else {
                        mWidth = 480;
                        mHeight = 854;
                    }
                    mFrameRate = 25;
                    break;

                case 700000:
                    mVideoBitrate = 950000;
                    if (mWidth > mHeight) {
                        mWidth = 960;
                        mHeight = 540;
                    } else {
                        mWidth = 540;
                        mHeight = 960;
                    }
                    mFrameRate = 30;
                    break;

                case 950000:
                    mVideoBitrate = 1050000;
                    if (mWidth > mHeight) {
                        mWidth = 1024;
                        mHeight = 576;
                    } else {
                        mWidth = 576;
                        mHeight = 1024;
                    }
                    break;

                case 1050000:
                    mVideoBitrate = 1150000;
                    if (mWidth > mHeight) {
                        mWidth = 1280;
                        mHeight = 720;
                    } else {
                        mWidth = 720;
                        mHeight = 1280;
                    }
                    break;

                case 1150000:
                    mVideoBitrate = 1500000;
                    break;

                default:
                    break;
            }
			MediaVideoCaptureCamera.getInstance().setResolutionChangeFlag(true);
			MediaVideoCaptureCamera.getInstance().raiseBitrate(mWidth, mHeight, mVideoBitrate);
            return true;
		}
	}
	
	@Override
	public boolean dropBitrateCamera() {
        if (status != STATUS_START) {
            LogUtils.e(TAG, "Invalid status for dropBitrate()");
            return false;
        } else {
            LogUtils.i(TAG, "dropBitrate() status is " + status);
            if (mVideoBitrate == 450000) {
                //ignore when current definition is already the lowest
                return true;
            }
            //set the definition and resolution to the lower level
            switch (mVideoBitrate) {
                case 1500000:
                    mVideoBitrate = 1150000;
                    break;

                case 1150000:
                    mVideoBitrate = 1050000;
                    if (mWidth > mHeight) {
                        mWidth = 1024;
                        mHeight = 576;
                    } else {
                        mWidth = 576;
                        mHeight = 1024;
                    }
                    break;

                case 1050000:
                    mVideoBitrate = 950000;
                    if (mWidth > mHeight) {
                        mWidth = 960;
                        mHeight = 540;
                    } else {
                        mWidth = 540;
                        mHeight = 960;
                    }
                    break;

                case 950000:
                    mVideoBitrate = 700000;
                    if (mWidth > mHeight) {
                        mWidth = 854;
                        mHeight = 480;
                    } else {
                        mWidth = 480;
                        mHeight = 854;
                    }
                    mFrameRate = 25;
                    break;

                case 700000:
                    mVideoBitrate = 450000;
                    if (mWidth > mHeight) {
                        mWidth = 640;
                        mHeight = 360;
                    } else {
                        mWidth = 360;
                        mHeight = 640;
                    }
                    mFrameRate = 20;
                    break;

                default:
                    break;
            }
            MediaVideoCaptureCamera.getInstance().setResolutionChangeFlag(true);
            MediaVideoCaptureCamera.getInstance().dropBitrate(mWidth, mHeight, mVideoBitrate);
            return true;
		}
	}
	
	
	private static class EventHandler extends Handler {
		private final WeakReference<LrMediaRecorder> mWeakRecorder;

		public EventHandler(LrMediaRecorder mr, Looper looper) {
			super(looper);
			mWeakRecorder = new WeakReference<LrMediaRecorder>(mr);
		}

		@Override
		public void handleMessage(Message msg) {
			LrMediaRecorder recorder = mWeakRecorder.get();

			switch (msg.what) {
			case MEDIA_PREPARED:
				LogUtils.i(TAG, "Prepared (" + msg.arg1 + "," + msg.arg2 + ")");	
				recorder.notifyOnPrepared(msg.arg1, msg.arg2);
				break;

			case MEDIA_ERROR:
				LogUtils.e(TAG, "Error (" + msg.arg1 + "," + msg.arg2 + ")");
//				if (!recorder.notifyOnError(msg.arg1, msg.arg2)) {
//					recorder.notifyOnStop();
//				}
				recorder.notifyOnError(msg.arg1, msg.arg2);
				break;

			case MEDIA_INFO:
				recorder.notifyOnInfo(msg.arg1, msg.arg2);
				break;

			case MEDIA_NOP: // interface test message - ignore
				break;

			default:
				LogUtils.e(TAG, "Unknown message type " + msg.what);
			}
		}
	}

	/*
	 * Called from native code when an interesting event happens. This method
	 * just uses the EventHandler system to post the event back to the main app
	 * thread. 
	 */
	@CalledByNative
	public static void postEventFromNative(int what,
			int arg1, int arg2) {
		if (instance != null) {
			if (instance.mEventHandler != null) {
				Message m = instance.mEventHandler.obtainMessage(what, arg1, arg2);
				instance.mEventHandler.sendMessage(m);
			}
		}
	}

    public static void postEvent(int what, int arg1, int arg2) {
		if (instance != null) {
			if (instance.mEventHandler != null) {
				Message m = instance.mEventHandler.obtainMessage(what, arg1, arg2);
				instance.mEventHandler.sendMessage(m);
			}
		}
    }

	private native void nativeSetJNIEnv(); 
}
