package com.example.iottrainproject;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class DeviceListActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    static class DeviceListAdapter extends BaseAdapter {
        //デバイスを検出した際に使用する補助クラス、デバイスリストを保持する
        //デバイスリストアダプタオブジェクトをリストビューにセット
        private final ArrayList<BluetoothDevice> mDeviceList;
        private final LayoutInflater mInflate; //xmlをviewオブジェクトに変換するもの

        public DeviceListAdapter(Activity activity) {
            super();
            mDeviceList = new ArrayList<>();
            mInflate = activity.getLayoutInflater();
        }

        //リストへの追加
        public void addDevice(BluetoothDevice device) {
            if (!mDeviceList.contains(device)) {
                //追加されていなければ追加する
                mDeviceList.add(device);
                notifyDataSetChanged(); //ListViewの更新
            }
        }

        //リストのクリア
        public void clear() {
            mDeviceList.clear();
            notifyDataSetChanged(); //ListViewの更新
        }

        @Override //配列やListの要素数を返す,Listに表示するデータの個数
        public int getCount() {
            return mDeviceList.size();
        }

        @Override //indexやオブジェクトを返す
        public Object getItem(int position) {
            return mDeviceList.get(position);
        }

        @Override //特別なIDやindexのほかに返す
        public long getItemId(int position) {
            return position;
        }

        static class ViewHolder {
            TextView deviceName;
            TextView deviceAddress;
        }

        @Override //ここで描画
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder viewHolder;
            //General ListView optimization code.
            if (null == convertView) {
                convertView = mInflate.inflate(R.layout.listitem_device, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = convertView.findViewById(R.id.textview_deviceaddress);
                viewHolder.deviceName = convertView.findViewById(R.id.textview_devicename);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            if(position%2 == 0){
                convertView.setBackgroundColor(Color.rgb(20,255,130));
            }else if(position%2 == 1){
                convertView.setBackgroundColor(Color.rgb(20,255,247));
            }

            BluetoothDevice device = mDeviceList.get(position);
            String deviceName = device.getName();
            if (null != deviceName && 0 < deviceName.length()) {
                viewHolder.deviceName.setText(deviceName);
            } else {
                viewHolder.deviceName.setText(R.string.unknown_device);
            }
            viewHolder.deviceAddress.setText(device.getAddress());

            return convertView;
        }
    }
    //定数
    private static final int Request_Enable_Bluetooth = 1; //Bluetooth機能の有効化要求時の識別コード
    public static final String Extras_Device_Name = "DEVICE_NAME";
    public static final String Extras_Device_Address = "DEVICE_ADDRESS";

    //メンバ変数
    private BluetoothAdapter mBluetoothAdapter; //BluetoothAdapter :Bluetooth処理で必要になる
    private DeviceListAdapter mDeviceListAdapter; //リストビューの内容
    private boolean mScanning = false; //スキャン中かどうかの確認用フラグ

    //ブロードキャスト（発信）レシーバー 端末がアプリ向けに発信している情報（インテント）を受信するクラス 時間、バッテ残量、細かな情報を知ることができる
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        //ブロードレシーバーオブジェクト　端末発見、検索終了時にソフトウェア固有処理(デバイスリスト追加等を行う)
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //Bluetooth端末を発見
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                runOnUiThread(() -> mDeviceListAdapter.addDevice(device));
                return;
            }
            //Bluetooth端末検索を終了
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                runOnUiThread(() -> {
                    mScanning = false;
                    //メニュー更新
                    invalidateOptionsMenu();
                });
            }
        }

    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        //戻り値の初期化
        setResult(Activity.RESULT_CANCELED);

        //リストビューの設定
        mDeviceListAdapter = new DeviceListAdapter(this);//ビューアダプタの初期化
        ListView listView = findViewById(R.id.devicelist); //リストビューの取得
        listView.setAdapter(mDeviceListAdapter); //リストビューにビューアダプタをセット
        listView.setOnItemClickListener(this); //クリックリスナーオブジェクトのセット

        //Bluetoothアダプタの取得
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if(null == mBluetoothAdapter){
            //デバイスがBluetoothをサポートしていない
            Toast.makeText(this, R.string.bluetooth_is_not_supported, Toast.LENGTH_SHORT).show();
            finish(); //アプリ終了の宣言
        }
    }

    //初回表示時、ポーズからの復帰時
    @Override
    protected void onResume(){
        super.onResume();
        //デバイスのBluetooth機能の有効化要求
        requestBluetoothFeature();
        //受信するためにブロードキャストレシーバーの登録、受信できるインテントを指定
        registerReceiver(mBroadcastReceiver,new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(mBroadcastReceiver,new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        //このアクティビティが起動したら自動で検索開始
        startScan();
    }

    //別のアクティビティ（か別のアプリに移動したときにバックグラウンドに移行したとき
    @Override
    protected void onPause(){
        super.onPause();
        //スキャンの停止
        stopScan();
        //ブロードレシーバーの登録解除
        unregisterReceiver(mBroadcastReceiver);
    }

    //デバイスのBluetooth機能の有効化要求
    private void requestBluetoothFeature(){
        if(mBluetoothAdapter.isEnabled()){
            return;
        }
        //デバイスのBluetooth機能が有効になってないときは、有効化要求(ダイアログ表示)
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent,Request_Enable_Bluetooth);
    }

    //機能の有効化ダイアログの捜査結果 209行sAFRから起動
    @Override
    protected void onActivityResult(int requestCode,int resultCode, Intent data){
        if (requestCode == Request_Enable_Bluetooth) { //Bluetooth有効化要求
            if (Activity.RESULT_CANCELED == resultCode) {
                //有効にされなかった
                Toast.makeText(this, R.string.bluetooth_is_not_working, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode,resultCode,data);
    }
    //スキャンの開始
    private void startScan(){
        //リストビューの内容を空にする。
        mDeviceListAdapter.clear();
        //スキャンの開始
        mScanning = true;
        mBluetoothAdapter.startDiscovery(); //約12秒間の問い合わせのスキャンが行われる BLUETOOTHSCANの権限をmanifestに追加
        //メニューの更新
        invalidateOptionsMenu();
    }

    //スキャンの停止
    private void stopScan(){
        //スキャンの停止
        mBluetoothAdapter.cancelDiscovery();
    }
    //リストビューのアイテムクリック時の処理、メインアクティビティに戻る処理
    @Override
    public void onItemClick(AdapterView<?> parent,View view, int position, long id){

        //クリックされたアイテムの取得
        BluetoothDevice device = (BluetoothDevice) mDeviceListAdapter.getItem(position);
        if( null == device ){
            return;
        }
        //戻り値の設定
        Intent intent = new Intent();//元のアクティビティへのIntentを生成
        intent.putExtra(Extras_Device_Name,device.getName()); //タッチしたデバイス名を取得
        intent.putExtra(Extras_Device_Address,device.getAddress());//タッチしたアドレスを取得
        setResult(Activity.RESULT_OK,intent);
        finish();
    }

    //オプションメニュー作成時の処理、オプションから検出開始、終了を行えるようにする
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.activity_device_list,menu);
        if(!mScanning){
            menu.findItem(R.id.menuitem_stop).setVisible(false);
            menu.findItem(R.id.menuitem_scan).setVisible(true);
            menu.findItem(R.id.menuitem_progress).setActionView(null);
        }else{
            menu.findItem(R.id.menuitem_stop).setVisible(true);
            menu.findItem(R.id.menuitem_scan).setVisible(false);
            menu.findItem(R.id.menuitem_progress).setActionView(R.layout.actionbar_indeterminate_pragoress);
        }
        return true;
    }
    //オプションメニューのアイテム選択時の処理
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int itemId = item.getItemId();
        if (itemId == R.id.menuitem_scan) {
            startScan(); //スキャンの開始
        } else if (itemId == R.id.menuitem_stop) {
            stopScan(); //スキャンの停止
        }
        return true;
    }
}

