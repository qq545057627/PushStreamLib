package com.yxd.live.recording.utils;


public class PingUtils {
	private static final String TAG = "PingUtils";

	public static native int nativeInit();

	public static native int nativeUninit();

	public static native int nativePing(String hostName);

	public static native String nativeGetHostIp();
}
