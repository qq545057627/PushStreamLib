package com.yxd.live.recording.utils;


public class JpegUtils {
	
	public static native int nativeJpegToRGB(String jpegFilePath, byte[] RgbData);
	
	public static native int nativeJpegToI420(String jpegFilePath, byte[] I420Data);
	
	public static native int nativeJpegToI420Scale(String jpegFilePath, byte[] I420Data, int outputWidth, int outputHeight);
	
}
