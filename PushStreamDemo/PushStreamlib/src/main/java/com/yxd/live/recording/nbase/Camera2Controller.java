package com.yxd.live.recording.nbase;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by yutao on 2016/12/28.
 * 相机预览控制
 *
 */


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Controller implements TextureView.SurfaceTextureListener{

    private final String TAG=Camera2Controller.class.getSimpleName();

    private static Camera2Controller camera2Controller;
    public static Camera2Controller getInstance(){
        if(camera2Controller==null){
            camera2Controller=new Camera2Controller();
        }
        return camera2Controller;
    }

    private Camera2Controller(){
        singleThreadPool = Executors.newSingleThreadExecutor();
    }
    //上下文
    private Context context;

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    private int width;
    private int height;
    private ImageView showImg;
    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public ImageView getShowImg() {
        return showImg;
    }

    public void setShowImg(ImageView showImg) {
        this.showImg = showImg;
    }

    public TextureView showTextureView;

    public TextureView getShowTextureView() {
        return showTextureView;
    }

    public void setShowTextureView(TextureView showTextureView) {
        this.showTextureView = showTextureView;
        showTextureView.setSurfaceTextureListener(this);
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static
    {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    // 摄像头ID（通常0代表后置摄像头，1代表前置摄像头）
    private String mCameraId = "1";
    // 定义代表摄像头的成员变量
    private CameraDevice cameraDevice;
    // 预览尺寸
    private Size previewSize;
    private CaptureRequest.Builder previewRequestBuilder;
    // 定义用于预览照片的捕获请求
    private CaptureRequest previewRequest;
    // 定义CameraCaptureSession成员变量
    private CameraCaptureSession captureSession;

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        // 摄像头被打开时激发该方法
        @Override
        public void onOpened(CameraDevice cdevice) {
            // 开始预览
            cameraDevice=cdevice;
            createCameraPreviewSession();
        }
        // 摄像头断开连接时激发该方法
        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        // 打开摄像头出现错误时激发该方法
        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
        }
    };

    // 打开摄像头
    public void openCamera(int width, int height) {
        this.width=width;
        this.height=height;
        setUpCameraOutputs(width, height);
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            // 打开摄像头
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            manager.openCamera(mCameraId, stateCallback,null);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }
    private Handler mHandler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case 1:
                    if(showImg!=null){
                        showImg.setImageBitmap((Bitmap) msg.obj);
                    }
                    break;
            }
        }
    };

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        openCamera(width,height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    private class ImageSaver implements Runnable {
        Image reader;

        public ImageSaver(Image reader) {
            this.reader = reader;
        }

        @Override
        public void run() {
            //Log.e(TAG, "run: =================" );
            final Image.Plane[] planes = reader.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();
            buffer.rewind();
            final byte[] data = new byte[buffer.capacity()];
            buffer.get(data);
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if(bitmap!=null){
                Message msg=Message.obtain();
                msg.what=1;
                msg.obj=bitmap;
                mHandler.sendMessage(msg);
            }
            if(reader!=null)
            reader.close();
        }
    }
    private ExecutorService singleThreadPool;
    private ImageReader.OnImageAvailableListener onImageAvaiableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            //Log.e(TAG, "onImageAvailable: ================" );
            singleThreadPool.execute(new ImageSaver(imageReader.acquireLatestImage()));
        }
    };

    private void createCameraPreviewSession()
    {
        try
        {
            // 创建作为预览的CaptureRequest.Builder
            previewRequestBuilder = cameraDevice
                    .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 设置自动对焦模式
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 设置自动曝光模式
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);

            // 将textureView的surface作为CaptureRequest.Builder的目标
            //Surface tSurface=new Surface(gSurfaceTexture);
            //previewRequestBuilder.addTarget(drawSurface);
            // 获取设备方向
            int rotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            // 根据设备方向计算设置照片的方向
            previewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            if(previewSize==null){
                previewSize=new Size(getWidth(),getHeight());
            }
            previewSize=new Size(150,150);
            Log.e(TAG, "createCameraPreviewSession: "+previewSize.getWidth()+  "height:"+previewSize.getHeight());
            //ImageReader mImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 2);
           // mImageReader.setOnImageAvailableListener(onImageAvaiableListener,null);
            //previewRequestBuilder.addTarget(mImageReader.getSurface());
            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
            Log.e(TAG, "createCameraPreviewSession: 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求" );
            if(showTextureView==null||showTextureView.getSurfaceTexture()==null){
                return;
            }
            Surface drawSurface=new Surface(showTextureView.getSurfaceTexture());

            previewRequestBuilder.addTarget(drawSurface);
            cameraDevice.createCaptureSession(Arrays.asList(drawSurface), new CameraCaptureSession.StateCallback()
                    {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession)
                        {
                            // 如果摄像头为null，直接结束方法
                            if (null == cameraDevice)
                            {
                                return;
                            }
                            Log.e(TAG, "onConfigured:当摄像头已经准备好时，开始显示预览 ");
                            // 当摄像头已经准备好时，开始显示预览
                            captureSession = cameraCaptureSession;
                            try
                            {
                                // 开始显示相机预览
                                previewRequest = previewRequestBuilder.build();
                                // 设置预览时连续捕获图像数据
                                captureSession.setRepeatingRequest(previewRequest,
                                        null, null);
                            }
                            catch (CameraAccessException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession)
                        {
                            Log.e(TAG, "onConfigureFailed: =====" );
                        }
                    }, null
            );
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }


    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                               TotalCaptureResult result) {
                    //Log.e(TAG, "onCaptureCompleted: ===================" );
                    checkState(result);
                }

                @Override
                public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                                CaptureResult partialResult) {
                    checkState(partialResult);
                }

                private void checkState(CaptureResult result) {

                }

            };

    private void setUpCameraOutputs(int width, int height)
    {
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try
        {
            // 获取指定摄像头的特性
            CameraCharacteristics characteristics
                    = manager.getCameraCharacteristics(mCameraId);
            // 获取摄像头支持的配置属性
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // 获取摄像头支持的最大尺寸
            Size largest = Collections.max(
                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());
            // 获取最佳的预览尺寸
            previewSize = chooseOptimalSize(map.getOutputSizes(
                    SurfaceTexture.class), width, height, largest);
           /* List<Size> list=Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888));
            previewSize=list.get(list.size()-1);*/
            Log.e(TAG,map.getOutputSizes(ImageFormat.JPEG)+ "setUpCameraOutputs: "+previewSize.getWidth()+"  he"+previewSize.getHeight());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio)
    {
        // 收集摄像头支持的大过预览Surface的分辨率
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices)
        {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height)
            {
                bigEnough.add(option);
            }
        }
        // 如果找到多个预览尺寸，获取其中面积最小的
        return choices[0];
    }
    // 为Size定义一个比较器Comparator
    static class CompareSizesByArea implements Comparator<Size>
    {
        @Override
        public int compare(Size lhs, Size rhs)
        {
            // 强转为long保证不会发生溢出
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
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
                height, Bitmap.Config.RGB_565);
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
        return output;
    }
}
