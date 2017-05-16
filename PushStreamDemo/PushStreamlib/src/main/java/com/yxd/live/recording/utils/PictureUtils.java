package com.yxd.live.recording.utils;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import java.util.Arrays;

public class PictureUtils {
	private static final String TAG = "PictureUtils";

	private static MediaCodecInfo mCodecInfo = null;

	public static MediaCodecInfo getMediaCodecInfoH264() {

		if (mCodecInfo != null)
			return mCodecInfo;

		int numCodecs = MediaCodecList.getCodecCount();
		MediaCodecInfo codecInfo = null;
		for (int i = 0; i < numCodecs && codecInfo == null; i++) {
			MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
			if (!info.isEncoder()) {
				continue;
			}

			// skip software encoder.
			if (info.getName().startsWith("OMX.google.")) {
				continue;
			}
			String[] types = info.getSupportedTypes();
			boolean found = false;
			for (int j = 0; j < types.length && !found; j++) {
				if (types[j].equals("video/avc")) {
					LogUtils.w(TAG, "[getMediaCodecSupportColorFormat]: found MediaCodec avc encoder");
					found = true;
				}
			}
			if (!found)
				continue;
			codecInfo = info;
		}
		LogUtils.d(TAG, "Found " + codecInfo.getName() + " supporting " + "video/avc");
		mCodecInfo = codecInfo;

		return mCodecInfo;
	}

	public static int getMediaCodecSupportColorFormat() {        
		// Find a color profile that the codec supports
		int colorFormat = 0;
		MediaCodecInfo codecInfo = getMediaCodecInfoH264();
		MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
		LogUtils.i(TAG, "length-" + capabilities.colorFormats.length + "==" + Arrays.toString(capabilities.colorFormats));
		for (int i = 0; i < capabilities.colorFormats.length && colorFormat == 0; i++) {
			int format = capabilities.colorFormats[i];
			switch (format) {
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
				// rayman: for some Sumsung devices with encoder OMX.Exynos, color format 
				// COLOR_FormatYUV420Planar seems not work properly, so ignore this for some specific HW.
				// sumsung encoder generally has the following components: [OMX.Exynos.avc.enc]
				// [OMX.Exynos.AVC.Encoder] [OMX.SEC.avc.enc]. I have performed test on galaxy Mega/S3/S4, 
				// find out that encoder has supported a lot of color formats, especially 
				// value = 19/21/0x7FC00002/0x7F000011/0x7F000789
				// hard-coded(2015/01/15).
				if (codecInfo.getName().contains("OMX.Exynos.") && android.os.Build.VERSION.SDK_INT < 18) {
					break;
				}
				LogUtils.d(TAG, "found COLOR_FormatYUV420Planar");
				colorFormat = format;
				break;
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
				LogUtils.d(TAG, "found COLOR_FormatYUV420PackedPlanar");
				colorFormat = format;
				break;
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
				LogUtils.d(TAG, "found COLOR_FormatYUV420SemiPlanar");
				colorFormat = format;
				break;
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
				LogUtils.d(TAG, "found COLOR_FormatYUV420PackedSemiPlanar");
				colorFormat = format;
				break;
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYCbYCr:
				LogUtils.d(TAG, "found COLOR_FormatYCbYCr");
				colorFormat = format;
				break;
			case MediaCodecInfo.CodecCapabilities.COLOR_FormatYCrYCb:
				LogUtils.d(TAG, "found COLOR_FormatYCrYCb");
				colorFormat = format;
				break;
			case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
				LogUtils.d(TAG, "found COLOR_TI_FormatYUV420PackedSemiPlanar");
				colorFormat = format;
				break;
			default:
				LogUtils.d(TAG, "Skipping unsupported color format " + format);
				break;
			}

			if (colorFormat != 0) {
				LogUtils.d(TAG, "Using color format " + colorFormat);
				return colorFormat;
			}
		}

		LogUtils.e(TAG, "no available color format match");
		return 0;
	}

	public static int getFrameSizeFromColorFormat(int frameWidth, int frameHeight, int colorForamt) {

		int pictureSize = 0;
		switch (colorForamt) {
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:     // I420
			//LogUtils.d(TAG, "[getFrameSizeFromColorFormat]: color format is: COLOR_FormatYUV420Planar");
			pictureSize = frameWidth * frameHeight * 3 / 2;
			break;
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar: // also I420
			//LogUtils.d(TAG, "[getFrameSizeFromColorFormat]: color format is: COLOR_FormatYUV420PackedPlanar");
			pictureSize = frameWidth * frameHeight * 3 / 2;
			break;
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:     // NV12
			//LogUtils.d(TAG, "[getFrameSizeFromColorFormat]: color format is: COLOR_FormatYUV420SemiPlanar");
			pictureSize = frameWidth * frameHeight * 3 / 2;
			break;
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: // also NV12
			//LogUtils.d(TAG, "[getFrameSizeFromColorFormat]: color format is: COLOR_FormatYUV420PackedSemiPlanar");
			pictureSize = frameWidth * frameHeight * 3 / 2;
			break;
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYCbYCr:    
			//LogUtils.d(TAG, "[getFrameSizeFromColorFormat]: color format is: COLOR_FormatYCbYCr");
			pictureSize = frameWidth * frameHeight * 2;
			break;
		case MediaCodecInfo.CodecCapabilities.COLOR_FormatYCrYCb:    
			//LogUtils.d(TAG, "[getFrameSizeFromColorFormat]: color format is: COLOR_FormatYCrYCb");
			pictureSize = frameWidth * frameHeight * 2;
			break;
		case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar: // also NV12
			//LogUtils.d(TAG, "[getFrameSizeFromColorFormat]: color format is: COLOR_TI_FormatYUV420PackedSemiPlanar");
			pictureSize = frameWidth * frameHeight * 3 / 2;
			break;
		case MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888: // RGB32
			//LogUtils.d(TAG, "[getFrameSizeFromColorFormat]: color format is: COLOR_Format32bitARGB8888");
			pictureSize = frameWidth * frameHeight * 4;
			break;
		default:
			LogUtils.e(TAG, "[getFrameSizeFromColorFormat]: error un supported color format: " + colorForamt);
			break;
		}

		return pictureSize;
	}


	public static native int nativeInit(int threadCount);

	public static native int nativeUninit();

	public static native int nativeARGBScale( byte[] src, int srcStride, int srcWidth, int srcHeight,
			byte[] dst, int dstStride, int dstWidth, int dstHeight);

	public static native int nativeScalePlane( byte[] src, int srcStride, int srcWidth, int srcHeight,
			byte[] dst, int dstStride, int dstWidth, int dstHeight);
	
	public static native int nativeRGBARotate( byte[] src, int srcStride, byte[] dst, int dstStride, 
			int width, int height, int rotationDegree);

	public static native int nativeI420ToNV12( byte[] src, int srcStride, byte[] dst, int dstStride, int width, int height);

	public static native int nativeRGBAToI420( byte[] src, int srcStride, byte[] dst, int dstStride, int width, int height);
	
	public static native int nativeRGB565ToI420( byte[] src, int srcStride, byte[] dst, int dstStride, int width, int height);

}
