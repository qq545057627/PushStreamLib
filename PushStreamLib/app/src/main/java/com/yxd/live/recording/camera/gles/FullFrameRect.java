package com.yxd.live.recording.camera.gles;


import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.yxd.live.recording.camera.gles.filter.BeauityFilter;
import com.yxd.live.recording.camera.gles.filter.BilateralFilter;
import com.yxd.live.recording.camera.gles.filter.GrayScaleFilter;
import com.yxd.live.recording.camera.gles.filter.SobelEdgeFilter;

/**
 * This class essentially represents a viewport-sized sprite that will be rendered with
 * a texture, usually from an external source like the camera or video decoder.
 */
public class FullFrameRect {
	private final Drawable2d mRectDrawable = new Drawable2d(Drawable2d.Prefab.FULL_RECTANGLE);
    private Texture2dProgram mProgram;
	/**
	 * Releases resources.
	 * <p>
	 * This must be called with the appropriate EGL context current (i.e. the one that was
	 * current when the constructor was called).  If we're about to destroy the EGL context,
	 * there's no value in having the caller make it current just to do this cleanup, so you
	 * can pass a flag that will tell this function to skip any EGL-context-specific cleanup.
	 */
    private boolean isReleased = false;
	public void release() {
        isReleased = true;
        grayScaleFilter.release();
        sobelEdgeFilter.release();
        bilateralFilter.release();
        beauityFilter.release();
        mProgram.release();

        GLES20.glDeleteTextures(mFrameBufferTextures.length, mFrameBufferTextures, 0);
        GLES20.glDeleteFramebuffers(mFrameBuffers.length, mFrameBuffers, 0);
	}

    private boolean isNeedFilter = true;
    public boolean switchFilter(){
        if(isNeedFilter){
            isNeedFilter = false;
        }else{
            isNeedFilter = true;
        }
        return isNeedFilter;
    }

	public void closeFilter(){
        isNeedFilter = false;
	}

	/**
	 * Draws a viewport-filling rect, texturing it with the specified texture object.
	 */
	public void drawFrame(int textureId, float[] texMatrix) {
        if(isReleased){
            return;
        }

        if(isNeedFilter){
            int previousTexture = textureId;
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
            grayScaleFilter.draw(GlUtil.IDENTITY_MATRIX, mRectDrawable.getVertexArray(), 0,
                    mRectDrawable.getVertexCount(), mRectDrawable.getCoordsPerVertex(),
                    mRectDrawable.getVertexStride(),
                    matrix, mRectDrawable.getTexCoordArray(), previousTexture,
                    mRectDrawable.getTexCoordStride());

            previousTexture = mFrameBufferTextures[0];
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[1]);
            sobelEdgeFilter.draw(GlUtil.IDENTITY_MATRIX, mRectDrawable.getVertexArray(), 0,
                    mRectDrawable.getVertexCount(), mRectDrawable.getCoordsPerVertex(),
                    mRectDrawable.getVertexStride(),
                    matrix, mRectDrawable.getTexCoordArray(), previousTexture,
                    mRectDrawable.getTexCoordStride());

            previousTexture = textureId;
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[2]);
            bilateralFilter.draw(GlUtil.IDENTITY_MATRIX, mRectDrawable.getVertexArray(), 0,
                    mRectDrawable.getVertexCount(), mRectDrawable.getCoordsPerVertex(),
                    mRectDrawable.getVertexStride(),
                    matrix, mRectDrawable.getTexCoordArray(), previousTexture,
                    mRectDrawable.getTexCoordStride());

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            beauityFilter.setTexture(mFrameBufferTextures[1], mFrameBufferTextures[2]);
            beauityFilter.draw(GlUtil.IDENTITY_MATRIX, mRectDrawable.getVertexArray(), 0,
                    mRectDrawable.getVertexCount(), mRectDrawable.getCoordsPerVertex(),
                    mRectDrawable.getVertexStride(),
                    texMatrix, mRectDrawable.getTexCoordArray(), textureId,
                    mRectDrawable.getTexCoordStride());
        }else{
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            mProgram.draw(GlUtil.IDENTITY_MATRIX, mRectDrawable.getVertexArray(), 0,
                    mRectDrawable.getVertexCount(), mRectDrawable.getCoordsPerVertex(),
                    mRectDrawable.getVertexStride(),
                    texMatrix, mRectDrawable.getTexCoordArray(), textureId,
                    mRectDrawable.getTexCoordStride());
        }
	}

	private GrayScaleFilter grayScaleFilter;
	private SobelEdgeFilter sobelEdgeFilter;
	private BilateralFilter bilateralFilter;
	private BeauityFilter beauityFilter;
	private int[] mFrameBuffers;
	private int[] mFrameBufferTextures;

	/**The 4x4 projection matrix.**/
	private static final float[] matrix = new float[] {
			1.0f, 0.0f, 0.0f, 0.0f,
			0.0f, 1.0f, 0.0f, 0.0f,
			0.0f, 0.0f, 1.0f, 0.0f,
			0.0f, 0.0f, 0.0f, 1.0f
	};

	public FullFrameRect(int width, int height) {
		grayScaleFilter = new GrayScaleFilter();
		sobelEdgeFilter = new SobelEdgeFilter();
		bilateralFilter = new BilateralFilter();
		beauityFilter = new BeauityFilter();
        mProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
        setDistanceNormalizationFactor(3.0f);
        setStepOffset(0.005f);
        setTexelSize(width, height);

		mFrameBuffers = new int[3];
		mFrameBufferTextures = new int[3];

		for (int i = 0; i < 3; i++) {
			GLES20.glGenFramebuffers(1, mFrameBuffers, i);
			GlUtil.checkGlError("glGenFramebuffers");
			GLES20.glGenTextures(1, mFrameBufferTextures, i);
			GlUtil.checkGlError("glGenTextures");
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFrameBufferTextures[i]);
			GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
					GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
					GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
					GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
					GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
					GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[i]);
			GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
					GLES20.GL_TEXTURE_2D, mFrameBufferTextures[i], 0);

			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
		}
	}

    /**
     * Creates a texture object suitable for use with this program.
     */
    public static int createTextureObject() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GlUtil.checkGlError("glGenTextures");

        int texId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        GlUtil.checkGlError("glBindTexture " + texId);

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");

        return texId;
    }


    public void setDistanceNormalizationFactor(final float newValue) {
		bilateralFilter.setDistanceNormalizationFactor(newValue);
	}

	public void setStepOffset(float stepOffset){
		bilateralFilter.setStepOffset(stepOffset);
	}

	public void setTexelSize(final float w, final float h) {
        sobelEdgeFilter.setTexelSize(1/w, 1/w);
	}
}
