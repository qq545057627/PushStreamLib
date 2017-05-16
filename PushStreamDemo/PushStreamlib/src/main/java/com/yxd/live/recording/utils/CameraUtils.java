package com.yxd.live.recording.utils;

import android.hardware.Camera;
import android.hardware.Camera.Size;

import java.util.List;


public class CameraUtils {
	
	private static final String TAG = "CameraUtils";
	/**
	 * Attempts to find a preview size that matches the provided width and height (which
	 * specify the dimensions of the encoded video).  If it fails to find a match it just
	 * uses the default preview size for video.
	 */
	public static void choosePreviewSize(Camera.Parameters parms, int width, int height) {
		// We should make sure that the requested MPEG size is less than the preferred
		// size, and has the same aspect ratio.
		Size ppsfv = parms.getPreferredPreviewSizeForVideo();
		if (ppsfv != null) {
			LogUtils.d(TAG, "Camera preferred preview size for video is " +
					ppsfv.width + "x" + ppsfv.height);
		}

		for (Size size : parms.getSupportedPreviewSizes()) {
			LogUtils.d(TAG, "supported: " + size.width + "x" + size.height);
		}

		for (Size size : parms.getSupportedPreviewSizes()) {
			if (size.width == width && size.height == height) {
				parms.setPreviewSize(width, height);
				return;
			}
		}

		LogUtils.w(TAG, "Unable to set preview size to " + width + "x" + height);
		if (ppsfv != null) {
			parms.setPreviewSize(ppsfv.width, ppsfv.height);
		}
		// else use whatever the default size is
	}

	/**
	 * Attempts to find a fixed preview frame rate that matches the desired frame rate.
	 * @return The expected frame rate, in thousands of frames per second.
	 */
	public static void chooseFixedPreviewFps(Camera.Parameters parms, int desiredThousandFps) {
		List<int[]> supported = parms.getSupportedPreviewFpsRange();

		for (int[] entry : supported) {
			if ((entry[0] == entry[1]) && (entry[0] == desiredThousandFps)) {
				parms.setPreviewFpsRange(entry[0], entry[1]);
				return;
			}
		}

		for (int[] entry : supported) {
			if ((entry[0] == entry[1])) {
				parms.setPreviewFpsRange(entry[0], entry[1]);
				return;
			}
		}


        //选择最小的FPS
        int[] tmp = {Integer.MAX_VALUE, Integer.MAX_VALUE};
        for (int[] entry : supported) {
            if ((entry[0] < tmp[0])) {
                tmp = entry;
            }
        }
        if(tmp[0] != Integer.MAX_VALUE && tmp[1] != Integer.MAX_VALUE){
            parms.setPreviewFpsRange(tmp[0], tmp[1]);
        }
	}
	
	public static void choosePictureSize(Camera.Parameters parms, int width, int height){
		for (Size size : parms.getSupportedPictureSizes()){
//			Log.i(TAG, "Picture size width = " + size.width + " height = " + size.height);
			if (size.width == width && size.height == height) {
				parms.setPictureSize(width, height);
				return;
			}
		}
	}
}
