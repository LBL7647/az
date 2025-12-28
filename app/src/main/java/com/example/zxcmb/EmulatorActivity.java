package com.example.zxcmb;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.navi.AMapNaviView;
import com.amap.api.navi.enums.IconType;
import com.amap.api.navi.enums.NaviType;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.NaviInfo;
import com.amap.api.navi.model.NaviLatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 导航模拟页面（集成蓝牙数据发送功能）
public class EmulatorActivity extends BaseActivity implements BluetoothSerialUtil.OnConnectionStateListener {
    // 导航信息展示控件
    private TextView tvRoadName, tvRemainingDistance, tvRemainingTime, tvTurnHint;
    // 蓝牙数据发送控件
    private EditText etBluetoothSend;
    private Button btnBluetoothSend;
    // 蓝牙通信工具类
    private BluetoothSerialUtil bluetoothUtil;
    private String bluetoothDeviceAddress;

    // 导航数据定时发送相关
    private Handler sendHandler;
    private Runnable sendRunnable;
    private static final long SEND_INTERVAL = 2000; // 发送间隔2秒
    private boolean isNaviStarted = false; // 导航启动标记

    // 导航参数存储
    private int mSelectedNaviMode = NaviType.EMULATOR; // 默认模拟导航
    private List<NaviLatLng> mStartList = new ArrayList<>(); // 起点列表
    private List<NaviLatLng> mEndList = new ArrayList<>(); // 终点列表
    private List<NaviLatLng> mWayPointList = new ArrayList<>(); // 途经点列表
    private NaviInfo currentNaviInfo; // 当前导航信息

