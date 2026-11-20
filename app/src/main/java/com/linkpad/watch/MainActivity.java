package com.linkpad.watch;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {
    private HidService hid;
    private TextView status;
    private SensorManager sensors;
    private Sensor gyro;
    private boolean airMouse = false, wrist = true, ppt = false, latched = false;
    private long lastTs=0, lastFlick=0;
    private float dx=0,dy=0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(128);
        getWindow().setFlags(1024,1024);
        sensors=(SensorManager)getSystemService(SENSOR_SERVICE);
        gyro=sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        buildUi();
        if (Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},9);
        } else startService();
    }
    private void startService() {
        Intent i=new Intent(this,HidService.class);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        bindService(i,new android.content.ServiceConnection(){
            public void onServiceConnected(android.content.ComponentName n,android.os.IBinder b){
                hid=((HidService.LocalBinder)b).getService();
                hid.setListener((s,r,c)->runOnUiThread(()->status.setText(s)));
            }
            public void onServiceDisconnected(android.content.ComponentName n){hid=null;}
        },BIND_AUTO_CREATE);
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==9&&Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)startService();}
    private TextView label(String t){TextView v=new TextView(this);v.setText(t);v.setTextColor(0xffffffff);v.setGravity(Gravity.CENTER);v.setTextSize(12);return v;}
    private Button button(String t,Runnable a,LinearLayout root){Button b=new Button(this);b.setText(t);b.setOnClickListener(v->a.run());root.addView(b,new LinearLayout.LayoutParams(-1,0,1));return b;}
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setGravity(Gravity.CENTER);root.setPadding(26,12,26,12);root.setBackgroundColor(0xff090d16);
        status=label("Linkpad Watch · 正在启动蓝牙 HID");root.addView(status,new LinearLayout.LayoutParams(-1,0,1));
        button("配对 / 蓝牙设置",()->{try{startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));}catch(Exception e){}},root);
        button("连接已配对电脑",()->{if(hid!=null){BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a!=null&&Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED){try{for(BluetoothDevice d:a.getBondedDevices())hid.connect(d);}catch(Exception e){status.setText("连接失败："+e.getMessage());}}}},root);
        button("空鼠："+(airMouse?"开":"关"),()->{airMouse=!airMouse;((Button)root.getChildAt(2)).setText("空鼠："+(airMouse?"开":"关"));},root);
        button("PPT 控制",()->{ppt=!ppt;status.setText(ppt?"PPT：甩腕翻页已开启":"普通空鼠模式");},root);
        button("激光笔 Ctrl+L",()->{if(hid!=null)hid.key(HidProtocol.KEY_L,HidProtocol.MOD_LEFT_CTRL);},root);
        button("上一页",()->{if(hid!=null)hid.key(HidProtocol.KEY_PAGE_UP,0);},root);
        button("下一页",()->{if(hid!=null)hid.key(HidProtocol.KEY_PAGE_DOWN,0);},root);
        button("F5 开始 / Esc 退出",()->{if(hid!=null)hid.key(HidProtocol.KEY_F5,0);},root);
        button("左键 / 右键",()->{if(hid!=null)hid.click(1);},root);
        setContentView(root);
    }
    @Override protected void onResume(){super.onResume();if(gyro!=null)sensors.registerListener(this,gyro,SensorManager.SENSOR_DELAY_GAME);}
    @Override protected void onPause(){sensors.unregisterListener(this);super.onPause();}
    @Override public void onSensorChanged(SensorEvent e){
        if(e.sensor.getType()!=Sensor.TYPE_GYROSCOPE)return;
        long t=e.timestamp;if(lastTs==0){lastTs=t;return;}float dt=Math.min(.05f,(t-lastTs)/1e9f);lastTs=t;
        float x=e.values[0],z=e.values[2],m=Math.max(Math.abs(x),Math.abs(z));
        if(m<1.1f)latched=false;
        if(ppt&&wrist&&m>3.0f&&!latched&&t-lastFlick>700000000L){latched=true;lastFlick=t;if(hid!=null)hid.key((x>=0?HidProtocol.KEY_PAGE_DOWN:HidProtocol.KEY_PAGE_UP),0);}
        if(airMouse&&hid!=null){dx+=z*dt*500;dy-=x*dt*500;int ix=(int)dx,iy=(int)dy;if(ix!=0||iy!=0){dx-=ix;dy-=iy;hid.move(ix,iy);}}
    }
    @Override public void onAccuracyChanged(Sensor s,int a){}
}
