package com.yxd.live.recording.camera.gles.filter;

import android.opengl.GLES20;

import com.yxd.live.recording.camera.gles.GlUtil;

public class BeauityFilter extends GLFilter {
    private static final String BEAUITY_VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            "varying vec2 textureCoordinate2;\n" +
            "varying vec2 textureCoordinate3;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    textureCoordinate = (uTexMatrix * aTextureCoord).xy;\n" +
            "    textureCoordinate2 = textureCoordinate;\n" +
            "    textureCoordinate3 = textureCoordinate;\n" +
            "}\n";

    private static final String BEAUITY_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            " varying vec2 textureCoordinate;\n" +
            " varying vec2 textureCoordinate2;\n" +
            " varying vec2 textureCoordinate3;\n" +
            " \n" +
            " uniform samplerExternalOES inputImageTexture;\n" +
            " uniform sampler2D inputImageTexture2;\n" +
            " uniform sampler2D inputImageTexture3;\n" +
            " const float smoothDegree = 1.0;\n" +
            " const highp vec3 W = vec3(0.2125, 0.7154, 0.0721);\n" +
            " \n" +
            " void main()\n" +
            " {\n" +
            "     vec4 origin = texture2D(inputImageTexture, textureCoordinate);\n" +
            "     vec4 canny = texture2D(inputImageTexture2, textureCoordinate2);\n" +
            "     vec4 bilateral = texture2D(inputImageTexture3,textureCoordinate3);\n" +
            "     vec4 smooth;\n" +
            "     lowp float r = origin.r;\n" +
            "     lowp float g = origin.g;\n" +
            "     lowp float b = origin.b;\n" +
            "     if (canny.r < 0.2) {\n" +
            "         smooth = bilateral;\n" +
            "     }\n" +
            "     else {\n" +
            "         smooth = origin;\n" +
            "     }\n" +
            "     smooth.r = log(1.0 + 0.2 * smooth.r)/log(1.2);\n" +
            "     smooth.g = log(1.0 + 0.2 * smooth.g)/log(1.2);\n" +
            "     smooth.b = log(1.0 + 0.2 * smooth.b)/log(1.2);\n" +
            "     if (smooth.r > smooth.b){\n" +
            "         smooth.r = smooth.r * 1.1;\n" +
            "     }\n" +
            "     else {\n" +
            "         smooth.r = smooth.r * 1.05;\n" +
            "     }\n" +
            "     smooth.g = smooth.g * 1.05;\n"+
            "     smooth.b = smooth.b * 1.04;\n"+
            "     gl_FragColor = smooth;\n" +
            " }\n";
    private int txtureUniform2,txtureUniform3;
    private int texture2, texture3;
    public BeauityFilter() {
        super(BEAUITY_VERTEX_SHADER, BEAUITY_FRAGMENT_SHADER);
        txtureUniform2 = GLES20.glGetUniformLocation(getProgramHandle(), "inputImageTexture2");
        GlUtil.checkLocation(txtureUniform2, "txtureUniform2");
        txtureUniform3 = GLES20.glGetUniformLocation(getProgramHandle(), "inputImageTexture3");
        GlUtil.checkLocation(txtureUniform3, "txtureUniform3");
    }

    public void setTexture(int texture2, int texture3) {
        this.texture3 = texture3;
        this.texture2 = texture2;
    }

    @Override
    protected void onPreDraw() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture2);
        GLES20.glUniform1i(txtureUniform2, 2);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture3);
        GLES20.glUniform1i(txtureUniform3, 3);
    }
}
