package com.example.asme2023;

import static android.os.SystemClock.sleep;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import com.example.asme2023.databinding.ActivityMainBinding;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import tool.CircleView;
import tool.RockerView;
import tool.UserSetData;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String TAG = MainActivity.class.getSimpleName()+"My";

    private View view; // 視圖
    private ActivityMainBinding binding; // MVVM 架構宣告
    private Handler mHandler; // 宣告 Handler
    private boolean isBlePostRunning = false; // (旗標)用於控制 Thread 是否繼續執行

    //數據資料儲存*********************************
    private UserSetData mUserSetData; // 存储數據到手機儲存空間
    private String index[]={"etnSunMaxW",
                            "etnWindMaxW",
                            "etnRcVMax",
                            "etnRcVMin",
                            "etnRcF",
                            "etnBatVMax",
                            "etnBatVMin",
                            "etnBatMah",
                            "etnSetMaxSpeed",
                            "etnSetTurnSensitive"}; // 建立索引列表
    private EditText[] editTexts;
    //******************************************

    //藍芽連線宣告*********************************
    private static final int BLUETOOTH_REQUEST_CODE = 1;
    private boolean connect = false;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice device = null;
    private String deviceName,deviceAddress;
    private BluetoothSocket socket;
    private ParcelUuid[] deviceUUid;
    private OutputStream os;
    private InputStream is;
    //******************************************

    //藍芽數據暫存********************************
    //接收-------------------
    private static final int CHUNK_SIZE = 4096;
    // 字串陣列
    private String receiveStrArray[] = {"00.0", "0.000", "00", "00.0", "0.000", "00", "00.000", "0.00", "00", "00.000", "0.00", "00", "0000", "00"};
                                 // 類別 "電壓(太陽能),電流(太陽能),狀態(太陽能),電壓(風力),電流(風力),狀態(風力),電壓(電池),電流(電池),狀態(電池),電壓(電容),電流(電容),狀態(電容),系統狀態,驗算碼"
                                 // 位置     [0]        [1]        [2]       [3]       [4]      [5]      [6]       [7]      [8]      [9]      [10]     [11]     [12]  [13]
    private float batAAdj = 0, rcAAdj = 0;
    //----------------------
    //發送-------------------
    private int bleSendText[] = {0,0,90,0,1,16};
                               // "前/後,速度,轉向角度,上/下,始能,驗算碼"
    //----------------------
    //進度條*************************************
    private CircleView mCircleViewRc; // 超級電容
    private CircleView mCircleViewBat; // 鋰電電池
    //******************************************

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // 使螢幕保持在喚醒狀態

        // 隱藏導航欄
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        // setContentView(R.layout.activity_main);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        view = binding.getRoot();
        setContentView(view);

        // 視圖初始化
        initView();

        // 建立 Handler
        mHandler = new Handler();
        mHandler.postDelayed(logClose,5900); // 關閉程式初始化介面動畫

        // 藍芽訊息監控 Thread 初始化
        receiveBlePost.start();
        sendBlePost.start();

        // BlueTooth Adapter
        bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // BlueTooth 抓到設備，發送廣播
        IntentFilter filter = new IntentFilter("android.bluetooth.devicepicker.action.DEVICE_SELECTED");
        if(receiver!=null) {
            registerReceiver(receiver, filter); // 廣播
        }

    }

    /**視圖元件初始化**/
    private void initView (){

        // 載入 MPChart 圖表
        initChart(binding.lineChartSun);
        initChart(binding.lineChartWind);

        // 載入水波紋進度條
        mCircleViewRc = binding.waveViewRc;
        mCircleViewBat = binding.waveViewBat;
        // 設置多高 0.1-1F
        mCircleViewRc.setmWaterLevel(0.00F);
        mCircleViewBat.setmWaterLevel(0.8F);
        // 執行
        mCircleViewRc.startWave();
        mCircleViewBat.startWave();

        // 百分比文字
        binding.textRcPercentage.setText("0%");
        binding.textBatPercentage.setText("0%");

        binding.textVersion.setText("v " + getVersionName(MainActivity.this));

        // 預設速度開關為高速
        binding.switchSpeed.setChecked(true);

        // 獲取按鈕的實例及監聽器
        Button btnBluetooth = binding.btnBluetooth;
        btnBluetooth.setOnClickListener(this);
        Button btnSend = binding.btnSend;
        btnSend.setOnClickListener(this);
        Button btnSet = binding.btnSet;
        btnSet.setOnClickListener(this);
        Button btnSetSave = binding.btnSetSave;
        btnSetSave.setOnClickListener(this);
        Button btnSetRst = binding.btnSetRst;
        btnSetRst.setOnClickListener(this);
        Button btnConsole = binding.btnConsole;
        btnConsole.setOnClickListener(this);
        Button btnConsoleUp =binding.btnConsoleUp;
        btnConsoleUp.setOnClickListener(this);
        Button btnConsoleDn =binding.btnConsoleDn;
        btnConsoleDn.setOnClickListener(this);

        try{
            RockerView rocker = binding.rocker;
            rocker.setListener(new RockerView.RockerListener() {
                @Override
                public void callback(int eventType, int currentAngle, float currentDistance) {
                    switch (eventType) {
                        case RockerView.EVENT_ACTION:
                            // 触摸事件回调
                            //Log.e("EVENT_ACTION-------->", "angle="+currentAngle+" - distance"+currentDistance);
                            break;
                        case RockerView.EVENT_CLOCK:
                            // 定时回调
                            if (currentAngle != -1){
                                //Log.e("EVENT_CLOCK", "angle="+currentAngle+" - distance="+currentDistance + "convert="+convertValue((int) currentAngle));
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (currentDistance > 0){
                                            // 前or後
                                            if (0 <= currentAngle && currentAngle <= 180) bleSendText[0] = 1; // 前進
                                            else bleSendText[0] = 2; // 後退
                                            // 速度
                                            if (binding.switchSpeed.isChecked())
                                                bleSendText[1] = (int) ((int)(currentDistance*0.55865922)*(Double.parseDouble(String.valueOf(binding.etnSetMaxSpeed.getText()))/100));
                                            else
                                                bleSendText[1] = (int) ((int)(currentDistance*0.55865922)*(Double.parseDouble(String.valueOf(binding.etnSetMaxSpeed.getText()))/200));
                                            // 角度
                                            bleSendText[2] = getValueByPercentage(convertValue((int) currentAngle), Integer.parseInt(String.valueOf(binding.etnSetTurnSensitive.getText())));
                                        }
                                        bleSendText[4] = 1; // 提示有訊息要發送
                                    }
                                });
                            } else if (bleSendText[0] != 0) {
                                bleSendText[0] = 0; // 停止前進後退
                                bleSendText[1] = 0; // 速度等於0
                                bleSendText[2] = 90; // 角度等於90
                                bleSendText[4] = 1; // 提示有訊息要發送
                            }
                            break;
                    }
                }
            });
        }catch (Exception e){
            e.printStackTrace();
        }

        // 資料預載入
        // 建立視圖元件
        View[] viewArray = new View[10];
        viewArray[0] = binding.etnSunMaxW;
        viewArray[1] = binding.etnWindMaxW;
        viewArray[2] = binding.etnRcVMax;
        viewArray[3] = binding.etnRcVMin;
        viewArray[4] = binding.etnRcF;
        viewArray[5] = binding.etnBatVMax;
        viewArray[6] = binding.etnBatVMin;
        viewArray[7] = binding.etnBatMah;
        viewArray[8] = binding.etnSetMaxSpeed;
        viewArray[9] = binding.etnSetTurnSensitive;
        // 將元件轉換成 EditText
        editTexts = new EditText[viewArray.length];
        for (int i = 0; i < viewArray.length; i++) editTexts[i] = (EditText) viewArray[i];

        mUserSetData = new UserSetData(this);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 判斷是否存過資料
                if(mUserSetData.read("User", "set?").equals("true")){
                    // 將先前的資料設定複寫至介面
                    for(int i = 0; i < index.length; i++) editTexts[i].setText(mUserSetData.read("User", index[i]));
                }else{
                    // APP初次運行儲存資料
                    mUserSetData.write("User", "set?", "true");
                    for(int i = 0; i < index.length; i++) mUserSetData.write("User", index[i], editTexts[i].getText().toString());
                    // 存一份原先資料
                    mUserSetData.write("oldUser", "set?", "true");
                    for(int i = 0; i < index.length; i++) mUserSetData.write("oldUser", index[i], editTexts[i].getText().toString());
                }
                upUI();
            }
        });
    }

    /**更新元件**/
    private void upUI(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 更新元件資料
                binding.textRcMah.setText(mUserSetData.read("User", index[4]) + " mAh");
                binding.textBatMah.setText(mUserSetData.read("User", index[7]) + " mAh");
            }
        });
    }

    @Override
    public void onClick(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        switch (v.getId()) {
            // 藍芽連線按鈕
            case R.id.btn_bluetooth:
                blueToothStartUsing();
                break;
            // 訊息按鈕
            case R.id.btn_send:
                //sendMessage("ForPhone：Connect ok");
                break;
            // 設定按鈕
            case R.id.btn_set:
                binding.btnSet.setVisibility(View.GONE);
                binding.btnConsole.setVisibility(View.VISIBLE);
                binding.rocker.setVisibility(View.INVISIBLE);
                binding.ccConsole.setVisibility(View.GONE);
                binding.ccSet.setVisibility(View.VISIBLE);
                break;
            // 保存按鈕
            case R.id.btn_set_save:
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        binding.btnConsole.setVisibility(View.GONE);
                        binding.btnSet.setVisibility(View.VISIBLE);
                        binding.ccSet.setVisibility(View.GONE);
                        binding.rocker.setVisibility(View.VISIBLE);
                        binding.ccConsole.setVisibility(View.VISIBLE);
                        // 儲存設定資料
                        mUserSetData.write("User", "set?", "true");
                        for(int i = 0; i < index.length; i++){
                            // 判斷輸入框是否是空值，是的話寫入預設值
                            if(editTexts[i].getText().toString().equals(""))editTexts[i].setText(mUserSetData.read("oldUser", index[i]));
                            mUserSetData.write("User", index[i], editTexts[i].getText().toString());
                        }
                        Toast.makeText(getApplicationContext(),"-設定已保存-",Toast.LENGTH_SHORT).show();
                        upUI();
                    }
                });
                break;
            // 重置按鈕
            case R.id.btn_set_rst:
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 判斷是否存過資料
                        if(mUserSetData.read("oldUser", "set?").equals("true")){
                            // 將先前的資料設定複寫至介面
                            for(int i = 0; i < index.length; i++) editTexts[i].setText(mUserSetData.read("oldUser", index[i]));
                        }
                        upUI();
                    }
                });
                break;
            // 控制台按鈕
            case R.id.btn_console:
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 判斷是否存過資料
                        if(mUserSetData.read("User", "set?").equals("true")){
                            // 將先前的資料設定複寫至介面
                            for(int i = 0; i < index.length; i++) editTexts[i].setText(mUserSetData.read("User", index[i]));
                        }
                        binding.btnConsole.setVisibility(View.GONE);
                        binding.btnSet.setVisibility(View.VISIBLE);
                        binding.ccSet.setVisibility(View.GONE);
                        binding.rocker.setVisibility(View.VISIBLE);
                        binding.ccConsole.setVisibility(View.VISIBLE);
                        upUI();
                    }
                });
                break;
            // 上升按鈕
            case R.id.btn_console_up:
                bleSendText[3] = 1;
                bleSendText[4] = 1; // 提示有訊息要發送
                break;
            // 下降按鈕
            case R.id.btn_console_dn:
                bleSendText[3] = 2;
                bleSendText[4] = 1; // 提示有訊息要發送
                break;
        }
    }

    /**點擊空白區域隱藏軟鍵盤**/
    @Override
    public boolean dispatchTouchEvent(MotionEvent me) {
        if (me.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (isShouldHideKeyboard(v, me)) {
                hideKeyboard(v.getWindowToken());   // 收起鍵盤
            }
        }
        return super.dispatchTouchEvent(me);
    }

    /**關閉程式初始化介面動畫(多線程)*/
    private Runnable logClose=new Runnable(){
        @Override
        public void run() {
            binding.ccLog.setVisibility(View.INVISIBLE);
            binding.ccUi.setVisibility(View.VISIBLE);
        }
    };

    /**藍芽訊息接收(多線程)*/
    Thread receiveBlePost = new Thread(new Runnable() {
        @Override
        public void run() {
            while (true)
                while (isBlePostRunning){
                    try {
                        if (is.available() > 0){
                            receiveMessages();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // 太陽能數值
                                    binding.textSunV.setText(String.format("%04.1f", Float.parseFloat(receiveStrArray[0])));
                                    binding.textSunA.setText(String.format("%05.3f", Float.parseFloat(receiveStrArray[1])));
                                    binding.textSunW.setText(String.format("%04.1f", Float.parseFloat(receiveStrArray[0]) * Float.parseFloat(receiveStrArray[1])));
                                    // 太陽能MP圖表
                                    double sunLevel = (Float.parseFloat(receiveStrArray[0]) * Float.parseFloat(receiveStrArray[1])) / Float.parseFloat(mUserSetData.read("User", "etnSunMaxW"));
                                    addData(binding.lineChartSun , (int)(sunLevel * 100));
                                    // 風力能數值
                                    binding.textWindV.setText(String.format("%04.1f", Float.parseFloat(receiveStrArray[3])));
                                    binding.textWindA.setText(String.format("%05.3f", Float.parseFloat(receiveStrArray[4])));
                                    binding.textWindW.setText(String.format("%04.1f", Float.parseFloat(receiveStrArray[3]) * Float.parseFloat(receiveStrArray[4])));
                                    // 風力能MP圖表
                                    double windLevel = (Float.parseFloat(receiveStrArray[3]) * Float.parseFloat(receiveStrArray[4])) / Float.parseFloat(mUserSetData.read("User", "etnWindMaxW"));
                                    addData(binding.lineChartWind , (int)(windLevel * 100));
                                    // 鋰電電池
                                    binding.textBatV.setText(String.format("%06.3f", Float.parseFloat(receiveStrArray[6])));
                                    binding.textBatA.setText(String.format("%04.2f", Float.parseFloat(receiveStrArray[7])));
                                    if(batAAdj != 0 && Float.parseFloat(receiveStrArray[7]) != 0) batAAdj = (Float.parseFloat(receiveStrArray[7]) + batAAdj) / 2;
                                    else if (Float.parseFloat(receiveStrArray[7]) != 0) batAAdj = Float.parseFloat(receiveStrArray[7]);
                                    else batAAdj = 0;
                                    // 鋰電電池水波容量圖
                                    double batteryLevel = ((Float.parseFloat(receiveStrArray[6]) - Float.parseFloat(mUserSetData.read("User", "etnBatVMin"))) / (Float.parseFloat(mUserSetData.read("User", "etnBatVMax")) - Float.parseFloat(mUserSetData.read("User", "etnBatVMin"))));
                                    if(batteryLevel > 0){
                                        mCircleViewBat.setmWaterLevel((float) batteryLevel);
                                        binding.textBatPercentage.setText((int)(batteryLevel*100) + "%");
                                    }else{
                                        mCircleViewBat.setmWaterLevel(0.00f);
                                        binding.textBatPercentage.setText("0%");
                                    }

                                    // 超級電容
                                    binding.textRcV.setText(String.format("%06.3f", Float.parseFloat(receiveStrArray[9])));
                                    binding.textRcA.setText(String.format("%04.2f", Float.parseFloat(receiveStrArray[10])));
                                    if(rcAAdj != 0 && Float.parseFloat(receiveStrArray[10]) != 0) rcAAdj = (Float.parseFloat(receiveStrArray[10]) + rcAAdj) / 2;
                                    else if (Float.parseFloat(receiveStrArray[10]) != 0) rcAAdj = Float.parseFloat(receiveStrArray[10]);
                                    else rcAAdj = 0;
                                    // 超級電容水波容量圖
                                    double rcLevel = ((Float.parseFloat(receiveStrArray[9]) - Float.parseFloat(mUserSetData.read("User", "etnRcVMin"))) / (Float.parseFloat(mUserSetData.read("User", "etnRcVMax")) - Float.parseFloat(mUserSetData.read("User", "etnRcVMin"))));
                                    if(rcLevel > 0){
                                        mCircleViewRc.setmWaterLevel((float) rcLevel);
                                        binding.textRcPercentage.setText((int)(rcLevel*100) + "%");
                                    }else{
                                        mCircleViewRc.setmWaterLevel(0.00f);
                                        binding.textRcPercentage.setText("0%");
                                    }

                                    // 鋰電電池可用時間
                                    double remainingCapacityBat = (batteryLevel*100) * Integer.parseInt(mUserSetData.read("User", "etnBatMah")) / 100.0;
                                    if (batAAdj != 0){
                                        double remainingTimeBat = remainingCapacityBat / batAAdj;
                                        binding.textBatTime.setText(String.format("可用時間約：%d分%02d秒", (int)remainingTimeBat/60, (int)remainingTimeBat%60));
                                    }
                                    // 超級電容可用時間
                                    double remainingCapacityRc = (batteryLevel*100) * Integer.parseInt(mUserSetData.read("User", "etnRcF")) / 100.0;
                                    if (rcAAdj != 0){
                                        double remainingTimeRc = remainingCapacityRc / rcAAdj;
                                        binding.textRcTime.setText(String.format("可用時間約：%d分%02d秒", (int)remainingTimeRc/60, (int)remainingTimeRc%60));
                                    }

                                    // 其他控件
                                    // 顯示上升
                                    if(Integer.parseInt(receiveStrArray[12]) / 1000 == 1){
                                        binding.btnConsoleUp.setVisibility(View.VISIBLE);
                                        binding.btnConsoleDn.setVisibility(View.INVISIBLE);
                                    }
                                    // 顯示下降
                                    else if(Integer.parseInt(receiveStrArray[12]) / 2000 == 1){
                                        binding.btnConsoleUp.setVisibility(View.INVISIBLE);
                                        binding.btnConsoleDn.setVisibility(View.VISIBLE);
                                    // 全部顯示
                                    }else{
                                        binding.btnConsoleUp.setVisibility(View.VISIBLE);
                                        binding.btnConsoleDn.setVisibility(View.VISIBLE);
                                    }
                                }
                            });
                            connect = true;
                        }
                    }catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        }
    });

    /**藍芽訊息發送(多線程)*/
    Thread sendBlePost = new Thread(new Runnable() {
        @Override
        public void run() {
            while (true)
                while (isBlePostRunning && bleSendText[4] == 1){
                    sendMessage(formatIntArray(bleSendText));
                    bleSendText[4] = 0;
                    sleep(100);
                }
        }
    });

    /**藍芽連線監控(多線程)*/
    private Runnable bleConnectionState=new Runnable(){
        @Override
        public void run() {
            // 檢測到沒有收到資料
            if (!connect){
                isBlePostRunning = false; // 關閉藍芽訊息監控
                Toast.makeText(getApplicationContext(),"裝置連線中斷",Toast.LENGTH_SHORT).show();
                Log.d(TAG + " BleConnect","裝置連線中斷");
                binding.btnBluetooth.setVisibility(View.VISIBLE); // 顯示配對按鈕
                binding.ccMod.setVisibility(View.INVISIBLE); // 隱藏模組布局
                binding.ccMenu.setVisibility(View.GONE); // 隱藏功能按鈕
                binding.rocker.setVisibility(View.INVISIBLE); // 隱藏搖桿
                binding.textBleStatus.setText("BlueTooth：Disconnect");
                // 清除藍芽設備資訊
                device = null;
                socket = null;
                is = null;
                os = null;
                sleep(1000); // 等待一會
                blueToothStartUsing(); // 重新連線
            }else{
                Log.v(TAG + " BleConnect","ok");
                connect = false;
                mHandler.postDelayed(bleConnectionState,3000); // 等待一會
            }
        }
    };

    /**藍芽初始化**/
    private void blueToothStartUsing(){
        // 檢查GPS權限
        if ( ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            // 檢查藍芽權限
            if (checkBluetoothPermission()){
                // 檢查設備是否支援藍芽
                if (bluetoothAdapter == null) {
                    Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
                    finish();
                }else{
                    // 檢查設備是否支援低功耗藍芽(BLE)
                    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                        Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
                        finish();
                    }else{
                        // 檢查是否開啟藍芽
                        if (!bluetoothAdapter.isEnabled()) {
                            // 彈出藍芽視窗(詢問是否打開藍芽)
                            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                            enableBtIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
                            startActivity(enableBtIntent);
                        }else{
                            // 開啟藍芽 Scanner 配對介面
                            Toast.makeText(view.getContext(),"配對設備",Toast.LENGTH_SHORT).show();
                            Intent bluetoothPicker = new Intent("android.bluetooth.devicepicker.action.LAUNCH");
                            startActivity(bluetoothPicker);
                        }
                    }
                }
            } else {
                Toast.makeText(view.getContext(),"無藍芽權限",Toast.LENGTH_SHORT).show();
                if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ) {
                    requestBluetoothPermission();
                }
            }
        }else{
            Toast.makeText(this,R.string.gps_not_supported,Toast.LENGTH_SHORT).show();
        }
    }

    /**藍芽廣播回傳**/
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // 檢查GPS權限
            if ( ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
            String action = intent.getAction();
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device==null)Toast.makeText(getApplicationContext(),"配對失敗或已離開配對介面 請重新配對設備",Toast.LENGTH_LONG).show();
            else{
                Log.v(TAG + " taggg",""+action);
                deviceName = device.getName(); // Device Name
                deviceAddress = device.getAddress(); // MAC Address
                try {
                    // 回傳選擇裝置進行配對
                    device.createBond();
                    // 與設備進行連線
                    blueConnection();
                    Log.v(TAG + " BleDeviceReceiver", "配對裝置:" + deviceName + " 位址:" + deviceAddress);
                } catch (Exception e) {
                    Log.e(TAG + " CreateBondError", e.getMessage());
                }
            }
        }
    };

    /**藍芽連線**/
    private void blueConnection (){
        if ( ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        try {
            deviceUUid = device.getUuids();
            Log.v(TAG + " BleUUid",""+deviceUUid[0].getUuid());
            Log.v(TAG + " BleUUidSize",""+deviceUUid.length);
            if(socket==null){
                // 連線方法1(不安全的連線)
                //socket=device.createInsecureRfcommSocketToServiceRecord(deviceUUid[0].getUuid());
                // 連線方法2(安全的連線)
                socket=device.createRfcommSocketToServiceRecord(deviceUUid[0].getUuid());
                // 迴圈嘗試進行連線
                while(!socket.isConnected()){
                    try {
                        socket.connect();
                        Log.d(TAG + " BleConnectState",""+socket.isConnected());
                        if(socket.isConnected()){
                            Toast.makeText(getApplicationContext(),"連線成功",Toast.LENGTH_SHORT).show();
                            binding.textBleStatus.setText("BlueTooth：Connected " + deviceAddress);
                            os=socket.getOutputStream(); // 輸入流
                            is=socket.getInputStream(); // 輸出流
                            isBlePostRunning = true; // 啟動藍芽訊息接收監控器
                            connect = true; // 變更連線狀態
                            mHandler.post(bleConnectionState); // 啟動藍芽連線狀態監控
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    binding.btnBluetooth.setVisibility(View.GONE); // 隱藏配對按鈕
                                    binding.ccMod.setVisibility(View.VISIBLE); // 顯示模組布局
                                    binding.ccMenu.setVisibility(View.VISIBLE); // 顯示功能按鈕
                                    binding.rocker.setVisibility(View.VISIBLE); // 顯示搖桿
                                }
                            });
                        }
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(),"裝置連線錯誤 請重新配對設備",Toast.LENGTH_LONG).show();
                        socket = null;
                        break;
                    }
                    // sleep(2000);
                }
            }
        }
        catch (Exception e){
            Log.d(TAG + " BleSocket Error",""+e);
        }
    }

    /**向藍芽裝置發送信息**/
    private void sendMessage(String message) {
        byte[] send = message.getBytes();
        try {
            os.write(send);
            Log.i(TAG, " SendMessages: " + message);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    binding.textSendOut.setText("Send Out：" + message);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, " Problem sending message.", e);
        }
    }

    /**接收藍芽裝置信息並格式化**/
    private void receiveMessages() {
        int num_bytes;
        byte[] buffer = new byte[CHUNK_SIZE];
        try {
            num_bytes = is.read(buffer);
            Log.v(TAG, " ReceivingMessages NumBytes: " + num_bytes);

            if (num_bytes > 0) {
                byte[] message_bytes = new byte[num_bytes];
                System.arraycopy(buffer, 0, message_bytes, 0, num_bytes);
                String message = new String(message_bytes);
                receiveStrArray = message.split(","); // 將數值轉換成陣列
                if( Integer.parseInt(receiveStrArray[receiveStrArray.length-1]) == num_bytes){
                    Log.i(TAG, " Message received: " + message);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            binding.textSendIn.setText("Send In：" + message);
                        }
                    });
                }else{
                    Arrays.fill(receiveStrArray, "0"); // 清除陣列元素
                    Log.i(TAG, " Message received: 數值異常");
                }

            }
        } catch (IOException e) {
            Log.e(TAG, " No longer connected.");
        }

    }

    /**確認藍芽權限**/
    private boolean checkBluetoothPermission() {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    /**請求藍芽權限**/
    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestBluetoothPermission() {
        String[] permissions = {android.Manifest.permission.BLUETOOTH_CONNECT};
        ActivityCompat.requestPermissions(this, permissions, BLUETOOTH_REQUEST_CODE);
    }

    /**載入 MPChart 圖表*/
    private void initChart(LineChart chart){
        chart.getDescription().setEnabled(false); // 設置不要圖表標籤
        chart.setTouchEnabled(false); //設置不可觸碰
        chart.setDragEnabled(false); // 設置不可互動
        // 設置單一線數據
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        chart.setData(data);
        // 設置左下角標籤
        Legend l =  chart.getLegend();
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.WHITE);

        // 設置Ｘ軸
        XAxis x =  chart.getXAxis();
        x.setTextColor(Color.WHITE);
        x.setDrawGridLines(true);//畫X軸線
        x.setPosition(XAxis.XAxisPosition.BOTTOM); // 把標籤放底部
        x.setLabelCount(5,true); // 設置顯示5個標籤
        // 設置X軸標籤內容物
        x.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return Math.round(value) + "s";
            }
        });
        // 設置Y軸
        YAxis y = chart.getAxisLeft();
        y.setTextColor(Color.WHITE);
        y.setDrawGridLines(true);
        y.setAxisMaximum(100); // 最高100
        y.setAxisMinimum(0); // 最低0
        // 設置Y軸標籤內容物
        y.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return Math.round(value) + "%";
            }
        });
        chart.getAxisRight().setEnabled(false); // 右邊Y軸不可視
        chart.setVisibleXRange(0, 120); // 設置顯示範圍
    }

    /**新增 MPChart 資料*/
    private void addData(LineChart chart ,float inputData){
        LineData data =  chart.getData(); // 取得原數據
        ILineDataSet set = data.getDataSetByIndex(0); // 取得曲線(因為只有一條，故為0，若有多條則需指定)
        if (set == null){
            set = createSet();
            data.addDataSet(set); // 如果是第一次跑則需要載入數據
        }
        data.addEntry(new Entry(set.getEntryCount(),inputData),0); // 新增數據點
        //
        data.notifyDataChanged();
        chart.notifyDataSetChanged();
        chart.setVisibleXRange(0, 120); // 設置可見範圍
        chart.moveViewToX(data.getEntryCount()); // 將可視焦點放在最新一個數據，使圖表可移動
    }

    /**設置 MPChart 數據線的樣式*/
    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "輸出能量");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.WHITE);
        set.setLineWidth(2);
        set.setDrawCircles(false);
        set.setFillColor(Color.GREEN);
        set.setFillAlpha(50);
        set.setDrawFilled(true);
        set.setValueTextColor(Color.WHITE);
        set.setDrawValues(false);
        return set;
    }

    /**將陣列轉為字串**/
    private static String formatIntArray(int[] intArray) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < intArray.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            int length = getLength(i);
            sb.append(String.format("%0" + length + "d", intArray[i]));
        }
        return sb.toString();
    }

    /**決定字串每個元素的長度**/
    private static int getLength(int index) {
        switch (index) {
            case 0:
            case 3:
            case 4:
                return 1;
            case 5:
                return 2;
            default:
                return 3;
        }
    }

    /**角度轉換**/
    private int convertValue(int value) {
        if (value > 0 && value < 180) {
            return 180 - value;
        } else if (value >= 180 && value <= 360) {
            return value - 180;
        }else return value;
    }

    /**舵機角度使用百分比控制靈敏度**/
    private static int getValueByPercentage(int value, int percentage) {
        int range = 180;
        int halfRange = range / 2;
        int offset = (int) (halfRange * (percentage / 100.0));
        int convertedValue;
        if (value <= halfRange) {
            convertedValue = halfRange - offset + (int) (value * (offset / (double) halfRange));
        } else {
            convertedValue = halfRange + offset - (int) ((range - value) * (offset / (double) halfRange));
        }
        return convertedValue;
    }

    /**根據EditText來判斷是否隱藏軟鍵盤**/
    private boolean isShouldHideKeyboard(View v, MotionEvent event) {
        if (v != null && (v instanceof EditText)) {
            int[] l = {0, 0};
            v.getLocationInWindow(l);
            int left = l[0],
                    top = l[1],
                    bottom = top + v.getHeight(),
                    right = left + v.getWidth();
            if (event.getX() > left && event.getX() < right
                    && event.getY() > top && event.getY() < bottom) {
                return false;
            } else {
                return true;
            }
        }
        // 如果焦点不是EditText则忽略
        return false;
    }

    /**取得InputMethodManager隱藏軟鍵盤**/
    private void hideKeyboard(IBinder token) {
        if (token != null) {
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    /**取得目前應用版本號**/
    public static String getVersionName(Context context) {
        String versionName = "";
        try {
            PackageInfo manager = context.getPackageManager().getPackageInfo(context.getPackageName(),  0 );
            versionName = manager.versionName;
        }catch(Exception ex) {
            ex.printStackTrace();
        }
        return versionName;
    }

    /**按下返回鍵**/
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            ConfirmExit(); // 按返回鍵，則執行退出確認
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**退出確認框**/
    public void ConfirmExit(){
        AlertDialog.Builder ad=new AlertDialog.Builder(MainActivity.this);
        ad.setTitle("離開");
        ad.setMessage("確定要離開此程式嗎?");
        ad.setPositiveButton("是", new DialogInterface.OnClickListener() { // 退出按鈕
            public void onClick(DialogInterface dialog, int i) {
                // TODO Auto-generated method stub
                MainActivity.this.finish(); // 關閉activity
            }
        });
        ad.setNegativeButton("否",new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                // 不退出不用執行任何操作
            }
        });
        ad.show(); // 顯示對話框
    }

    /**將在程式關閉前運行**/
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清除裝置資料
        socket = null;
        is = null;
        os = null;
        Toast.makeText(getApplicationContext(),"藍芽已斷開",Toast.LENGTH_SHORT).show();
        // 清除水波紋進度條資料
        mCircleViewRc.stopWave();
        mCircleViewRc = null;
        // 清除 Handler 避免產生記憶體溢出
        if ( mHandler != null ) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

}