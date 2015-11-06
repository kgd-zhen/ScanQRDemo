package com.kgd.zhen.scanqrdemo;

import java.io.File;

import com.kgd.zhen.util.QRCodeUtil;
import com.zxing.activity.CaptureActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private EditText et_qr;
	private ImageView img_qr; //显示生成的二维码
	private CheckBox cb_qr; //生成二维码是否包含Logo
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		et_qr = (EditText)findViewById(R.id.et_qr);
		img_qr = (ImageView)findViewById(R.id.img_qr);
		cb_qr = (CheckBox)findViewById(R.id.cb_qr);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public void ScanQR(View v){
		Intent intent = new Intent(this, CaptureActivity.class);
		startActivity(intent);
	}
	
	public void GenerateQR(View v){
		if(et_qr.getText().toString().equals("")){
			Toast.makeText(getApplicationContext(), "et_qr 不能为空！", Toast.LENGTH_LONG);
			return;
		}
		//二维码图片较大时，生成图片、保存文件的时间可能较长，因此放在新线程中
        new Thread(new Runnable() {
            @Override
            public void run() {
            	final String filePath = QRCodeUtil.getFileRoot(MainActivity.this) + File.separator
                        + "qr_" + System.currentTimeMillis() + ".jpg"; //生成二维码图片 文件路径

            	boolean success = QRCodeUtil.createQRImage(et_qr.getText().toString().trim(), 800, 800,
                        cb_qr.isChecked() ? BitmapFactory.decodeResource(getResources(), R.drawable.barcode_torch_on) : null,
                        filePath);
                
                if (success) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            img_qr.setImageBitmap(BitmapFactory.decodeFile(filePath));
                        }
                    });
                }
            }
        }).start();
	}
}
