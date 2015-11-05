/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zxing.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.zxing.camera.CameraManager;
import com.zxing.decode.DecodeThread;
import com.zxing.utils.BeepManager;
import com.zxing.utils.CaptureActivityHandler;
import com.zxing.utils.CommonUtils;

import com.zxing.utils.InactivityTimer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.kgd.zhen.scanqrdemo.R;
import com.kgd.zhen.util.ImageUtils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Hashtable;


/**
 * This activity opens the camera and does the actual scanning on a background
 * thread. It draws a viewfinder to help the user place the barcode correctly,
 * shows feedback as the image processing is happening, and then overlays the
 * results when a scan is successful.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CaptureActivity extends Activity implements
	SurfaceHolder.Callback, OnClickListener {

    private static final String TAG = CaptureActivity.class.getSimpleName();
    
    private CameraManager cameraManager;
    private CaptureActivityHandler handler;
    private InactivityTimer inactivityTimer;
    private BeepManager beepManager;

    private SurfaceView scanPreview = null;
    private RelativeLayout scanContainer;
    private RelativeLayout scanCropView;
    private ImageView scanLine;
    private ImageView mFlash;
    private Rect mCropRect = null;
    public Context mContext;
    public String scanText = "";
    public String photo_path = "";
    
    public Handler getHandler() {
    	return handler;
    }

    public CameraManager getCameraManager() {
    	return cameraManager;
    }

    private boolean isHasSurface = false;

    @Override
    public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		this.mContext = this;
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_qr_scan);
	
		scanPreview = (SurfaceView) findViewById(R.id.capture_preview);
		scanContainer = (RelativeLayout) findViewById(R.id.capture_container);
		scanCropView = (RelativeLayout) findViewById(R.id.capture_crop_view);
		scanLine = (ImageView) findViewById(R.id.capture_scan_line);
		mFlash = (ImageView) findViewById(R.id.capture_flash);
		mFlash.setOnClickListener(this);
	
		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);
	
		TranslateAnimation animation = new TranslateAnimation(
			Animation.RELATIVE_TO_PARENT, 0.0f,
			Animation.RELATIVE_TO_PARENT, 0.0f,
			Animation.RELATIVE_TO_PARENT, 0.0f,
			Animation.RELATIVE_TO_PARENT, 0.9f);
		animation.setDuration(4500);
		animation.setRepeatCount(-1);
		animation.setRepeatMode(Animation.RESTART);
		scanLine.startAnimation(animation);
    }

    @Override
    protected void onResume() {
		super.onResume();
	
		// CameraManager must be initialized here, not in onCreate(). This is
		// necessary because we don't
		// want to open the camera driver and measure the screen size if we're
		// going to show the help on
		// first launch. That led to bugs where the scanning rectangle was the
		// wrong size and partially
		// off screen.
		cameraManager = new CameraManager(getApplication());
	
		handler = null;
	
		if (isHasSurface) {
		    // The activity was paused but not stopped, so the surface still
		    // exists. Therefore
		    // surfaceCreated() won't be called, so init the camera here.
		    initCamera(scanPreview.getHolder());
		} else {
		    // Install the callback and wait for surfaceCreated() to init the
		    // camera.
		    scanPreview.getHolder().addCallback(this);
		}
	
		inactivityTimer.onResume();
    }

    @Override
    protected void onPause() {
		if (handler != null) {
		    handler.quitSynchronously();
		    handler = null;
		}
		inactivityTimer.onPause();
		beepManager.close();
		cameraManager.closeDriver();
		if (!isHasSurface) {
		    scanPreview.getHolder().removeCallback(this);
		}
		super.onPause();
    }

    @Override
    protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
		    Log.e(TAG,
			    "*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!isHasSurface) {
		    isHasSurface = true;
		    initCamera(holder);
		}
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    	isHasSurface = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
	    int height) {

    }

    /**
     * A valid barcode has been found, so give an indication of success and show
     * the results.
     * 
     * @param rawResult
     *            The contents of the barcode.
     * 
     * @param bundle
     *            The extras
     */
    public void handleDecode(final Result rawResult, Bundle bundle) {
		inactivityTimer.onActivity();
		beepManager.playBeepSoundAndVibrate();
		
		// 通过这种方式可以获取到扫描的图片
	//	bundle.putInt("width", mCropRect.width());
	//	bundle.putInt("height", mCropRect.height());
	//	bundle.putString("result", rawResult.getText());
	//
	//	startActivity(new Intent(CaptureActivity.this, ResultActivity.class)
	//		.putExtras(bundle));
		
		handler.postDelayed(new Runnable() {
	
	            @Override
	            public void run() {
	                handleText(rawResult.getText());
	            }
	        }, 800);
    }
    
    private void handleText(String text) {
    	scanText = text;
        if (CommonUtils.isUrl(text)) {  //url
            showUrlOption(text);
        } else {
            handleOtherText(text); //text
        }
    	handler.post(runToast);
    	this.finish();
    }

    private void showUrlOption(final String url) {
        if (url.contains("scan_login")) {
//            showConfirmLogin(url);
            return;
        }
    }


    private void handleOtherText(final String text) {
    	System.out.println("======handleOtherText:"+text);
        // 判断是否符合基本的json格式
        if (!text.matches("^\\{.*")) {
            showCopyTextOption(text);
        } else {
//            try {
//                BarCode barcode = BarCode.parse(text);
//                int type = barcode.getType();
//                switch (type) {
//                case BarCode.SIGN_IN:// 签到
//                    handleSignIn(barcode);
//                    break;
//                default:
//                    break;
//                }
//            } catch (AppException e) {
//                showCopyTextOption(text);
//            }
        }
    }



    private void showCopyTextOption(final String text) {
    	System.out.println("-------showCopyTextOption---------");
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
		    throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
		    Log.w(TAG,
			    "initCamera() while already open -- late SurfaceView callback?");
		    return;
		}
		try {
		    cameraManager.openDriver(surfaceHolder);
		    // Creating the handler starts the preview, which can also throw a
		    // RuntimeException.
		    if (handler == null) {
			handler = new CaptureActivityHandler(this, cameraManager,
				DecodeThread.ALL_MODE);
		    }
	
		    initCrop();
		} catch (IOException ioe) {
		    Log.w(TAG, ioe);
		    displayFrameworkBugMessageAndExit();
		} catch (RuntimeException e) {
		    // Barcode Scanner has seen crashes in the wild of this variety:
		    // java.?lang.?RuntimeException: Fail to connect to camera service
		    Log.w(TAG, "Unexpected error initializing camera", e);
		    displayFrameworkBugMessageAndExit();
		}
    }

    private void displayFrameworkBugMessageAndExit() {
		// camera error
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage("相机打开出错，请稍后重试");
		builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
	
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
			finish();
		    }
	
		});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
	
		    @Override
		    public void onCancel(DialogInterface dialog) {
			finish();
		    }
		});
		builder.show();
    }

    public void restartPreviewAfterDelay(long delayMS) {
		if (handler != null) {
		    handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
    }

    public Rect getCropRect() {
    	return mCropRect;
    }

    /**
     * 初始化截取的矩形区域
     */
    private void initCrop() {
		int cameraWidth = cameraManager.getCameraResolution().y;
		int cameraHeight = cameraManager.getCameraResolution().x;
	
		/** 获取布局中扫描框的位置信息 */
		int[] location = new int[2];
		scanCropView.getLocationInWindow(location);
	
		int cropLeft = location[0];
		int cropTop = location[1] - getStatusBarHeight();
	
		int cropWidth = scanCropView.getWidth();
		int cropHeight = scanCropView.getHeight();
	
		/** 获取布局容器的宽高 */
		int containerWidth = scanContainer.getWidth();
		int containerHeight = scanContainer.getHeight();
	
		/** 计算最终截取的矩形的左上角顶点x坐标 */
		int x = cropLeft * cameraWidth / containerWidth;
		/** 计算最终截取的矩形的左上角顶点y坐标 */
		int y = cropTop * cameraHeight / containerHeight;
	
		/** 计算最终截取的矩形的宽度 */
		int width = cropWidth * cameraWidth / containerWidth;
		/** 计算最终截取的矩形的高度 */
		int height = cropHeight * cameraHeight / containerHeight;
	
		/** 生成最终的截取的矩形 */
		mCropRect = new Rect(x, y, width + x, height + y);
    }

    private int getStatusBarHeight() {
		try {
		    Class<?> c = Class.forName("com.android.internal.R$dimen");
		    Object obj = c.newInstance();
		    Field field = c.getField("status_bar_height");
		    int x = Integer.parseInt(field.get(obj).toString());
		    return getResources().getDimensionPixelSize(x);
		} catch (Exception e) {
		    e.printStackTrace();
		}
		return 0;
    }

    @Override
    public void onClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()) {
			case R.id.capture_flash:
			    light();
			    break;
		
			default:
			    break;
		}
    }
    
    private boolean flag;
    
    protected void light() {
        if (flag == true) {
            flag = false;
            // 开闪光灯
            cameraManager.openLight();
            mFlash.setBackgroundResource(R.drawable.barcode_torch_on);
        } else {
            flag = true;
            // 关闪光灯
            cameraManager.offLight();
            mFlash.setBackgroundResource(R.drawable.barcode_torch_off);
        }
    }

    public void goback(View v){
    	System.out.println("goback");
    	this.finish();
    }
    
    public void openPhotoAlbum(View v){
    	System.out.println("openPhotoAlbum");
        Intent intent;
        if (Build.VERSION.SDK_INT < 19) {
            intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "选择二维码图片"),
                    ImageUtils.REQUEST_CODE_GETIMAGE_BYSDCARD);
        } else {
            intent = new Intent(Intent.ACTION_PICK, Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "选择二维码图片"),
                    ImageUtils.REQUEST_CODE_GETIMAGE_BYSDCARD);
        }
    }
    
    
	@Override
	protected void onActivityResult(final int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK)
            return;

        new Thread() {
            public String selectedImagePath;

            @Override
            public void run() {
                Bitmap bitmap = null;
                String path = "";
                if (requestCode == ImageUtils.REQUEST_CODE_GETIMAGE_BYSDCARD) {
                    if (data == null)
                        return;
                    Uri selectedImageUri = data.getData();
                    if (selectedImageUri != null) {
                        	selectedImagePath = ImageUtils.getImagePath(selectedImageUri, CaptureActivity.this);
                        	path = selectedImagePath;
                        	bitmap = ImageUtils.getBitmapByPath(path);
                        	if(bitmap ==null){
                                bitmap = ImageUtils.getBitmapByPath(path, null);
                        	}
                        	if(bitmap ==null){
                                bitmap = ImageUtils.loadPicasaImageFromGalley(
                                        selectedImageUri, CaptureActivity.this);
                        	}
                    } else {
                        bitmap = ImageUtils.loadPicasaImageFromGalley(
                                selectedImageUri, CaptureActivity.this);
                    }
                    
                	Result result = scanningImage(bitmap);  
                	// String result = decode(photo_path);  
                	if (result == null) {  
                         Looper.prepare();  
                         Toast.makeText(getApplicationContext(), "图片格式有误", 0)  
                                 .show();  
                         Looper.loop();  
                     } else {  
                         Log.i("123result", result.toString());  
                         // Log.i("123result", result.getText());  
                         // 数据返回  
                         String recode = recode(result.toString());
                         System.out.println("recode="+recode);
                         scanText = recode;
//                         handler.post(runToast);
                     }  
                    
                }
            };
        }.start();
	}

	protected Result scanningImage(Bitmap bitmap) {  
        if (bitmap == null) {  
            return null;  
        }  
        // DecodeHintType 和EncodeHintType  
        Hashtable<DecodeHintType, String> hints = new Hashtable<DecodeHintType, String>();  
        hints.put(DecodeHintType.CHARACTER_SET, "utf-8"); // 设置二维码内容的编码  
  
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);  
        BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));  
        QRCodeReader reader = new QRCodeReader();  
        try {  
            return reader.decode(bitmap1, hints);  
        } catch (NotFoundException e) {  
            e.printStackTrace();  
        } catch (ChecksumException e) {  
            e.printStackTrace();  
        } catch (FormatException e) {  
            e.printStackTrace();  
        }  
        return null;  
    }  
	
	private String recode(String str) {  
        String formart = "";  
  
        try {  
            boolean ISO = Charset.forName("ISO-8859-1").newEncoder()  
                    .canEncode(str);  
            if (ISO) {  
                formart = new String(str.getBytes("ISO-8859-1"), "GB2312");  
                Log.i("1234      ISO8859-1", formart);  
            } else {  
                formart = str;  
                Log.i("1234      stringExtra", str);  
            }  
        } catch (UnsupportedEncodingException e) {  
            // TODO Auto-generated catch block  
            e.printStackTrace();  
        }  
        return formart;  
    }  
	
	Runnable runToast = new Runnable() {
		@Override
		public void run() {
			Toast.makeText(getApplicationContext(), scanText, Toast.LENGTH_LONG).show();
		}
	};
}