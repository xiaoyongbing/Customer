package com.hdos.cstp.customercammerdemo;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.hdos.cstp.customercammerdemo.camare.CameraPreview;
import com.hdos.cstp.customercammerdemo.camare.FocusView;
import com.hdos.cstp.customercammerdemo.camare.Utils;
import com.hdos.cstp.customercammerdemo.view.CutView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TakePhotoActivity extends AppCompatActivity implements CameraPreview.OnCameraStatusListener,SensorEventListener,View.OnTouchListener {
    private static final String TAG = "TakePhoteActivity";
    public static final Uri IMAGE_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    public static final String PATH = Environment.getExternalStorageDirectory()
            .toString() + "/AndroidMedia/";

    CameraPreview mCameraPreview;//相机类
    private String filename;//文件名
    private ImageView img;//要裁剪的图片
    private FocusView focusView;//设置焦点
    private RelativeLayout mTakePhotoLayout;//照相区域
    private LinearLayout mCropperLayout;//裁剪区域
    private ImageView imgClose;//关闭裁剪
    private ImageView imgSave;//确定
    private CutView cutview;//裁剪框
    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();


    /** 动作标志：无 */
    private static final int NONE = 0;
    /** 动作标志：拖动 */
    private static final int DRAG = 1;
    /** 动作标志：缩放 */
    private static final int ZOOM = 2;
    /** 初始化动作标志 */
    private int mode = NONE;

    /** 记录起始坐标 */
    private PointF start = new PointF();
    /** 记录缩放时两指中间点坐标 */
    private PointF mid = new PointF();
    private float oldDist = 1f;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_take_photo);

        initView();
        initData();
        initEvent();




    }

    /**
     * 初始化截图区域，并将源图按裁剪框比例缩放
     *
     * @param top
     * @param bitmap
     */
    private void initcutView(int top, final Bitmap bitmap) {
        // bitmap = BitmapFactory.decodeResource(this.getResources(),
        // R.drawable.a1111111);
        cutview = new CutView(TakePhotoActivity.this);
        cutview.setCustomTopBarHeight(top);
        cutview.addOnDrawCompleteListener(new CutView.OnDrawListenerComplete() {

            public void onDrawCompelete() {
                cutview.removeOnDrawCompleteListener();
                int cutHeight = cutview.getCutHeight();
                int cutWidth = cutview.getCutWidth() + 50;
                int midX = cutview.getCutLeftMargin() + (cutWidth / 2);
                int midY = cutview.getCutTopMargin() + (cutHeight / 2);

                int imageWidth = bitmap.getWidth();
                int imageHeight = bitmap.getHeight();

                // 按裁剪框求缩放比例
                float scale = (cutWidth * 1.0f) / imageWidth;
                if (imageWidth > imageHeight) {
                    scale = (cutHeight * 1.0f) / imageHeight;
                }
                // 起始中心点
                float imageMidX = imageWidth / 2;
                float imageMidY = cutview.getCustomTopBarHeight()
                        + imageHeight * scale / 2;
                img.setScaleType(ImageView.ScaleType.MATRIX);

                // 缩放
                matrix.postScale(scale, scale);
                // 平移
                matrix.postTranslate(0, midY - imageMidY);

                img.setImageMatrix(matrix);
                img.setImageBitmap(bitmap);
            }
        });

        this.addContentView(cutview, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void initEvent() {
        mCameraPreview.setFocusView(focusView);
        mCameraPreview.setOnCameraStatusListener(this);

        /**
         * 保存裁剪
         */
        imgSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getBitmap();
                Intent intent = new Intent();
                intent.putExtra("tempFile", PATH+filename);
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        });

        img.setOnTouchListener(this);

    }


    /**
     * 获取裁剪框内截图
     *
     * @return
     */
    private Bitmap getBitmap() {

        // 获取截屏
        View view = this.getWindow().getDecorView();
        view.setDrawingCacheEnabled(true);
        view.buildDrawingCache();

        int contentTop = getWindow().findViewById(Window.ID_ANDROID_CONTENT)
                .getTop();
        Bitmap finalBitmap = Bitmap.createBitmap(view.getDrawingCache(),
                cutview.getCutLeftMargin(), cutview.getCutTopMargin()
                        + contentTop, cutview.getCutWidth(),
                cutview.getCutHeight());
        //判断图片是否大于1M大于的话就压缩
        finalBitmap = ratio(finalBitmap,cutview.getWidth(),cutview.getCutHeight());
        savaBitmap(finalBitmap);
        // 释放资源
        view.destroyDrawingCache();

        return finalBitmap;
    }


    /**
     * 保存bitmap对象到本地
     *
     * @param bitmap
     */
    public void savaBitmap(Bitmap bitmap) {
        File f = new File(PATH+filename);
        FileOutputStream fOut = null;
        try {
            f.createNewFile();
            fOut = new FileOutputStream(f);
        } catch (Exception e) {
            e.printStackTrace();
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fOut);// 把Bitmap对象解析成流
        try {
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Bitmap ratio(Bitmap image, float pixelW, float pixelH) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, os);
        int length = os.toByteArray().length;
        if( os.toByteArray().length / 1024>200) {//判断如果图片大于1M,进行压缩避免在生成图片（BitmapFactory.decodeStream）时溢出
            os.reset();//重置baos即清空baos
            image.compress(Bitmap.CompressFormat.JPEG, 50, os);//这里压缩50%，把压缩后的数据存放到baos中
        }
        ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        //开始读入图片，此时把options.inJustDecodeBounds 设回true了
        newOpts.inJustDecodeBounds = true;
        newOpts.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeStream(is, null, newOpts);
        newOpts.inJustDecodeBounds = false;
        int w = newOpts.outWidth;
        int h = newOpts.outHeight;
        float hh = pixelH;// 设置高度为240f时，可以明显看到图片缩小了
        float ww = pixelW;// 设置宽度为120f，可以明显看到图片缩小了
        //缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
        int be = 1;//be=1表示不缩放
        if (w > h && w > ww) {//如果宽度大的话根据宽度固定大小缩放
            be = (int) (newOpts.outWidth / ww);
        } else if (w < h && h > hh) {//如果高度高的话根据宽度固定大小缩放
            be = (int) (newOpts.outHeight / hh);
        }
        if (be <= 0) be = 1;
        newOpts.inSampleSize = be;//设置缩放比例
        //重新读入图片，注意此时已经把options.inJustDecodeBounds 设回false了
        is = new ByteArrayInputStream(os.toByteArray());
        bitmap = BitmapFactory.decodeStream(is, null, newOpts);
        //压缩好比例大小后再进行质量压缩
//      return compress(bitmap, maxSize); // 这里再进行质量压缩的意义不大，反而耗资源，删除
        return bitmap;
    }

    private void initData() {
        mSensorManager = (SensorManager) getSystemService(Context.
                SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.
                TYPE_ACCELEROMETER);
    }

    private void initView() {
        img = (ImageView) this.findViewById(R.id.cropImageView);
        mCameraPreview = (CameraPreview) findViewById(R.id.cameraPreview);
        focusView = (FocusView) findViewById(R.id.view_focus);
        mTakePhotoLayout = (RelativeLayout) findViewById(R.id.take_photo_layout);
        mCropperLayout = (LinearLayout) findViewById(R.id.cropper_layout);
        imgSave = (ImageView) findViewById(R.id.img_ok_cut);
        imgClose = (ImageView) findViewById(R.id.img_close_cut);
    }

    /**
     * 拍照
     * @param view
     */
    public void takePhoto(View view) {
        if(mCameraPreview != null) {
            mCameraPreview.takePicture();
        }
    }

    public void openlight(View view)
    {
        if (mCameraPreview != null)
        {
            mCameraPreview.openLight();
            view.setVisibility(View.GONE);
            View v = findViewById(R.id.nolight);
            v.setVisibility(View.VISIBLE);
        }
    }
    public void offlight(View v)
    {
        if (mCameraPreview != null)
        {
            mCameraPreview.offLight();
            v.setVisibility(View.GONE);
            View view = findViewById(R.id.light);
            view.setVisibility(View.VISIBLE);
        }
    }
    public void close(View view) {
        finish();
    }


    boolean isRotated = false;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onResume() {
        super.onResume();
        if(!isRotated) {
            //TextView hint_tv = (TextView) findViewById(R.id.hint);
            /*ObjectAnimator animator = ObjectAnimator.ofFloat(hint_tv, "rotation", 0f, 90f);
            animator.setStartDelay(800);
            animator.setDuration(1000);
            animator.setInterpolator(new LinearInterpolator());
            animator.start();*/
            //View view =  findViewById(R.id.crop_hint);
            AnimatorSet animSet = new AnimatorSet();
            //ObjectAnimator animator1 = ObjectAnimator.ofFloat(view, "rotation", 0f, 90f);
            //ObjectAnimator moveIn = ObjectAnimator.ofFloat(view, "translationX", 0f, -50f);
            //animSet.play(animator1).before(moveIn);
            animSet.setDuration(10);
            animSet.start();
            isRotated = true;
        }
        mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //mSensorManager.unregisterListener(this);
    }




    /**
     * 拍照成功后回调
     * 存储图片并显示截图界面
     * @param data
     */
    @Override
    public void onCameraStopped(byte[] data) {
        Log.i("TAG", "==onCameraStopped==");
        // 创建图像
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        bitmap = Utils.rotate(bitmap, -90);
        // 系统时间
        long dateTaken = System.currentTimeMillis();
        // 图像名称
        filename = DateFormat.format("yyyy-MM-dd kk.mm.ss", dateTaken)
                .toString() + ".jpg";
        // 存储图像（PATH目录）
        Uri source = insertImage(getContentResolver(), filename, dateTaken, PATH,
                filename, bitmap, data);
     /*   Intent intent = new Intent();
        intent.putExtra("tempFile",PATH +filename);
        setResult(RESULT_OK, intent);*/
        //裁剪图片
        showCropperLayout(bitmap);
        // bitmap.recycle();
        //finish();
        super.overridePendingTransition(R.anim.fade_in,
                R.anim.fade_out);
        //准备截图

    }

    private void showCropperLayout(final Bitmap bitmap) {
        mTakePhotoLayout.setVisibility(View.GONE);
        mCropperLayout.setVisibility(View.VISIBLE);
       // mCameraPreview.start();   //继续启动摄像头
        ViewTreeObserver observer = img.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            @SuppressWarnings("deprecation")
            public void onGlobalLayout() {
                img.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                initcutView(img.getTop(), bitmap);
            }
        });
    }

    /**
     * 存储图像并将信息添加入媒体数据库
     */
    private Uri insertImage(ContentResolver cr, String name, long dateTaken,
                            String directory, String filename, Bitmap source, byte[] jpegData) {
        OutputStream outputStream = null;
        String filePath = directory + filename;
        try {
            File dir = new File(directory);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(directory, filename);
            if (file.createNewFile()) {
                outputStream = new FileOutputStream(file);
                if (source != null) {
                    source.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                } else {
                    outputStream.write(jpegData);
                }
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage());
            return null;
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return null;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Throwable t) {
                }
            }
        }
        ContentValues values = new ContentValues(7);
        values.put(MediaStore.Images.Media.TITLE, name);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
        values.put(MediaStore.Images.Media.DATE_TAKEN, dateTaken);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATA, filePath);
        return cr.insert(IMAGE_URI, values);
    }

    private float mLastX = 0;
    private float mLastY = 0;
    private float mLastZ = 0;
    private boolean mInitialized = false;
    private SensorManager mSensorManager;
    private Sensor mAccel;
    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        if (!mInitialized){
            mLastX = x;
            mLastY = y;
            mLastZ = z;
            mInitialized = true;
        }
        float deltaX  = Math.abs(mLastX - x);
        float deltaY = Math.abs(mLastY - y);
        float deltaZ = Math.abs(mLastZ - z);

        if(deltaX > 0.8 || deltaY > 0.8 || deltaZ > 0.8){
            mCameraPreview.setFocus();
        }
        mLastX = x;
        mLastY = y;
        mLastZ = z;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        ImageView view = (ImageView) v;
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                // 设置开始点位置
                start.set(event.getX(), event.getY());
                mode = DRAG;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                oldDist = spacing(event);
                if (oldDist > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    mode = ZOOM;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    matrix.set(savedMatrix);
                    matrix.postTranslate(event.getX() - start.x, event.getY()
                            - start.y);
                } else if (mode == ZOOM) {
                    float newDist = spacing(event);
                    if (newDist > 10f) {
                        matrix.set(savedMatrix);
                        float scale = newDist / oldDist;
                        matrix.postScale(scale, scale, mid.x, mid.y);
                    }
                }
                break;
        }
        view.setImageMatrix(matrix);
        return true;
    }

    /**
     * 多点触控时，计算最先放下的两指距离
     *
     * @param event
     * @return
     */
    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * 多点触控时，计算最先放下的两指中心坐标
     *
     * @param point
     * @param event
     */
    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }
}


