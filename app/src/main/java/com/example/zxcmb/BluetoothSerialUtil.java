package com.example.zxcmb;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

// 蓝牙串口通信工具类（单例模式）
public class BluetoothSerialUtil {
    // SPP蓝牙串口UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // 单例实例
    private static BluetoothSerialUtil instance;

    // 全局静态变量（保存蓝牙连接状态）
    public static String LAST_CONNECTED_ADDRESS = ""; // 最后连接设备的MAC地址
    public static String LAST_CONNECTED_NAME = "";    // 最后连接设备的名称
    public static boolean IS_CONNECTED = false;       // 当前连接状态

    // 蓝牙通信核心对象
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    // 连接状态监听
    private OnConnectionStateListener connectionListener;
    // 主线程处理器（用于更新UI）
    private Handler mainHandler;
    // 应用上下文
    private Context appContext;

    // 连接状态监听接口
    public interface OnConnectionStateListener {
        void onDisconnected();
    }

    // 获取单例实例
    public static synchronized BluetoothSerialUtil getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothSerialUtil(context.getApplicationContext());
        }
        return instance;
    }

    // 私有构造方法
    private BluetoothSerialUtil(Context context) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // 初始化主线程Handler
        mainHandler = new Handler(Looper.getMainLooper());
        // 保存应用上下文
        appContext = context;
        // 检查设备是否支持蓝牙
        if (bluetoothAdapter == null) {
            showToast("设备不支持蓝牙");
        }
    }

    // 在主线程显示Toast提示
    private void showToast(String message) {
        mainHandler.post(() -> {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show();
        });
    }

    // 连接蓝牙设备
    public boolean connectDevice(String deviceAddress, Context context, OnConnectionStateListener listener) {
        this.connectionListener = listener;
        // 初始化连接状态为未连接
        IS_CONNECTED = false;

        // 检查蓝牙是否可用
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            showToast("蓝牙未开启");
            return false;
        }

        try {
            // 获取远程蓝牙设备
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            // 停止蓝牙扫描
            bluetoothAdapter.cancelDiscovery();
            // 创建蓝牙Socket连接
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            bluetoothSocket.connect();

            // 获取输入输出流
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();

            // 更新全局连接状态
            IS_CONNECTED = true;
            LAST_CONNECTED_ADDRESS = deviceAddress;
            LAST_CONNECTED_NAME = device.getName() != null ? device.getName() : "未知设备";

            // 启动心跳检测线程
            startHeartbeatThread(context);
            showToast("连接成功");
            return true;

        } catch (IOException e) {
            // 连接失败关闭资源
            closeConnection();
            showToast("连接失败：" + e.getMessage());
            return false;
        }
    }

    // 心跳检测线程（维持连接状态）
    private void startHeartbeatThread(Context context) {
        new Thread(() -> {
            byte[] buffer = new byte[1];
            // 循环检测连接状态
            while (IS_CONNECTED) {
                try {
                    // 读取输入流数据
                    if (inputStream != null && inputStream.available() > 0) {
                        inputStream.read(buffer);
                    }
                    // 1秒检测一次
                    Thread.sleep(1000);
                } catch (IOException e) {
                    // 连接断开更新状态
                    IS_CONNECTED = false;
                    // 主线程执行断开回调
                    mainHandler.post(() -> {
                        if (connectionListener != null) {
                            connectionListener.onDisconnected();
                        }
                        showToast("蓝牙断开");
                    });
                    // 关闭连接资源
                    closeConnection();
                    break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // 发送数据（GB2312编码）
    public boolean sendData(String data, Context context) {
        // 检查连接状态
        if (outputStream == null || bluetoothSocket == null || !bluetoothSocket.isConnected()) {
            mainHandler.post(() -> {
                Toast.makeText(context, "蓝牙未连接，无法发送数据", Toast.LENGTH_SHORT).show();
            });
            return false;
        }

        try {
            // 使用GB2312编码发送数据
            outputStream.write(data.getBytes("GB2312"));
            // 强制刷新输出流
            outputStream.flush();
            return true;
        } catch (IOException e) {
            mainHandler.post(() -> {
                Toast.makeText(context, "数据发送失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
            return false;
        }
    }

    // 关闭蓝牙连接
    public void closeConnection() {
        // 更新连接状态
        IS_CONNECTED = false;
        try {
            // 关闭输入输出流和Socket
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 清空资源引用
            inputStream = null;
            outputStream = null;
            bluetoothSocket = null;
            connectionListener = null;
        }
    }

    // 判断当前是否连接
    public boolean isConnected() {
        return IS_CONNECTED && bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    // 获取蓝牙适配器
    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    // 获取输出流（兼容旧逻辑）
    public OutputStream getOutputStream() {
        return outputStream;
    }
}
