package com.yxd.live.recording.camera.gles.filter;

import android.opengl.GLES20;

public class SobelEdgeFilter extends GLFilter {
    private static final String SOBEL_EDGE_VERTEX_SHADER =
            "precision mediump float;\n" +
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "\n" +
            "uniform highp float texelWidth; \n" +
            "uniform highp float texelHeight; \n" +
            "\n" +
            "varying vec2 textureCoordinate;\n" +
            "varying vec2 leftTextureCoordinate;\n" +
            "varying vec2 rightTextureCoordinate;\n" +
            "\n" +
            "varying vec2 topTextureCoordinate;\n" +
            "varying vec2 topLeftTextureCoordinate;\n" +
            "varying vec2 topRightTextureCoordinate;\n" +
            "\n" +
            "varying vec2 bottomTextureCoordinate;\n" +
            "varying vec2 bottomLeftTextureCoordinate;\n" +
            "varying vec2 bottomRightTextureCoordinate;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    textureCoordinate = (uTexMatrix * aTextureCoord).xy;\n" +
            "\n" +
            "    vec2 widthStep = vec2(texelWidth, 0.0);\n" +
            "    vec2 heightStep = vec2(0.0, texelHeight);\n" +
            "    vec2 widthHeightStep = vec2(texelWidth, texelHeight);\n" +
            "    vec2 widthNegativeHeightStep = vec2(texelWidth, -texelHeight);\n" +
            "\n" +
            "    textureCoordinate = textureCoordinate.xy;\n" +
            "    leftTextureCoordinate = textureCoordinate.xy - widthStep;\n" +
            "    rightTextureCoordinate = textureCoordinate.xy + widthStep;\n" +
            "\n" +
            "    topTextureCoordinate = textureCoordinate.xy - heightStep;\n" +
            "    topLeftTextureCoordinate = textureCoordinate.xy - widthHeightStep;\n" +
            "    topRightTextureCoordinate = textureCoordinate.xy + widthNegativeHeightStep;\n" +
            "\n" +
            "    bottomTextureCoordinate = textureCoordinate.xy + heightStep;\n" +
            "    bottomLeftTextureCoordinate = textureCoordinate.xy - widthNegativeHeightStep;\n" +
            "    bottomRightTextureCoordinate = textureCoordinate.xy + widthHeightStep;\n" +
            "}";


    private static final String SOBEL_EDGE_DETECTION = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "\n" +
            "varying vec2 textureCoordinate;\n" +
            "varying vec2 leftTextureCoordinate;\n" +
            "varying vec2 rightTextureCoordinate;\n" +
            "\n" +
            "varying vec2 topTextureCoordinate;\n" +
            "varying vec2 topLeftTextureCoordinate;\n" +
            "varying vec2 topRightTextureCoordinate;\n" +
            "\n" +
            "varying vec2 bottomTextureCoordinate;\n" +
            "varying vec2 bottomLeftTextureCoordinate;\n" +
            "varying vec2 bottomRightTextureCoordinate;\n" +
            "\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "void main()\n" +
            "{\n" +
            "    float bottomLeftIntensity = texture2D(inputImageTexture, bottomLeftTextureCoordinate).r;\n" +
            "    float topRightIntensity = texture2D(inputImageTexture, topRightTextureCoordinate).r;\n" +
            "    float topLeftIntensity = texture2D(inputImageTexture, topLeftTextureCoordinate).r;\n" +
            "    float bottomRightIntensity = texture2D(inputImageTexture, bottomRightTextureCoordinate).r;\n" +
            "    float leftIntensity = texture2D(inputImageTexture, leftTextureCoordinate).r;\n" +
            "    float rightIntensity = texture2D(inputImageTexture, rightTextureCoordinate).r;\n" +
            "    float bottomIntensity = texture2D(inputImageTexture, bottomTextureCoordinate).r;\n" +
            "    float topIntensity = texture2D(inputImageTexture, topTextureCoordinate).r;\n" +
            "    float h = -topLeftIntensity - 2.0 * topIntensity - topRightIntensity + bottomLeftIntensity + 2.0 * bottomIntensity + bottomRightIntensity;\n" +
            "    float v = -bottomLeftIntensity - 2.0 * leftIntensity - topLeftIntensity + bottomRightIntensity + 2.0 * rightIntensity + topRightIntensity;\n" +
            "\n" +
            "    float mag = length(vec2(h, v));\n" +
            "\n" +
            "    gl_FragColor = vec4(vec3(mag), 1.0);\n" +
            "}";
    private int mUniformTexelWidthLocation;
    private int mUniformTexelHeightLocation;
    private float mTexelWidth = 0.008f;
    private float mTexelHeight = 0.008f;
    public SobelEdgeFilter() {
        super(SOBEL_EDGE_VERTEX_SHADER, SOBEL_EDGE_DETECTION);
        mUniformTexelWidthLocation = GLES20.glGetUniformLocation(getProgramHandle(), "texelWidth");
        mUniformTexelHeightLocation = GLES20.glGetUniformLocation(getProgramHandle(), "texelHeight");

    }

    public void setTexelSize(float mTexelWidth, float mTexelHeight) {
        this.mTexelWidth = mTexelWidth;
        this.mTexelHeight = mTexelHeight;
    }

    @Override
    protected void onPreDraw() {
        setFloat(mUniformTexelWidthLocation, mTexelWidth);
        setFloat(mUniformTexelHeightLocation, mTexelHeight);
    }

    @Override
    protected int getTextureTarget() {
        return GLES20.GL_TEXTURE_2D;
    }
}
