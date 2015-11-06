package com.kgd.zhen.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Environment;
import android.view.Gravity;
import android.view.View.MeasureSpec;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;

public class QRCodeUtil {

	/**
	 * 生成二维码图片并保存至filePath
	 * @param content	二维码内容
	 * @param widthPix	图片的宽度
	 * @param heightPix	图片的高度
	 * @param logoBm	二维码中间的logo
	 * @param filePath	生成图片的保存路径(含文件名)
	 * @return boolean  生成成功返回true, 否则返回false
	 */
	public static boolean createQRImage(String content, int widthPix, int heightPix, Bitmap logoBm, String filePath) {
        try {
            if (content == null || "".equals(content)) {
                return false;
            }
 
            //配置参数
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
            //容错级别
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            //设置空白边距的宽度
//            hints.put(EncodeHintType.MARGIN, 2); //default is 4
 
            // 图像数据转换，使用了矩阵转换
            BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, widthPix, heightPix, hints);
            int[] pixels = new int[widthPix * heightPix];
            // 下面这里按照二维码的算法，逐个生成二维码的图片，
            // 两个for循环是图片横列扫描的结果
            for (int y = 0; y < heightPix; y++) {
                for (int x = 0; x < widthPix; x++) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * widthPix + x] = 0xff000000;
                    } else {
                        pixels[y * widthPix + x] = 0xffffffff;
                    }
                }
            }
 
            // 生成二维码图片的格式，使用ARGB_8888
            Bitmap bitmap = Bitmap.createBitmap(widthPix, heightPix, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, widthPix, 0, 0, widthPix, heightPix);
 
            if (logoBm != null) {
                bitmap = addLogo(bitmap, logoBm);
            }
 
            //必须使用compress方法将bitmap保存到文件中再进行读取。直接返回的bitmap是没有任何压缩的，内存消耗巨大！
            return bitmap != null && bitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(filePath));
        } catch (WriterException | IOException e) {
            e.printStackTrace();
        }
        return false;
    }
	
	/**
	 * 生成二维码
	 * @param content	二维码内容
	 * @param widthPix	二维码宽度
	 * @param heightPix 二维码高度
	 * @param logoBm	二维码中心logo
	 * @return bitmap   返回bitmap  
	 */
	public static Bitmap createQRImage(String content, int widthPix, int heightPix, Bitmap logoBm) {
		Bitmap bitmap = null;
		try {
            if (content == null || "".equals(content)) {
                return null;
            }
 
            //配置参数
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
            //容错级别
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            //设置空白边距的宽度
//            hints.put(EncodeHintType.MARGIN, 2); //default is 4
 
            // 图像数据转换，使用了矩阵转换
            BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, widthPix, heightPix, hints);
            int[] pixels = new int[widthPix * heightPix];
            // 下面这里按照二维码的算法，逐个生成二维码的图片，
            // 两个for循环是图片横列扫描的结果
            for (int y = 0; y < heightPix; y++) {
                for (int x = 0; x < widthPix; x++) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * widthPix + x] = 0xff000000;
                    } else {
                        pixels[y * widthPix + x] = 0xffffffff;
                    }
                }
            }
 
            // 生成二维码图片的格式，使用ARGB_8888
            bitmap = Bitmap.createBitmap(widthPix, heightPix, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, widthPix, 0, 0, widthPix, heightPix);
 
            if (logoBm != null) {
                bitmap = addLogo(bitmap, logoBm);
            }
        } catch (WriterException e) {
            e.printStackTrace();
        }
		return bitmap;
	}
	
	
	/**
	 * 在二维码中间添加logo
	 * @param src	
	 * @param logo
	 * @return
	 */
	private static Bitmap addLogo(Bitmap src, Bitmap logo) {
        if (src == null) {
            return null;
        }
 
        if (logo == null) {
            return src;
        }
 
        //获取图片的宽高
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        int logoWidth = logo.getWidth();
        int logoHeight = logo.getHeight();
 
        if (srcWidth == 0 || srcHeight == 0) {
            return null;
        }
 
        if (logoWidth == 0 || logoHeight == 0) {
            return src;
        }
 
        //logo大小为二维码整体大小的1/5
        float scaleFactor = srcWidth * 1.0f / 5 / logoWidth;
        Bitmap bitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(src, 0, 0, null);
            canvas.scale(scaleFactor, scaleFactor, srcWidth / 2, srcHeight / 2);
            canvas.drawBitmap(logo, (srcWidth - logoWidth) / 2, (srcHeight - logoHeight) / 2, null);
 
            canvas.save(Canvas.ALL_SAVE_FLAG);
            canvas.restore();
        } catch (Exception e) {
            bitmap = null;
            e.getStackTrace();
        }
        return bitmap;
    }
	
	//------------------------------------------------------条形码-----------------------------------------------------
	/**
	 * 生成条形码
	 * @param context		
	 * @param contents		条形码内容
	 * @param desiredWidth	条形码宽度
	 * @param desiredHeight	条形码高度
	 * @param displayCode	是否在正文显示条形码内容
	 * @return
	 */
	public static Bitmap creatBarcode(Context context, String contents,
			int desiredWidth, int desiredHeight, boolean displayCode) {
		Bitmap ruseltBitmap = null;
		/**
		 * 图片两端所保留的空白的宽度
		 */
		int marginW = 20;
		/**
		 * 条形码的编码类型
		 */
		BarcodeFormat barcodeFormat = BarcodeFormat.CODE_128;

		if (displayCode) {
			//生成contents 的条形码
			Bitmap barcodeBitmap = encodeAsBitmap(contents, barcodeFormat,
					desiredWidth, desiredHeight);
			//生成contents的bitmap
			Bitmap codeBitmap = creatCodeBitmap(contents, desiredWidth + 2
					* marginW, desiredHeight, context);
			//合并上面两个bitmap
			ruseltBitmap = mixtureBitmap(barcodeBitmap, codeBitmap, new PointF(
					0, desiredHeight));
		} else {
			ruseltBitmap = encodeAsBitmap(contents, barcodeFormat,
					desiredWidth, desiredHeight);
		}

		return ruseltBitmap;
	}
	
	/**
	 * 生成条形码bitmap
	 * @param contents		条形码内容
	 * @param format		条形码格式
	 * @param desiredWidth
	 * @param desiredHeight
	 * @return
	 */
	protected static Bitmap encodeAsBitmap(String contents,
			BarcodeFormat format, int desiredWidth, int desiredHeight) {
		final int WHITE = 0xFFFFFFFF;
		final int BLACK = 0xFF000000;

		MultiFormatWriter writer = new MultiFormatWriter();
		BitMatrix result = null;
		try {
			result = writer.encode(contents, format, desiredWidth,
					desiredHeight, null);
		} catch (WriterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		int width = result.getWidth();
		int height = result.getHeight();
		int[] pixels = new int[width * height];
		// All are 0, or black, by default
		for (int y = 0; y < height; y++) {
			int offset = y * width;
			for (int x = 0; x < width; x++) {
				pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
			}
		}

		Bitmap bitmap = Bitmap.createBitmap(width, height,
				Bitmap.Config.ARGB_8888);
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
		return bitmap;
	}
	
	/**
	 * 生成显示条形码内容的bitmap
	 * @param contents
	 * @param width
	 * @param height
	 * @param context
	 * @return
	 */
	protected static Bitmap creatCodeBitmap(String contents, int width,
			int height, Context context) {
		TextView tv = new TextView(context);
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
				LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		tv.setLayoutParams(layoutParams);
		tv.setText(contents);
		tv.setHeight(height);
		tv.setGravity(Gravity.CENTER_HORIZONTAL);
		tv.setWidth(width);
		tv.setDrawingCacheEnabled(true);
		tv.setTextColor(Color.BLACK);
		tv.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		tv.layout(0, 0, tv.getMeasuredWidth(), tv.getMeasuredHeight());

		tv.buildDrawingCache();
		Bitmap bitmapCode = tv.getDrawingCache();
		return bitmapCode;
	}
	
	protected static Bitmap mixtureBitmap(Bitmap first, Bitmap second,
			PointF fromPoint) {
		if (first == null || second == null || fromPoint == null) {
			return null;
		}
		int marginW = 20;
		Bitmap newBitmap = Bitmap.createBitmap(
				first.getWidth() + second.getWidth() + marginW,
				first.getHeight() + second.getHeight(), Config.ARGB_4444);
		Canvas cv = new Canvas(newBitmap);
		cv.drawBitmap(first, marginW, 0, null);
		cv.drawBitmap(second, fromPoint.x, fromPoint.y, null);
		cv.save(Canvas.ALL_SAVE_FLAG);
		cv.restore();

		return newBitmap;
	}
	

	/**
	 *	文件存储根目录 如：/storage/emulated/0/Android/data/com.kgd.zhen.scanqrdemo/files 
	 * @param context
	 * @return
	 */
    public static String getFileRoot(Context context) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File external = context.getExternalFilesDir(null);
            if (external != null) {
                return external.getAbsolutePath();
            }
        }
        return context.getFilesDir().getAbsolutePath();
    }
}
