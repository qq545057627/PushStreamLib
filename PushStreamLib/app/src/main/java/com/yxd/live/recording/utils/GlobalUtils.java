package com.yxd.live.recording.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.text.format.Formatter;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.regex.Pattern;

public class GlobalUtils {
	private static final String TAG = "GlobalUtils";

	public static int getCPUCores() {
		//Private Class to display only CPU devices in the directory listing
		class CpuFilter implements FileFilter {
			@Override
			public boolean accept(File pathname) {
				//Check if filename is "cpu", followed by a single digit number
				if(Pattern.matches("cpu[0-9]", pathname.getName())) {
					return true;
				}
				return false;
			}      
		}

		try {
			//Get directory containing CPU info
			File dir = new File("/sys/devices/system/cpu/");
			//Filter to only list the devices we care about
			File[] files = dir.listFiles(new CpuFilter());
			LogUtils.d(TAG, "CPU Count: "+files.length);
			//Return the number of cores (virtual CPU devices)
			return files.length;
		} catch(Exception e) {
			//Print exception
			LogUtils.d(TAG, "CPU Count: Failed.");
			e.printStackTrace();
			//Default to return 1 core
			return 1;
		}
	}
	
	public static float getProcessCpuRate()
	{

		float totalCpuTime1 = getTotalCpuTime();
		float processCpuTime1 = getAppCpuTime();
		try
		{
			Thread.sleep(360);

		}
		catch (Exception e)
		{
		}

		float totalCpuTime2 = getTotalCpuTime();
		float processCpuTime2 = getAppCpuTime();

		float cpuRate = 100 * (processCpuTime2 - processCpuTime1) / (totalCpuTime2 - totalCpuTime1);

		return cpuRate;
	}

	/**
	 * 获取Cpu使用率
	 * (总时间-idle)/总时间
	 * @return
     */
	public static float getAllCpuRate(){
		float totalCpuTime1 = getTotalCpuTime();
		float idelTime1 = getIdelTime();
		try
		{
			Thread.sleep(360);

		}
		catch (Exception e)
		{
		}
		float totalCpuTime2 = getTotalCpuTime();
		float idelTime2 = getIdelTime();
		float cpuRate= 100*((totalCpuTime2-totalCpuTime1)-(idelTime2-idelTime1))/(totalCpuTime2-totalCpuTime1);
        return cpuRate;
	}
	/**
	 * 获取Cpu使用率
	 * (user+system)/总时间
	 * @return
	 */
	public static float getAllCpuRate2(){
		float totalCpuTime1 = getTotalCpuTime();
		long [] arr=getUserAndSystem();
		try
		{
			Thread.sleep(360);

		}
		catch (Exception e)
		{
		}
		float totalCpuTime2 = getTotalCpuTime();
		long [] arr2=getUserAndSystem();
		float cpuRate= 100*((arr2[0]+arr2[1])-(arr[0]+arr[1]))/(totalCpuTime2-totalCpuTime1);
		return cpuRate;
	}

