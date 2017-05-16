package com.yxd.live.recording.camera.gles.filter;


import android.opengl.GLES20;

public class BilateralFilter extends GLFilter {
    private static final String BILATERAL_VERTEX_SHADER = "" +
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 textureCoordinate;\n" +
            "uniform float stepOffset;\n" +
            "varying vec2 coordinates[4];\n" +
            "void main()\n" +
            "{\n" +
            "   gl_Position = uMVPMatrix * aPosition;\n" +
            "   textureCoordinate = (uTexMatrix * aTextureCoord).xy;\n" +
            "   coordinates[0].x = textureCoordinate.x;\n" +
            "   coordinates[0].y = textureCoordinate.y - stepOffset;\n" +
            "   coordinates[1].x = textureCoordinate.x + stepOffset;\n" +
            "   coordinates[1].y = textureCoordinate.y;\n" +
            "   coordinates[2].x = textureCoordinate.x;\n" +
            "   coordinates[2].y = textureCoordinate.y + stepOffset;\n" +
            "   coordinates[3].x = textureCoordinate.x - stepOffset;\n" +
            "   coordinates[3].y = textureCoordinate.y;\n" +
            "}";

    private static final String BILATERAL_FRAGMENT_SHADER = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 textureCoordinate;\n" +
            "uniform samplerExternalOES inputImageTexture;\n" +

            " varying vec2 coordinates[4];\n" +

            " uniform mediump float distanceNormalizationFactor;\n" +

            " void main()\n" +
            " {\n" +
            "     lowp vec4 centralColor;\n" +
            "     lowp float gaussianWeightTotal;\n" +
            "     lowp vec4 sum;\n" +
            "     lowp vec4 sampleColor;\n" +
            "     lowp float distanceFromCentralColor;\n" +
            "     lowp float gaussianWeight;\n" +
            "     \n" +
            "     centralColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "     gaussianWeightTotal = 0.36;\n" +
            "     sum = centralColor * 0.36;\n" +
            "     \n" +
            "sampleColor = texture2D(inputImageTexture, coordinates[0]);\n" +
            "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);\n" +
            "gaussianWeight = 0.16 * (1.0 - distanceFromCentralColor);\n" +
            "gaussianWeightTotal += gaussianWeight;\n" +
            "sum += sampleColor * gaussianWeight;\n" +
            "\n" +
            "sampleColor = texture2D(inputImageTexture, coordinates[1]);\n" +
            "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);\n" +
            "gaussianWeight = 0.16 * (1.0 - distanceFromCentralColor);\n" +
            "gaussianWeightTotal += gaussianWeight;\n" +
            "sum += sampleColor * gaussianWeight;\n" +
            "\n" +
            "sampleColor = texture2D(inputImageTexture, coordinates[2]);\n" +
            "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);\n" +
            "gaussianWeight = 0.16 * (1.0 - distanceFromCentralColor);\n" +
            "gaussianWeightTotal += gaussianWeight;\n" +
            "sum += sampleColor * gaussianWeight;\n" +
            "\n" +
            "sampleColor = texture2D(inputImageTexture, coordinates[3]);\n" +
            "distanceFromCentralColor = min(distance(centralColor, sampleColor) * distanceNormalizationFactor, 1.0);\n" +
            "gaussianWeight = 0.16 * (1.0 - distanceFromCentralColor);\n" +
            "gaussianWeightTotal += gaussianWeight;\n" +
            "sum += sampleColor * gaussianWeight;\n" +
            "\n" +
            "     gl_FragColor = sum / gaussianWeightTotal;\n" +
            " }";

    private int mDisFactorLocation;
    private int mstepOffsetLocation;
    private float distanceNormalizationFactor = 5.0f;
    private float stepOffset = 0.05f;

    public BilateralFilter() {
        super(BILATERAL_VERTEX_SHADER, BILATERAL_FRAGMENT_SHADER);
        mDisFactorLocation = GLES20.glGetUniformLocation(getProgramHandle(), "distanceNormalizationFactor");
        mstepOffsetLocation = GLES20.glGetUniformLocation(getProgramHandle(), "stepOffset");
    }

    @Override
    protected void onPreDraw() {
        super.onPreDraw();
        setFloat(mDisFactorLocation, distanceNormalizationFactor);
        setFloat(mstepOffsetLocation, stepOffset);
    }

    public void setDistanceNormalizationFactor(float newValue) {
        this.distanceNormalizationFactor = newValue;
    }

    public void setStepOffset(float stepOffset) {
        this.stepOffset = stepOffset;
    }
}
