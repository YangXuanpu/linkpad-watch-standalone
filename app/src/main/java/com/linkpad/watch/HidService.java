package com.linkpad.watch;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.Executor;

public class HidService extends Service {
    public interface Listener { void changed(String s,boolean registered,boolean connected); }
    public class LocalBinder extends Binder { HidService getService(){return HidService.this;} }
    private final IBinder binder=new LocalBinder();
    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice peer;
    private boolean registered=false;
    private Listener listener;
    private final Executor executor=r->new Handler(Looper.getMainLooper()).post(r);
    private void say(String s){if(listener!=null)listener.changed(s,registered,peer!=null);}
    private final BluetoothProfile.ServiceListener profile=new BluetoothProfile.ServiceListener(){
        public void onServiceConnected(int p,BluetoothProfile proxy){
            hid=(BluetoothHidDevice)proxy;
            BluetoothHidDeviceAppSdpSettings sdp=new BluetoothHidDeviceAppSdpSettings("Linkpad Watch","Mouse and PPT Remote","Linkpad",(byte)0xC0,HidProtocol.DESCRIPTOR);
            boolean ok=hid.registerApp(sdp,null,null,executor,callback);
            say(ok?"HID 注册请求已发送":"系统拒绝 HID 注册");
        }
        public void onServiceDisconnected(int p){hid=null;registered=false;peer=null;say("HID 服务断开");}
    };
    private final BluetoothHidDevice.Callback callback=new BluetoothHidDevice.Callback(){
        public void onAppStatusChanged(BluetoothDevice d,boolean r){registered=r;say(r?"HID 已注册，请从电脑连接手表":"HID 未注册");}
        public void onConnectionStateChanged(BluetoothDevice d,int state){
            if(state==BluetoothProfile.STATE_CONNECTED){peer=d;say("已连接："+d.getName());}
            else if(state==BluetoothProfile.STATE_DISCONNECTED){peer=null;say("电脑已断开");}
        }
        public void onGetReport(BluetoothDevice d,byte type,byte id,int size){if(hid!=null)hid.replyReport(d,type,id,id==1?new byte[8]:id==2?new byte[5]:new byte[2]);}
    };
    public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("hid","Linkpad HID",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);startForeground(7,new Notification.Builder(this,"hid").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Linkpad Watch").setContentText("蓝牙 HID 服务运行中").build());}
        adapter=((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        if(adapter!=null&&adapter.isEnabled())adapter.getProfileProxy(this,profile,BluetoothProfile.HID_DEVICE);
        else say("请先开启手表蓝牙");
    }
    public int onStartCommand(Intent i,int f,int id){return START_STICKY;}
    public IBinder onBind(Intent i){return binder;}
    public void setListener(Listener l){listener=l;say(adapter==null?"蓝牙不可用":"等待电脑连接");}
    public boolean isConnected(){return peer!=null;}
    public void connect(BluetoothDevice d){if(hid!=null&&registered&&d!=null)hid.connect(d);}
    public void move(int x,int y){send(HidProtocol.REPORT_MOUSE,HidProtocol.mouseReport(0,x,y,0,0));}
    public void click(int b){send(HidProtocol.REPORT_MOUSE,HidProtocol.mouseReport(b,0,0,0,0));new Handler().postDelayed(()->send(HidProtocol.REPORT_MOUSE,HidProtocol.mouseReport(0,0,0,0,0)),70);}
    public void key(int key,int mod){send(HidProtocol.REPORT_KEYBOARD,HidProtocol.keyboardReport(mod,key));new Handler().postDelayed(()->send(HidProtocol.REPORT_KEYBOARD,new byte[8]),70);}
    private void send(int id,byte[] data){if(hid!=null&&registered&&peer!=null)hid.sendReport(peer,id,data);}
    public void onDestroy(){try{if(hid!=null){if(registered)hid.unregisterApp();adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,hid);}}catch(Exception e){}super.onDestroy();}
}
