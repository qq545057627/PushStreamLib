package com.yxd.live.recording.nbase;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class NTextureCameraView extends  TextureView implements TextureView.SurfaceTextureListener{

    private final String TAG = NTextureCameraView.class.getSimpleName();
    private int mCameraId = 0;
    private Camera mCamera = null;
    private boolean hasCamera = false;
    private boolean previewWhenAvailable = false, isPreviewing = false;
    private ExecutorService singleThreadPool;
    private Surface drawSurface = null;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    synchronized (drawSurface) {
                        if (drawSurface != null) {
                            try {
                                Canvas canvas = drawSurface.lockCanvas(null);
                                Paint paint = new Paint();
                                paint.setColor(Color.WHITE);
                                paint.setAntiAlias(true);
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setStrokeWidth(6);
                                canvas.drawCircle(getWidth() / 2, getWidth() / 2, roundPx, paint);
                                canvas.drawBitmap((Bitmap) msg.obj, offSetX, offSetY, null);
                                drawSurface.unlockCanvasAndPost(canvas);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;
            }
        }
    };
    private SurfaceTexture surfaceTexture;
    private byte[] previewBuffer;

    private int setOrientation(int cameraId) { // 调整摄像头方向
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = -90;
                break;
            case Surface.ROTATION_90:
                degrees = 0;
                break;
            case Surface.ROTATION_180:
                degrees = 90;
                break;
            case Surface.ROTATION_270:
                degrees = 180;
                break;
        }
        int result = degrees;
        return result;
    }

    public NTextureCameraView(Context context) {
        this(context, null);
    }

    public NTextureCameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NTextureCameraView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initThis(context);
    }

    private void initThis(Context context) {
        singleThreadPool = Executors.newSingleThreadExecutor();
        hasCamera = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
        if (hasCamera) {
            super.setSurfaceTextureListener(this);
        }
    }

    public boolean isCameraIdValid(int cameraId) {
        return cameraId < Camera.getNumberOfCameras();
    }

    public Camera getCamera() {
        return mCamera;
    }

    private long lastShowTime = 0;
    private long intervalTime = 60;

    private Camera.PreviewCallback previewCallback=new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame( byte[] data,  Camera camera) {
            camera.addCallbackBuffer(previewBuffer);
            long currentTempTime = System.currentTimeMillis();
            if (currentTempTime - lastShowTime < intervalTime) {
                return;
            }
            lastShowTime = currentTempTime;
            ProcessBitmapThread bitmapThread=new ProcessBitmapThread(data,camera);
            singleThreadPool.execute(bitmapThread);
        }
    };

    /**
     * 图片处理线程
     */
    public class ProcessBitmapThread extends Thread{
        private byte[] data;
        private Camera camera;

        public ProcessBitmapThread(byte[] data,  Camera camera){
            this.data=data;
            this.camera=camera;
        }
        @Override
        public void run() {
            try{
                Camera.Parameters parameters = camera.getParameters();
                int width = parameters.getPreviewSize().width;
                int height = parameters.getPreviewSize().height;
                Bitmap bitmapImage = rawByteArray2RGBABitmap2(data, width, height);
                Bitmap rbitmap = adjustPhotoRotation(toRoundBitmap(bitmapImage), setOrientation(mCameraId));
                Message msg = Message.obtain();
                msg.what = 0;
                msg.obj = rbitmap;
                mHandler.sendMessage(msg);
                //Log.e(TAG, "run: "+bitmap );
                //Bitmap bitmap=getBitmapFromYUV(data,camera);
                //Log.e(TAG, "run: "+bitmap );
               /* ByteArrayOutputStream out=new ByteArrayOutputStream();
                YuvImage yuv = new YuvImage(data, ImageFormat.NV21, width, height, null);
                yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);
                out.close();
                byte[] bytes = out.toByteArray();*/

                //out.flush();
                //out.close();

                /*Bitmap rbitmap = adjustPhotoRotation(toRoundBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length)), setOrientation(mCameraId));
                Message msg = Message.obtain();
                msg.what = 0;
                msg.obj = rbitmap;
                mHandler.sendMessage(msg);*/
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        public Bitmap getBitmapFromYUV(byte[] data,Camera camera)
        {
            final int Width  = camera.getParameters().getPreviewSize().width;
            final int Height = camera.getParameters().getPreviewSize().height;
            YuvImage image = new YuvImage(data, ImageFormat.NV21,Width, Height, null);

            if(image!=null){
                try{
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    image.compressToJpeg(new Rect(0, 0, Width, Height), 80, stream);
                    Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                    stream.close();
                    return bmp;
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            return null;
        }

        public Bitmap rawByteArray2RGBABitmap2(byte[] data, int width, int height) {
            int frameSize = width * height;
            int[] rgba = new int[frameSize];

            for (int i = 0; i < height; i++)
                for (int j = 0; j < width; j++) {
                    int y = (0xff & ((int) data[i * width + j]));
                    int u = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 0]));
                    int v = (0xff & ((int) data[frameSize + (i >> 1) * width + (j & ~1) + 1]));
                    y = y < 16 ? 16 : y;

                    int r = Math.round(1.164f * (y - 16) + 1.596f * (v - 128));
                    int g = Math.round(1.164f * (y - 16) - 0.813f * (v - 128) - 0.391f * (u - 128));
                    int b = Math.round(1.164f * (y - 16) + 2.018f * (u - 128));

                    r = r < 0 ? 0 : (r > 255 ? 255 : r);
                    g = g < 0 ? 0 : (g > 255 ? 255 : g);
                    b = b < 0 ? 0 : (b > 255 ? 255 : b);

                    rgba[i * width + j] = 0xff000000 + (b << 16) + (g << 8) + r;
                }

            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bmp.setPixels(rgba, 0 , width, 0, 0, width, height);
            return bmp;
        }
    }

    public void startPreview() throws IOException {
        if (!hasCamera) {
            throw new IllegalStateException("no camera found on this device");
        }
        Log.d("TextureCameraView", "startPreview: ");
        if (!isAvailable()) {
            previewWhenAvailable = true;
            isPreviewing = false;
            return;
        }
        if (mCamera == null) {
            int cameraCount = 0;
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            cameraCount = Camera.getNumberOfCameras(); // get cameras number
            for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
                Camera.getCameraInfo(camIdx, cameraInfo); // get camerainfo
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) { // 代表摄像头的方位，目前有定义值两个分别为CAMERA_FACING_FRONT前置和CAMERA_FACING_BACK后置
                    try {
                        mCameraId = camIdx;
                        mCamera = Camera.open(camIdx);
                    } catch (Exception e) {
                      /*  EventBus.getDefault().post(new OpenCameraErrorEvent());
                        UMGameAgent.onEvent("openCameraEorr","Exception:" + e.getMessage());
                        PopwindowUtil.getInstance().show(YXDLiveApp.intance, true);
                        PopwindowUtil.getInstance().setTitle("相机打开失败，请检查权限或关闭占用相机应用");
                        PopwindowUtil.getInstance().setColor(PopwindowUtil.ERROR);*/
                        e.printStackTrace();
                    }
                }
            }
            Camera.Size size = findBestPreviewSize();
            if (size != null) {
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setPreviewSize(size.width, size.height);
                mCamera.setParameters(parameters);
            }
            surfaceTexture = new SurfaceTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            if (mCamera != null) {
                mCamera.setPreviewTexture(surfaceTexture);
                int buffersize = size.width * size.height *ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
                previewBuffer = new byte[buffersize];
                mCamera.addCallbackBuffer(previewBuffer);
                mCamera.setPreviewCallbackWithBuffer(previewCallback);
                mCamera.startPreview();
                isPreviewing = true;
            }
        }
    }


    public void stopPreview() {
        try{
            if (mCamera == null) {
                return;
            }
            mCamera.stopPreview();
            isPreviewing = false;
            previewWhenAvailable = false;
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void release() {
        if (mCamera == null) {
            return;
        }
        try{
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.release();
            mCamera = null;
            isPreviewing = false;
            previewWhenAvailable = false;
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private Camera.Size findBestPreviewSize() {
        if (mCamera == null) {
            return null;
        }
        List<Camera.Size> sizeList = mCamera.getParameters().getSupportedPreviewSizes();
        if (sizeList == null || sizeList.isEmpty()) {
            return null;
        }
        int viewWid = getWidth();
        int viewHei = getHeight();
        int viewArea = viewWid * viewHei;
        int length = sizeList.size();
        Camera.Size resultSize = null;
        int deltaArea = 0;
        for (int i = 0; i < length; i++) {
            Camera.Size size = sizeList.get(i);
            int area = size.width * size.height;
            int delta = Math.abs(area - viewArea);
            if (deltaArea == 0 || delta < deltaArea) {
                deltaArea = delta;
                resultSize = size;
            }
        }
        return resultSize;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        drawSurface = new Surface(surface);
        if (previewWhenAvailable && !isPreviewing) {
            try {
                startPreview();
            } catch (Exception e) {
                //UMGameAgent.onEvent("openCameraEorr","startPreview:" + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        stopPreview();
        try {
            startPreview();
        } catch (IOException e) {
            //UMGameAgent.onEvent("openCameraEorr","startPreview:" + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mCamera != null) {
            stopPreview();
            release();
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mCamera != null) {
            stopPreview();
            release();
        }
    }


    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }


    @Override
    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        /*super.setSurfaceTexture(surfaceTexture);*/
    }

    @Override
    public void setTransform(Matrix transform) {
        /*super.setTransform(transform);*/
    }


    private float offSetX = 0;
    private float offSetY = 0;
    private float roundPx = 0;

    public Bitmap toRoundBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float left, top, right, bottom, dst_left, dst_top, dst_right, dst_bottom;
        if (width <= height) {
            roundPx = width / 2;
            float clipX = (height - width) / 2;
            top = clipX;
            bottom = height - clipX;
            left = 0;
            right = width;
            height = width;
            dst_left = 0;
            dst_top = 0;
            dst_right = width;
            dst_bottom = width;
        } else {
            roundPx = height / 2;
            float clipY = (width - height) / 2;
            left = clipY;
            right = width - clipY;
            top = 0;
            bottom = height;
            width = height;
            dst_left = 0;
            dst_top = 0;
            dst_right = height;
            dst_bottom = height;
        }
        offSetX = (getWidth() - (2 * roundPx)) / 2;
        offSetY = (getHeight() - (2 * roundPx)) / 2;
        Bitmap output = Bitmap.createBitmap(width,
                height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect src = new Rect((int) left, (int) top, (int) right, (int) bottom);
        final Rect dst = new Rect((int) dst_left, (int) dst_top, (int) dst_right, (int) dst_bottom);
        final RectF rectF = new RectF(dst);
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, src, dst, paint);
        bitmap.recycle();
        return output;
    }

    Bitmap adjustPhotoRotation(Bitmap bm, final int orientationDegree) {
        Matrix m = new Matrix();
        m.setRotate(orientationDegree, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
        try {
            Bitmap bm1 = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            bm.recycle();
            return bm1;
        } catch (OutOfMemoryError ex) {
        }
        return null;
    }
}