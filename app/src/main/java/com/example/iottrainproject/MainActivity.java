package com.example.iottrainproject;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    //Bluetoothサービスクラス
    static public class BluetoothService{
        //(Bluetooth UUID)
        private static final UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        //定数
        public static final int Message_State_Change     = 1;
        public static final int Message_Written          = 3;
        public static final int State_None               = 0;
        public static final int State_ConnectStart       = 1;
        public static final int State_ConnectFailed      = 2;
        public static final int State_Connected          = 3;
        public static final int State_ConnectionLost     = 4;
        public static final int State_DisconnectStart    = 5;
        public static final int State_Disconnected       = 6;
        //メンバ変数
        private int mState;
        private ConnectionThread mConnectionThread;
        private Handler mHandler;
        //接続時処理用のスレッド
        private class ConnectionThread extends Thread{

            private BluetoothSocket mBluetoothSocket;
            private OutputStream mOutput;

            //コンストラクタ(オブジェクトを作成したときに自動的に実行されるメソッド)
            public ConnectionThread(BluetoothDevice bluetoothDevice){
                try {
                    mBluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID_SPP);
                    mOutput = mBluetoothSocket.getOutputStream();
                }
                catch (IOException e){//入出力処理中の例外を管理
                    Log.e("BluetoothService","failed : bluetoothDevice.createRfcommSocketToServiceRecord(UUID_SPP)", e);
                }
            }
            //処理
            public void run(){
                while (State_Disconnected != mState) {
                    switch (mState) {
                        case State_None:
                            break;
                        case State_ConnectStart  : //接続開始
                            try {//Bluetoothオブジェクトを用いて、Bluetoothデバイスに接続を試みる
                                mBluetoothSocket.connect();
                            }
                            catch (IOException e) { //接続失敗
                                Log.d("BluetoothService","Failed : mBluetoothSocket.connect()");
                                setState(State_ConnectFailed);
                                cancel(); //スレッド終了
                                return;
                            }
                            //接続成功
                            setState(State_Connected);
                            break;
                        case State_ConnectFailed: //接続失敗
                            //処理はcancel()
                            break;
                        case State_Connected: //接続済み(Bluetoothデバイスから送信されるデータを受信)
                            break;
                        case State_ConnectionLost: //接続ロスト時
                            //処理はcancel()
                            break;
                        case State_DisconnectStart: //切断開始
                            //処理はcancel()
                            break;
                    }
            }
                synchronized (BluetoothService.this){
                    //親クラスが保持する自スレッドオブジェクトの解放(自分自身の解放)
                    mConnectionThread = null;
                }
            }
            //キャンセル（接続を終了する。ステータスをSTATE_DISCONNECTEDにすることによってスレッドも終了する)
            public void cancel(){
                try {
                    mBluetoothSocket.close();
                }
                catch (IOException e){
                    Log.e("BluetoothService","Failed : mBluetoothSocket.close()", e);
                }
                setState(State_Disconnected);
            }

            //バイト列送信
            public void write( byte[] buf) {
                try {
                    synchronized (BluetoothService.this) {
                        mOutput.write(buf);
                    }
                    mHandler.obtainMessage(Message_Written).sendToTarget();
                }catch(IOException e){
                    Log.e("BluetoothService","Failed : BluetoothSocket.close()",e);
                }
            }
        }

        //コンストラクタ（自動起動)
        public BluetoothService(Handler handler, BluetoothDevice device){
            mHandler = handler;
            mState  = State_None;

            //接続時処理用スレッドの作成と開始
            mConnectionThread = new ConnectionThread(device);
            mConnectionThread.start();
        }

        //ステータス設定
        private synchronized void setState(int state){
            mState = state;
            mHandler.obtainMessage(Message_State_Change,state, -1).sendToTarget();//obtain=入手
        }

        //接続開始時の処理
        public synchronized void connect(){
            if(State_None != mState){
                //1つのBluetoothServiceオブジェクトに対して、connect()は一回だけ呼べる。
                //２回目以降の呼び出しは処理しない
                return;
            }
            //ステータス設定
            setState(State_ConnectStart);
        }

        //接続切断時の処理
        public synchronized void disconnect(){
            if(State_Connected != mState){//接続中以外は、処理しない
                return;
            }
            //ステータス設定
            setState(State_DisconnectStart);
            mConnectionThread.cancel();
        }

        //バイト列送信(非同期)
        public void write(byte[] out){
            ConnectionThread connectionThread;
            synchronized (this){
                if(State_Connected != mState){
                    return;
                }
                connectionThread = mConnectionThread;
            }
            connectionThread.write(out);
        }
    }

    //定数
    private static final int REQUEST_ENABLE_BLUETOOTH = 1; // Bluetooth機能有効化要求時の識別コード
    private static final int REQUEST_CONNECT_DEVICE = 2; //デバイス接続要求時の識別コード、デバイスリストアクティビティに移行する際に必要
    //メンバ変数
    private BluetoothAdapter mBluetoothAdapter; //Bluetooth処理で必要となる
    private String mDeviceAddress = ""; //デバイスアドレス
    private BluetoothService mBluetoothService; //BluetoothService:Bluetoothデバイスとの通信処理を担う
    //GUIアイテム
    private Button mButton_Connect; //接続ボタン
    private Button mButton_Disconnect; //切断ボタン
    private Button mButton_adLow;
    private Button mButton_adMid;
    private Button mButton_adHigh;
    private Button mButton_bkLow;
    private Button mButton_bkMid;
    private Button mButton_bkHigh;
    private Button mButton_TrainStop; //停車ボタン
    private Button mButton_reload;
    private WebView myWebview;

    public String pettern = "A"; //A = stop時　B =前進時　C= 後退時

    //Bluetoothサービスから情報を取得するハンドラ
    private final Handler mHandler = new Handler(Looper.getMainLooper()){

        //ハンドルメッセージ
        //UIスレッドの処理なので,UI処理について、runOnUiThread対応は不要
        @Override
        public void handleMessage(Message msg){
            switch (msg.what){
                case BluetoothService.Message_State_Change:
                    switch (msg.arg1){
                        case BluetoothService.State_None: //未接続
                            break;
                        case BluetoothService.State_ConnectStart:
                            break;
                        case BluetoothService.State_ConnectFailed:
                            Toast.makeText(MainActivity.this,"Failed to connect device.",Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothService.State_Connected:
                            //GUIアイテムの有効無効の設定
                            //切断ボタン等を有効にする
                            mButton_Disconnect.setEnabled(true);
                            mButton_adLow.setEnabled(true);
                            mButton_adMid.setEnabled(true);
                            mButton_adHigh.setEnabled(true);
                            mButton_bkLow.setEnabled(true);
                            mButton_bkMid.setEnabled(true);
                            mButton_bkHigh.setEnabled(true);
                            mButton_TrainStop.setEnabled(true);
                            break;
                        case BluetoothService.State_ConnectionLost:
                            //Toast.makeText("MainActivity.this,"Lost connection to the device.",Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothService.State_DisconnectStart:
                            //GUIアイテム以下略
                            mButton_Disconnect.setEnabled(false);
                            mButton_adLow.setEnabled(false);
                            mButton_adMid.setEnabled(false);
                            mButton_adHigh.setEnabled(false);
                            mButton_bkLow.setEnabled(false);
                            mButton_bkMid.setEnabled(false);
                            mButton_bkHigh.setEnabled(false);
                            mButton_TrainStop.setEnabled(false);
                            break;
                        case BluetoothService.State_Disconnected:
                            //GUIアイテム以下略
                            mButton_Connect.setEnabled(true);
                            mBluetoothService = null; //Bluetoothオブジェクトの解放
                            break;
                    }
                    break;
                case BluetoothService.Message_Written:
                    //GUI以下略
                    //連打対策したボタンを復帰
                    if(pettern.equals("A")) {
                        mButton_TrainStop.setEnabled(true);
                        mButton_adLow.setEnabled(true);
                        mButton_adMid.setEnabled(true);
                        mButton_adHigh.setEnabled(true);
                        mButton_bkLow.setEnabled(true);
                        mButton_bkMid.setEnabled(true);
                        mButton_bkHigh.setEnabled(true);
                        break;
                    }else if(pettern.equals("B")){
                        mButton_TrainStop.setEnabled(true);
                        mButton_adLow.setEnabled(true);
                        mButton_adMid.setEnabled(true);
                        mButton_adHigh.setEnabled(true);
                        break;
                    }else if(pettern.equals("C")){
                        mButton_TrainStop.setEnabled(true);
                        mButton_bkLow.setEnabled(true);
                        mButton_bkMid.setEnabled(true);
                        mButton_bkHigh.setEnabled(true);
                        break;
                    }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myWebview = findViewById(R.id.webView);
        myWebview.setWebViewClient(new WebViewClient());
        myWebview.getSettings().setBuiltInZoomControls(true);
        myWebview.loadUrl("http://10.1.36.152:8080/stream");

        //GUIアイテム,ボタンの取得、クリックリスターの設定
        mButton_Connect = findViewById(R.id.button_connect);
        mButton_Connect.setOnClickListener(this);
        mButton_Disconnect = findViewById(R.id.button_disconnect);
        mButton_Disconnect.setOnClickListener(this);
        mButton_TrainStop = findViewById(R.id.button_TrainStop);
        mButton_TrainStop.setOnClickListener(this);
        mButton_adLow = findViewById(R.id.button_adLow);
        mButton_adLow.setOnClickListener(this);
        mButton_adMid = findViewById(R.id.button_adMid);
        mButton_adMid.setOnClickListener(this);
        mButton_adHigh = findViewById(R.id.button_adHigh);
        mButton_adHigh.setOnClickListener(this);
        mButton_bkLow = findViewById(R.id.button_bkLow);
        mButton_bkLow.setOnClickListener(this);
        mButton_bkMid = findViewById(R.id.button_bkMid);
        mButton_bkMid.setOnClickListener(this);
        mButton_bkHigh = findViewById(R.id.button_bkHigh);
        mButton_bkHigh.setOnClickListener(this);
        mButton_reload = findViewById(R.id.button_reload);
        mButton_reload.setOnClickListener(this);

        //Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (null == mBluetoothAdapter) {//Android端末がBluetoothをサポートしていない
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show();//短く表示
            finish(); //アプリ終了
        }
    }
    //初回表示、ポーズからの復帰
    //接続、切断ボタン最初は無効、デバイスアドレスが空でなければ接続ボタンが有効になるように処理
    @Override
    protected void onResume() {
        super.onResume();
        // 端末のBluetooth機能の有効化を要求
        requestBluetoothFeature();
        //GUIアイテム最初は無効
        mButton_Connect.setEnabled(false);
        mButton_Disconnect.setEnabled(false);
        mButton_adLow.setEnabled(false);
        mButton_adMid.setEnabled(false);
        mButton_adHigh.setEnabled(false);
        mButton_bkLow.setEnabled(false);
        mButton_bkMid.setEnabled(false);
        mButton_bkHigh.setEnabled(false);
        mButton_TrainStop.setEnabled(false);
        myWebview.loadUrl("http://10.1.36.152:8080/stream");

        //デバイスアドレスが空でなければ接続ボタンを有効にする
        if(!mDeviceAddress.equals("")){
            mButton_Connect.setEnabled(true);
        }
        //接続ボタンを押す
        mButton_Connect.callOnClick();
    }

    //別のアクティビティ（または別のアプリ）に移行したことで、バックグラウンドに追いやられた時
    @Override
    protected void onPause() {
        super.onPause();
        disconnect();//切断関数
    }

    //アクティビティの終了直前,BluetoothServiceクラスのオブジェクトを削除
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if( null != mBluetoothService) {
            mBluetoothService.disconnect();
            mBluetoothService = null;
        }
    }


    //端末のBluetooth機能の有効化を要求
    private void requestBluetoothFeature() {
        if (mBluetoothAdapter.isEnabled()) {
            return;
        }
        //デバイスのBluetoothが有効になっていないときは、有効化を要求する
        //画面遷移に似てる(?)Intentを作成　sAFRで起動　識別コードは1　
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
    }

    //機能の有効化ダイアログの操作結果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH: //Bluetooth有効化要求
                if (Activity.RESULT_CANCELED == resultCode) {
                    //ユーザーが有効を拒否またはエラーで有効にされなかった場合
                    Toast.makeText(this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                break;
            case REQUEST_CONNECT_DEVICE: //デバイス要求、デバイスリストアクティビティから情報を取得して戻る
                String strDeviceName;
                if (Activity.RESULT_OK == resultCode) {
                    //デバイスリストアクティビティから情報を取得
                    strDeviceName = data.getStringExtra(DeviceListActivity.Extras_Device_Name);
                    mDeviceAddress = data.getStringExtra(DeviceListActivity.Extras_Device_Address);
                    //取得してきたデバイス名、アドレスを変数に入れる
                } else {
                    strDeviceName = "";
                    mDeviceAddress = "";
                }
                ((TextView) findViewById(R.id.textview_deviceName)).setText(strDeviceName);
                ((TextView) findViewById(R.id.textview_deviceAddress)).setText(mDeviceAddress);
                //textViewに変数（デバイス名とアドレス）をセット
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    //オプションメニューをアクティビティ上に作成時の処理
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    //オプションメニューのアイテム選択時の処理,検索ボタンを押したとき
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menuitem_search) {
            Intent deviceListActivityIntent = new Intent(this, DeviceListActivity.class);
            //画面遷移の処理。第一引数の画面から第二引数の画面へ　newする際は引数二つ必要。
            startActivityForResult(deviceListActivityIntent, REQUEST_CONNECT_DEVICE);
            return true;
        }
        return false;
    }
    @Override
    public void onClick(View v) {
        if(mButton_Connect.getId() == v.getId()){
            mButton_Connect.setEnabled(false); //接続ボタンの無効化(連打防止)
            connect(); //接続
            return;
        }if(mButton_Disconnect.getId() == v.getId()){
            mButton_Disconnect.setEnabled(false); //切断ボタンの無効化
            disconnect(); //切断
            return;
        }if(mButton_TrainStop.getId() == v.getId()){
            pettern = "A";
            mButton_TrainStop.setEnabled(false);
            mButton_adLow.setEnabled(false);
            mButton_adMid.setEnabled(false);
            mButton_adHigh.setEnabled(false);
            mButton_bkLow.setEnabled(false);
            mButton_bkMid.setEnabled(false);
            mButton_bkHigh.setEnabled(false);
            write("a");
            return;
        }if(mButton_adLow.getId() == v.getId()){
            pettern = "B";
            mButton_TrainStop.setEnabled(false);
            mButton_adLow.setEnabled(false);
            mButton_adMid.setEnabled(false);
            mButton_adHigh.setEnabled(false);
            mButton_bkLow.setEnabled(false);
            mButton_bkMid.setEnabled(false);
            mButton_bkHigh.setEnabled(false);
            write("b");
            return;
        }if(mButton_adMid.getId() == v.getId()){
            pettern = "B";
            mButton_TrainStop.setEnabled(false);
            mButton_adLow.setEnabled(false);
            mButton_adMid.setEnabled(false);
            mButton_adHigh.setEnabled(false);
            mButton_bkLow.setEnabled(false);
            mButton_bkMid.setEnabled(false);
            mButton_bkHigh.setEnabled(false);
            write("c");
            return;
        }if(mButton_adHigh.getId() == v.getId()){
            pettern = "B";
            mButton_TrainStop.setEnabled(false);
            mButton_adLow.setEnabled(false);
            mButton_adMid.setEnabled(false);
            mButton_adHigh.setEnabled(false);
            mButton_bkLow.setEnabled(false);
            mButton_bkMid.setEnabled(false);
            mButton_bkHigh.setEnabled(false);
            write("d");
            return;
        }if(mButton_bkLow.getId() == v.getId()){
            pettern = "C";
            mButton_TrainStop.setEnabled(false);
            mButton_adLow.setEnabled(false);
            mButton_adMid.setEnabled(false);
            mButton_adHigh.setEnabled(false);
            mButton_bkLow.setEnabled(false);
            mButton_bkMid.setEnabled(false);
            mButton_bkHigh.setEnabled(false);
            write("e");
            return;
        }if(mButton_bkMid.getId() == v.getId()){
            pettern = "C";
            mButton_TrainStop.setEnabled(false);
            mButton_adLow.setEnabled(false);
            mButton_adMid.setEnabled(false);
            mButton_adHigh.setEnabled(false);
            mButton_bkLow.setEnabled(false);
            mButton_bkMid.setEnabled(false);
            mButton_bkHigh.setEnabled(false);
            write("f");
            return;
        }if(mButton_bkHigh.getId() == v.getId()){
            pettern = "C";
            mButton_TrainStop.setEnabled(false);
            mButton_adLow.setEnabled(false);
            mButton_adMid.setEnabled(false);
            mButton_adHigh.setEnabled(false);
            mButton_bkLow.setEnabled(false);
            mButton_bkMid.setEnabled(false);
            mButton_bkHigh.setEnabled(false);
            write("g");
            return;
        }if (mButton_reload.getId() == v.getId()){
            myWebview.reload();
            return;
        }
    }
    private void connect(){
        if(mDeviceAddress.equals("")){
            //DeviceAddressが空の場合は処理しない
            return;
        }
        if(null != mBluetoothService){
            //BluetoothServiceがnullでないなら接続済みか、接続中
            return;
        }
        //接続処理
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
        mBluetoothService = new BluetoothService(mHandler ,device);
        mBluetoothService.connect();
    }
    private void disconnect(){
        if(null == mBluetoothService){//nullなら切断済or切断中
            return;
        }
        mBluetoothService.disconnect();//切断処理
        mBluetoothService = null;
    }
    //文字列送信
    private void write (String string){
        if( null == mBluetoothService){//nullなら切断済or切断中
            return;
        }
        String stringSend = string + "\r\n"; //終端に改行コードを付加
        mBluetoothService.write(stringSend.getBytes()); //バイト列送信
    }
}