    // 页面创建初始化
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_basic_navi);

        // 初始化导航视图
        mAMapNaviView = findViewById(R.id.navi_view);
        mAMapNaviView.onCreate(savedInstanceState);
        mAMapNaviView.setAMapNaviViewListener(this);

        // 初始化所有控件
        initAllViews();
        // 接收跳转传递的参数
        receiveIntentParams();
        // 初始化导航坐标点
        initNaviPoints();
        // 初始化蓝牙连接
        initBluetooth();
        // 初始化导航数据发送任务
        initNaviDataSender();
    }

    // 初始化页面控件及点击事件
    private void initAllViews() {
        // 绑定导航信息控件
        tvRoadName = findViewById(R.id.tv_road_name);
        tvRemainingDistance = findViewById(R.id.tv_remaining_distance);
        tvRemainingTime = findViewById(R.id.tv_remaining_time);
        tvTurnHint = findViewById(R.id.tv_turn_hint);

        // 绑定蓝牙发送控件
        etBluetoothSend = findViewById(R.id.et_bluetooth_send);
        btnBluetoothSend = findViewById(R.id.btn_bluetooth_send);

        // 蓝牙手动发送按钮点击事件
        btnBluetoothSend.setOnClickListener(v -> {
            String sendData = etBluetoothSend.getText().toString().trim();
            if (sendData.isEmpty()) {
                Toast.makeText(this, "请输入发送内容", Toast.LENGTH_SHORT).show();
                return;
            }
            // 蓝牙已连接则发送数据
            if (bluetoothUtil != null && bluetoothUtil.isConnected()) {
                boolean isSuccess = bluetoothUtil.sendData(sendData, this);
                if (isSuccess) {
                    etBluetoothSend.setText("");
                    Log.d("BluetoothSend", "手动发送数据成功：" + sendData);
                }
            } else {
                Toast.makeText(this, "蓝牙未连接", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 接收跳转传递的参数
    private void receiveIntentParams() {
        Intent intent = getIntent();
        // 获取导航模式
        mSelectedNaviMode = intent.getIntExtra("navi_mode", NaviType.EMULATOR);
        // 获取蓝牙设备地址
        bluetoothDeviceAddress = intent.getStringExtra("bluetooth_address");
        Log.d("NaviMode", mSelectedNaviMode == NaviType.EMULATOR ? "模拟导航模式" : "GPS导航模式");
    }

    // 初始化蓝牙连接
    private void initBluetooth() {
        // 获取蓝牙工具类实例
        bluetoothUtil = BluetoothSerialUtil.getInstance(this);
        // 确定要连接的蓝牙地址（优先最后连接的，其次传入的，最后默认）
        String connectAddress = BluetoothSerialUtil.LAST_CONNECTED_ADDRESS.isEmpty() ?
                (bluetoothDeviceAddress == null || bluetoothDeviceAddress.isEmpty() ?
                        "98:DA:20:09:25:E9" : bluetoothDeviceAddress) :
                BluetoothSerialUtil.LAST_CONNECTED_ADDRESS;

        // 未连接时尝试连接
        if (!bluetoothUtil.isConnected()) {
            new Thread(() -> {
                boolean isConnected = bluetoothUtil.connectDevice(connectAddress, this, this);
                runOnUiThread(() -> {
                    if (!isConnected) {
                        Toast.makeText(this, "蓝牙连接失败，将尝试默认地址", Toast.LENGTH_SHORT).show();
                        // 尝试默认地址重试
                        if (!BluetoothSerialUtil.LAST_CONNECTED_ADDRESS.equals("98:DA:20:09:25:E9")) {
                            new Thread(() -> {
                                boolean retryConnected = bluetoothUtil.connectDevice("98:DA:20:09:25:E9", this, this);
                                runOnUiThread(() -> {
                                    if (!retryConnected) {
                                        Toast.makeText(this, "默认地址连接也失败", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }).start();
                        }
                    }
                });
            }).start();
        }
    }

    // 初始化导航起点和终点
    private void initNaviPoints() {
        // 获取跳转传递的坐标，无则使用默认值
        double startLat = getIntent().getDoubleExtra("start_lat", 26.032567);
        double startLng = getIntent().getDoubleExtra("start_lng", 119.205788);
        double endLat = getIntent().getDoubleExtra("end_lat", 26.179888);
        double endLng = getIntent().getDoubleExtra("end_lng", 119.394084);

        // 添加起点和终点
        mStartList.add(new NaviLatLng(startLat, startLng));
        mEndList.add(new NaviLatLng(endLat, endLng));
    }

    // 初始化导航数据定时发送任务
    private void initNaviDataSender() {
        sendHandler = new Handler(Looper.getMainLooper());
        sendRunnable = new Runnable() {
            @Override
            public void run() {
                // 导航已启动且蓝牙已连接时发送数据
                if (isNaviStarted && bluetoothUtil != null && bluetoothUtil.isConnected() && currentNaviInfo != null) {
                    try {
                        // 提取导航信息文本
                        String roadName = tvRoadName.getText().toString().replace("当前道路：", "").trim();
                        String remainDist = tvRemainingDistance.getText().toString()
                                .replace("剩余距离：", "")
                                .trim();
                        String remainTime = tvRemainingTime.getText().toString()
                                .replace("剩余时间：", "")
                                .replace("分", ".")
                                .replace("秒", "")
                                .trim();

                        // 获取转向类型文本
                        String turnValue = getTurnDirectionText(currentNaviInfo.getIconType());
                        int turnDist = currentNaviInfo.getCurStepRetainDistance();

                        // 构造发送数据格式
                        String currentNaviData = String.format(
                                Locale.getDefault(),
                                "%s,%s,%s,%s,%d\r\n",
                                roadName, turnValue, remainDist, remainTime, turnDist
                        );

                        // 发送导航数据
                        boolean isSuccess = bluetoothUtil.sendData(currentNaviData, EmulatorActivity.this);
                        if (isSuccess) {
                            Log.d("NaviSend", (mSelectedNaviMode == NaviType.EMULATOR ? "模拟" : "GPS") + "数据发送成功：" + currentNaviData);
                        } else {
                            Log.e("NaviSend", (mSelectedNaviMode == NaviType.EMULATOR ? "模拟" : "GPS") + "数据发送失败");
                        }
                    } catch (Exception e) {
                        Log.e("NaviSend", "发送数据异常：" + e.getMessage());
                    }
                }
                // 循环执行发送任务
                sendHandler.postDelayed(this, SEND_INTERVAL);
            }
        };
    }

    // 转换转向类型为文本描述
    private String getTurnDirectionText(int iconType) {
        switch (iconType) {
            case IconType.STRAIGHT: return "直行";
            case IconType.LEFT: return "左转";
            case IconType.RIGHT: return "右转";
            case IconType.LEFT_FRONT: return "左前方转弯";
            case IconType.RIGHT_FRONT: return "右前方转弯";
            case IconType.LEFT_BACK: return "左后方转弯";
            case IconType.RIGHT_BACK: return "右后方转弯";
            case IconType.LEFT_TURN_AROUND: return "左转掉头";
            case IconType.ENTER_ROUNDABOUT: return "进入环岛";
            case IconType.OUT_ROUNDABOUT: return "驶出环岛";
            case IconType.ARRIVED_DESTINATION: return "到达目的地";
            default: return "前方行驶";
        }
    }

    // 导航信息更新回调
    @Override
    public void onNaviInfoUpdate(NaviInfo info) {
        super.onNaviInfoUpdate(info);
        if (info == null) return;

        // 保存当前导航信息
        currentNaviInfo = info;

        // 更新导航信息UI
        tvRoadName.setText("当前道路：" + (info.getCurrentRoadName() != null ? info.getCurrentRoadName().trim() : "无名道路"));

        // 显示剩余距离
        int remainDist = info.getPathRetainDistance();
        tvRemainingDistance.setText(remainDist >= 1000
                ? String.format(Locale.getDefault(), "剩余距离：%.1f公里", remainDist / 1000.0)
                : "剩余距离：" + remainDist + "米");

        // 显示剩余时间
        int remainTime = info.getPathRetainTime();
        tvRemainingTime.setText(String.format(Locale.getDefault(), "剩余时间：%d分%d秒",
                remainTime / 60, remainTime % 60));

        // 显示转向提示
        int turnDist = info.getCurStepRetainDistance();
        String direction = getTurnDirectionText(info.getIconType());
        tvTurnHint.setText(turnDist > 0
                ? String.format(Locale.getDefault(), "前方提示：%d米后%s", turnDist, direction)
                : "前方提示：" + direction);
    }

    // 导航初始化成功回调
    @Override
    public void onInitNaviSuccess() {
        super.onInitNaviSuccess();
        int strategy = 0;
        try {
            // 设置导航路线策略
            strategy = mAMapNavi.strategyConvert(true, false, false, false, false);
        } catch (Exception e) {
            Log.e("NaviError", "路线计算失败：" + e.getMessage());
        }
        // 计算驾车导航路线
        mAMapNavi.calculateDriveRoute(mStartList, mEndList, mWayPointList, strategy);
    }

    // 路线计算成功回调
    @Override
    public void onCalculateRouteSuccess(AMapCalcRouteResult result) {
        super.onCalculateRouteSuccess(result);
        // 启动导航
        mAMapNavi.startNavi(mSelectedNaviMode);
        // 标记导航已启动
        isNaviStarted = true;
        // 启动导航数据定时发送
        sendHandler.postDelayed(sendRunnable, SEND_INTERVAL);
        Log.d("NaviStart", "导航启动成功，开始蓝牙数据发送");
    }

    // 蓝牙断开连接回调
    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "蓝牙连接已断开", Toast.LENGTH_SHORT).show();
            // 尝试重新连接蓝牙
            if (!bluetoothUtil.isConnected()) {
                new Thread(() -> {
                    bluetoothUtil.connectDevice(BluetoothSerialUtil.LAST_CONNECTED_ADDRESS, this, this);
                }).start();
            }
        });
    }

    // 页面恢复时执行
    @Override
    protected void onResume() {
        super.onResume();
        mAMapNaviView.onResume();
        // 恢复导航数据发送
        if (isNaviStarted) {
            sendHandler.postDelayed(sendRunnable, SEND_INTERVAL);
        }
    }

    // 页面暂停时执行
    @Override
    protected void onPause() {
        super.onPause();
        mAMapNaviView.onPause();
        // 暂停导航数据发送
        sendHandler.removeCallbacks(sendRunnable);
    }

    // 页面销毁时执行
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAMapNaviView.onDestroy();
        // 停止并销毁导航
        if (mAMapNavi != null) {
            mAMapNavi.stopNavi();
            mAMapNavi.destroy();
        }
        // 停止所有导航数据发送任务
        sendHandler.removeCallbacksAndMessages(null);
        isNaviStarted = false;
    }
}
