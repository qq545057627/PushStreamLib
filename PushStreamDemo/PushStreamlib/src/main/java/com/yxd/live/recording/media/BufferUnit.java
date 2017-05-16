package com.yxd.live.recording.media;


/*
 *  A simple buffer class that hold on the android mediacodec buffer with buffer info.
 *  
 */
public class BufferUnit {

	private byte [] mData = null;
	private int     mLength = 0;
	private long    mPts = 0;
	private int     mFlags = 0;

	public BufferUnit() {

	}

	public void setData(byte [] data) {

		mData = data;
	}

	public void setLength(int length) {

		mLength = length;
	}

	public void setPts(long pts) {

		mPts = pts;
	}

	public void setFlags(int flags) {

		mFlags = flags;
	}

	public byte [] getData() {

		return mData;
	}

	public int getLength() {

		return mLength;
	}

	public long getPts() {

		return mPts;
	}

	public int getFlags() {

		return mFlags;
	}
}
