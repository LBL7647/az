package com.example.zxcmb;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// 蓝牙设备管理页面
public class BluetoothDeviceActivity extends AppCompatActivity {
    // 页面控件声明
    private Button btnSearchBluetooth;
    private Button btnDisconnectBluetooth;
    private ListView lvBluetoothDevices;
    private TextView tvConnectedDevice;
    private TextView tvEmptyView;
    private ProgressBar pbLoading;

    // 功能相关对象
    private InternalDeviceAdapter deviceAdapter;
    private BluetoothSerialUtil bluetoothUtil;

    // 权限请求常量
    private static final int BLUETOOTH_PERMISSION_CODE = 2;
    private static final String[] BLUETOOTH_PERMISSIONS = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };

    // 页面创建初始化
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_device);

        // 初始化蓝牙工具类
        bluetoothUtil = BluetoothSerialUtil.getInstance(this);
        // 初始化页面控件
        initViews();
        // 初始化蓝牙设备列表
        initBluetoothList();
        // 注册蓝牙相关广播
        registerBluetoothReceivers();

        // 自动刷新一次设备状态
        autoRefreshOnce();
    }

    // 绑定页面控件并设置点击事件
    private void initViews() {
        btnSearchBluetooth = findViewById(R.id.btn_search_bluetooth);
        btnDisconnectBluetooth = findViewById(R.id.btn_disconnect_bluetooth);
        lvBluetoothDevices = findViewById(R.id.lv_bluetooth_devices);
        tvConnectedDevice = findViewById(R.id.tv_connected_device);
        tvEmptyView = findViewById(R.id.tv_empty_view);
        pbLoading = findViewById(R.id.pb_loading);

        // 设置列表无数据时的占位提示
        lvBluetoothDevices.setEmptyView(tvEmptyView);

        // 搜索蓝牙按钮点击事件
        btnSearchBluetooth.setOnClickListener(v -> checkBluetoothPermissionAndSearch());

        // 断开连接按钮点击事件
        btnDisconnectBluetooth.setOnClickListener(v -> {
            if (BluetoothSerialUtil.IS_CONNECTED) {
                bluetoothUtil.closeConnection();
                refreshFromStaticState();
                Toast.makeText(this, "已断开连接", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 初始化蓝牙设备列表适配器
    private void initBluetoothList() {
        deviceAdapter = new InternalDeviceAdapter(this);
        lvBluetoothDevices.setAdapter(deviceAdapter);

        // 列表项点击事件（连接选中设备）
        lvBluetoothDevices.setOnItemClickListener((parent, view, position, id) -> {
            // 先断开已有连接
            if (BluetoothSerialUtil.IS_CONNECTED) {
                bluetoothUtil.closeConnection();
            }

            // 获取选中的蓝牙设备
            BluetoothDevice device = deviceAdapter.getItem(position);
            if (device == null) return;
            String address = device.getAddress();

            // 检查蓝牙连接权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "需要蓝牙连接权限", Toast.LENGTH_SHORT).show();
                return;
            }

            // 停止扫描提升连接速度
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            pbLoading.setVisibility(View.GONE);

            Toast.makeText(this, "正在连接: " + device.getName(), Toast.LENGTH_SHORT).show();

            // 连接蓝牙设备
            bluetoothUtil.connectDevice(address, this, () -> {
                runOnUiThread(() -> {
                    refreshFromStaticState();
                    Toast.makeText(this, "连接成功", Toast.LENGTH_SHORT).show();
                });
            });
            refreshFromStaticState();
        });
    }

    // 自动刷新设备状态和列表
    private void autoRefreshOnce() {
        refreshFromStaticState();
        refreshDeviceList();
    }

    // 根据蓝牙连接状态刷新页面显示
    private void refreshFromStaticState() {
        boolean isConnected = bluetoothUtil.isConnected();
        BluetoothSerialUtil.IS_CONNECTED = isConnected;

        // 获取状态卡片并更新样式
        CardView cardStatus = findViewById(R.id.card_status);
        if (isConnected) {
            tvConnectedDevice.setText("已连接：" + BluetoothSerialUtil.LAST_CONNECTED_NAME);
            btnDisconnectBluetooth.setEnabled(true);
            cardStatus.setCardBackgroundColor(0xFF4CAF50);
        } else {
            tvConnectedDevice.setText("未连接设备");
            btnDisconnectBluetooth.setEnabled(false);
            cardStatus.setCardBackgroundColor(0xFF2196F3);
        }
    }

    // 刷新蓝牙设备列表
    private void refreshDeviceList() {
        // 清空现有列表
        deviceAdapter.clear();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) return;

        // 蓝牙未开启则请求开启
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 100);
            return;
        }

        // 检查蓝牙扫描权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, BLUETOOTH_PERMISSIONS, BLUETOOTH_PERMISSION_CODE);
            return;
        }

        // 添加已配对设备到列表
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                deviceAdapter.addDevice(device);
            }
        }

        // 开始扫描新设备
        startDiscovery(bluetoothAdapter);
    }

    // 检查蓝牙权限后执行搜索
    private void checkBluetoothPermissionAndSearch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            boolean hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

            if (hasScan && hasConnect) {
                searchBluetoothDevices();
            } else {
                ActivityCompat.requestPermissions(this, BLUETOOTH_PERMISSIONS, BLUETOOTH_PERMISSION_CODE);
            }
        } else {
            searchBluetoothDevices();
        }
    }

    // 执行蓝牙设备搜索
    private void searchBluetoothDevices() {
        deviceAdapter.clear();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            startDiscovery(adapter);
        }
    }

    // 启动蓝牙扫描
    private void startDiscovery(BluetoothAdapter adapter) {
        // 停止正在进行的扫描
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }
        // 开始新的扫描
        adapter.startDiscovery();
        pbLoading.setVisibility(View.VISIBLE);
        Toast.makeText(this, "正在搜索...", Toast.LENGTH_SHORT).show();
    }

    // 蓝牙设备列表适配器（内部类）
    private class InternalDeviceAdapter extends BaseAdapter {
        private Context context;
        private List<BluetoothDevice> devices;

        // 适配器构造方法
        public InternalDeviceAdapter(Context context) {
            this.context = context;
            this.devices = new ArrayList<>();
        }

        // 添加设备到列表
        public void addDevice(BluetoothDevice device) {
            if (!devices.contains(device)) {
                devices.add(device);
                notifyDataSetChanged();
            }
        }

        // 清空设备列表
        public void clear() {
            devices.clear();
            notifyDataSetChanged();
        }

        // 获取列表数量
        @Override
        public int getCount() {
            return devices.size();
        }

        // 获取指定位置的设备
        @Override
        public BluetoothDevice getItem(int position) {
            return devices.get(position);
        }

        // 获取项ID
        @Override
        public long getItemId(int position) {
            return position;
        }

        // 构建列表项视图
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            // 复用视图优化性能
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_bluetooth_device, parent, false);
                holder = new ViewHolder();
                holder.ivIcon = convertView.findViewById(R.id.iv_device_icon);
                holder.tvName = convertView.findViewById(R.id.tv_device_name);
                holder.tvAddress = convertView.findViewById(R.id.tv_device_address);
                // 设置图片显示模式
                holder.ivIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                holder.ivIcon.setAdjustViewBounds(true);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            // 设置设备信息
            BluetoothDevice device = devices.get(position);
            String deviceName = device.getName();

            holder.tvName.setText(deviceName != null && !deviceName.isEmpty() ? deviceName : "未知设备");
            holder.tvAddress.setText(device.getAddress());

            // 根据设备名称设置不同图标
            if (deviceName != null && deviceName.contains("BT04-A")) {
                holder.ivIcon.setImageResource(R.drawable.ic_device_bike_meter);
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_device_bluetooth);
            }

            return convertView;
        }

        // 视图持有者类
        class ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            TextView tvAddress;
        }
    }

    // 注册蓝牙相关广播接收器
    private void registerBluetoothReceivers() {
        // 发现新设备广播
        IntentFilter foundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothFoundReceiver, foundFilter);

        // 设备断开连接广播
        IntentFilter disconnectFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothDisconnectReceiver, disconnectFilter);

        // 蓝牙状态变更广播
        IntentFilter stateFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, stateFilter);

        // 扫描完成广播
        IntentFilter finishFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryFinishedReceiver, finishFilter);
    }

    // 发现蓝牙设备广播接收器
    private final BroadcastReceiver bluetoothFoundReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    deviceAdapter.addDevice(device);
                }
            }
        }
    };

    // 蓝牙断开连接广播接收器
    private final BroadcastReceiver bluetoothDisconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                BluetoothSerialUtil.IS_CONNECTED = false;
                runOnUiThread(() -> refreshFromStaticState());
            }
        }
    };

    // 蓝牙状态变更广播接收器
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                    BluetoothSerialUtil.IS_CONNECTED = false;
                    runOnUiThread(() -> refreshFromStaticState());
                }
            }
        }
    };

    // 蓝牙扫描完成广播接收器
    private final BroadcastReceiver discoveryFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                pbLoading.setVisibility(View.GONE);
                if (deviceAdapter.getCount() == 0) {
                    Toast.makeText(context, "未找到设备", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    // 页面销毁时释放资源
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // 注销所有广播接收器
            unregisterReceiver(bluetoothFoundReceiver);
            unregisterReceiver(bluetoothDisconnectReceiver);
            unregisterReceiver(bluetoothStateReceiver);
            unregisterReceiver(discoveryFinishedReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 权限请求结果处理
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLUETOOTH_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                searchBluetoothDevices();
            } else {
                Toast.makeText(this, "缺少蓝牙权限", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
