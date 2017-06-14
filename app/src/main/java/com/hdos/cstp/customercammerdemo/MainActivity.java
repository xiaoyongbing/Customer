package com.hdos.cstp.customercammerdemo;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {
    private Context context;//上下文
    private Button btnTakePhoto;//拍照
    private Button btnChoosenPhoto;//选择照片
    private ImageView imgPrevice;//预览图片

    public int screenWidth = 0;
    public int screenHeight = 0;

    /** 截取结束标志 */
    private static final int FLAG_MODIFY_FINISH = 3;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        initView();
        initEvent();
    }

    private void initEvent() {
        //拍照
        btnTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context,TakePhotoActivity.class);
                startActivityForResult(intent,100);
            }
        });
        //选择图片
        btnChoosenPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(
                        Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent,
                        1001);
            }
        });
    }

    private void initView() {
        btnTakePhoto = (Button) findViewById(R.id.take_phone);
        btnChoosenPhoto = (Button) findViewById(R.id.chooesn_phone);
        imgPrevice = (ImageView) findViewById(R.id.img_preivew_view);
        
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case 100:
                if (data != null) {
                    //获得图片上传
                    String filePath = data.getStringExtra("tempFile");
                    Toast.makeText(context,filePath,Toast.LENGTH_LONG).show();
                    //uploadFile(filePath);
                    getWindowWH();
                    Bitmap bitmap = createBitmap(filePath, screenWidth, screenHeight);
                    imgPrevice.setImageBitmap(bitmap);
                }
                break;
            case 1001:
                //外界的程序访问ContentProvider所提供数据 可以通过ContentResolver接口
                ContentResolver resolver = getContentResolver();
                if (data != null) {
                    Uri uri = data.getData();
                    if (uri.getAuthority()!=null && !"".equals(uri.getAuthority())){
                        Bitmap bitMap = null;
                        try {
                            if (bitMap != null) bitMap.recycle();
                            // bitMap = null;
                            // bitMap = MediaStore.Images.Media.getBitmap(resolver,
                            //       uri);
                            String[] proj = {MediaStore.MediaColumns.DATA};
                            Cursor cursor = managedQuery(uri, proj, null, null,
                                    null);
                            // 按我个人理解 这个是获得用户选择的图片的索引值
                            int column_index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                            // 将光标移至开头 ，这个很重要，不小心很容易引起越界
                            cursor.moveToFirst();

                            // 最后根据索引值获取图片路径
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            String filePath = cursor.getString(column_index);
                            Intent intent = new Intent(this, CutPictureAty.class);
                            intent.putExtra("path", filePath);
                            startActivityForResult(intent, FLAG_MODIFY_FINISH);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }else{
                        Intent intent = new Intent(this, CutPictureAty.class);
                        intent.putExtra("path", uri.getPath());
                        startActivityForResult(intent, FLAG_MODIFY_FINISH);
                    }
                    //uploadFile(filePath);
                } else {
                    System.out.println("CHOOSE_SMALL_PICTURE: data = " + data);
                }
                break;
            case FLAG_MODIFY_FINISH:
                if(resultCode == RESULT_OK){
                    if (data != null) {
                        final String path = data.getStringExtra("path");
                        Bitmap bitmap = createBitmap(path, screenWidth, screenHeight);
                        imgPrevice.setImageBitmap(bitmap);
                    }
                }
                break;
        }
    }

    public Bitmap createBitmap(String path, int w, int h) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            // 这里是整个方法的关键，inJustDecodeBounds设为true时将不为图片分配内存。
            BitmapFactory.decodeFile(path, opts);
            int srcWidth = opts.outWidth;// 获取图片的原始宽度
            int srcHeight = opts.outHeight;// 获取图片原始高度
            int destWidth = 0;
            int destHeight = 0;
            // 缩放的比例
            double ratio = 0.0;
            if (srcWidth < w || srcHeight < h) {
                ratio = 0.0;
                destWidth = srcWidth;
                destHeight = srcHeight;
            } else if (srcWidth > srcHeight) {// 按比例计算缩放后的图片大小，maxLength是长或宽允许的最大长度
                ratio = (double) srcWidth / w;
                destWidth = w;
                destHeight = (int) (srcHeight / ratio);
            } else {
                ratio = (double) srcHeight / h;
                destHeight = h;
                destWidth = (int) (srcWidth / ratio);
            }
            BitmapFactory.Options newOpts = new BitmapFactory.Options();
            // 缩放的比例，缩放是很难按准备的比例进行缩放的，目前我只发现只能通过inSampleSize来进行缩放，其值表明缩放的倍数，SDK中建议其值是2的指数值
            newOpts.inSampleSize = (int) ratio + 1;
            // inJustDecodeBounds设为false表示把图片读进内存中
            newOpts.inJustDecodeBounds = false;
            // 设置大小，这个一般是不准确的，是以inSampleSize的为准，但是如果不设置却不能缩放
            newOpts.outHeight = destHeight;
            newOpts.outWidth = destWidth;
            // 获取缩放后图片
            return BitmapFactory.decodeFile(path, newOpts);
        } catch (Exception e) {
            // TODO: handle exception
            return null;
        }
    }

    /**
     * 获取屏幕的高和宽
     */
    private void getWindowWH() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
    }
}