	public static long getTotalCpuTime()
	{
		String[] cpuInfos = null;
		try
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream("/proc/stat")), 1000);
			String load = reader.readLine();
			reader.close();
			cpuInfos = load.split(" ");
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}
		long totalCpu = Long.parseLong(cpuInfos[2])
				+ Long.parseLong(cpuInfos[3]) + Long.parseLong(cpuInfos[4])
				+ Long.parseLong(cpuInfos[6]) + Long.parseLong(cpuInfos[5])
				+ Long.parseLong(cpuInfos[7]) + Long.parseLong(cpuInfos[8]);
		return totalCpu;
	}

	public static long[] getUserAndSystem()
	{
		String[] cpuInfos = null;
		try
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream("/proc/stat")), 1000);
			String load = reader.readLine();
			reader.close();
			cpuInfos = load.split(" ");
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}
		Long user=Long.parseLong(cpuInfos[2]);
		Long system=Long.parseLong(cpuInfos[4]);;

		return new long[]{user,system};
	}

	public static long getIdelTime()
	{
		String[] cpuInfos = null;
		try
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream("/proc/stat")), 1000);
			String load = reader.readLine();
			reader.close();
			cpuInfos = load.split(" ");
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}
		long totalCpu = Long.parseLong(cpuInfos[5]);

		return totalCpu;
	}

	/**
	 * cpu空置率
	 * @return
     */
	public static  int getFreeCpuRate() {

		StringBuilder tv = new StringBuilder();
		int rate = 0;

		try {
			String Result;
			Process p;
			p = Runtime.getRuntime().exec("top -n 1");

			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((Result = br.readLine()) != null) {
				if (Result.trim().length() < 1) {
					continue;
				} else {
					String[] CPUusr = Result.split("%");
					tv.append("USER:" + CPUusr[0] + "\n");
					String[] CPUusage = CPUusr[0].split("User");
					String[] SYSusage = CPUusr[1].split("System");
					tv.append("CPU:" + CPUusage[1].trim() + " length:" + CPUusage[1].trim().length() + "\n");
					tv.append("SYS:" + SYSusage[1].trim() + " length:" + SYSusage[1].trim().length() + "\n");

					rate = Integer.parseInt(CPUusage[1].trim()) + Integer.parseInt(SYSusage[1].trim());
					break;
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return rate;
	}

	public static long getAppCpuTime()
	{
		String[] cpuInfos = null;
		try
		{
			int pid = android.os.Process.myPid();
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream("/proc/" + pid + "/stat")), 1000);
			String load = reader.readLine();
			reader.close();
			cpuInfos = load.split(" ");
		}
		catch (IOException ex)
		{
			ex.printStackTrace();
		}
		long appCpuTime = Long.parseLong(cpuInfos[13])
				+ Long.parseLong(cpuInfos[14]) + Long.parseLong(cpuInfos[15])
				+ Long.parseLong(cpuInfos[16]);
		return appCpuTime;
	}

	public static int getSupportedColorFormat()
	{
		int numCodecs = MediaCodecList.getCodecCount();
		MediaCodecInfo codecInfo = null;
		boolean found = false;
		int colorFormat = 0;
		for (int i = 0; i < numCodecs; i++) {
			MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
			if (info.isEncoder()) {
				String[] types = info.getSupportedTypes();
				for (int j = 0; j < types.length && !found; j++) {
					if (types[j].equals("video/avc")) {
						LogUtils.i(TAG, "found");
						found = true;
					}
				}
			}
			if (found) {
				codecInfo = info;
				break;
			}else {
				continue;
			}	
		}
		LogUtils.d(TAG, "Found" + codecInfo.getName() + " supporting video/avc");
		
		MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
		LogUtils.i(TAG, "length-" + capabilities.colorFormats.length + "==" + Arrays.toString(capabilities.colorFormats));
		Arrays.sort(capabilities.colorFormats);
		LogUtils.i(TAG, "sorted length-" + capabilities.colorFormats.length + "==" + Arrays.toString(capabilities.colorFormats));
		int index1 = Arrays.binarySearch(capabilities.colorFormats, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
		int index2 = Arrays.binarySearch(capabilities.colorFormats, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
		if (index1 >= 0 && index2 >= 0) {
			String manufacturer = android.os.Build.MANUFACTURER;
			LogUtils.e(TAG,"�ֻ��������ǣ�" + android.os.Build.MANUFACTURER);
			if (manufacturer.equals("HUAWEI")) {
				//When use COLOR_FormatYUV420Planar as MediaCodec's input color format, we got a SIGABRT
				//(frameworks/av/media/libstagefright/ACodec.cpp:1705 CHECK(def.nBufferSize >= size) failed.)
				colorFormat = capabilities.colorFormats[index2];
			}else {
				colorFormat = capabilities.colorFormats[index1];
			}
		}else if (index1 >= 0 || index2 >= 0) {
			if (index1 >= 0) {
				colorFormat = capabilities.colorFormats[index1];
			}else {
				colorFormat = capabilities.colorFormats[index2];
			}
		}else {
			LogUtils.e(TAG, "Don't support COLOR_FormatYUV420Planar and COLOR_FormatYUV420SemiPlanar color formats");
		}
		LogUtils.i(TAG, "Chosen color format is " + colorFormat);
		return colorFormat;
	}

	/**
	 * 获取当前手机可用内存
	 * @param context
	 * @return
     */
	public static String getAvailMemory(Context context) {

		ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
		am.getMemoryInfo(mi);
		//mi.availMem; 当前系统的可用内存
		long AvailMemor= mi.availMem;
		return Formatter.formatFileSize(context, AvailMemor);// 将获取的内存大小规格化
	}

	/**
	 * 获取当前手机实际的最大内存
	 * @return
     */
	public static String getTotalMemory(Context context) {
		String str1 = "/proc/meminfo";// 系统内存信息文件
		String str2;
		String[] arrayOfString;
		long initial_memory = 0;

		try {
			FileReader localFileReader = new FileReader(str1);
			BufferedReader localBufferedReader = new BufferedReader(
					localFileReader, 8192);
			str2 = localBufferedReader.readLine();// 读取meminfo第一行，系统总内存大小

			arrayOfString = str2.split("\\s+");
			for (String num : arrayOfString) {
				Log.i(str2, num + "\t");
			}

			initial_memory = Integer.valueOf(arrayOfString[1]).intValue() * 1024;// 获得系统总内存，单位是KB，乘以1024转换为Byte
			localBufferedReader.close();

		} catch (IOException e) {
		}
		long TotalMemory=initial_memory;
		return Formatter.formatFileSize(context, TotalMemory);// Byte转换为KB或者MB，内存大小规格化
	}
}
