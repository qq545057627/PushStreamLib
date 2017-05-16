package com.yxd.live.recording.media;

import com.yxd.live.recording.interfaces.IMediaRecorder;

public abstract class AbstractMediaRecoder implements IMediaRecorder {

	private OnPreparedListener mOnPreparedListener;
	
	private OnStopListener mOnStopListener;

	private OnErrorListener mOnErrorListener;

	private OnInfoListener mOnInfoListener;

	public final void setOnPreparedListener(OnPreparedListener listener) {
		mOnPreparedListener = listener;
	}
	
	public final void setOnStopListener(OnStopListener listener){
		mOnStopListener = listener;
	}
	
	public final void setOnErrorListener(OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    public final void setOnInfoListener(OnInfoListener listener) {
        mOnInfoListener = listener;
    }
    
    public void resetListeners() {
        mOnPreparedListener = null;
        mOnStopListener = null;
        mOnErrorListener = null;
        mOnInfoListener = null;
    }

    protected final boolean notifyOnPrepared(int what, int extra) {
        if (mOnPreparedListener != null) {
           return mOnPreparedListener.onPrepared(this, what, extra);
        }
        return false;
    }
    
    protected final void notifyOnStop() {
        if (mOnStopListener != null)
        	mOnStopListener.onStop(this);
    }
    
    protected final boolean notifyOnError(int what, int extra) {
    	if (mOnErrorListener != null) {
			return mOnErrorListener.onError(this, what, extra);
		}
        return false;
    }

    protected final boolean notifyOnInfo(int what, int extra) {
    	if (mOnInfoListener != null) {
			return mOnInfoListener.onInfo(this, what, extra);
		}
        return false;
    }
}
