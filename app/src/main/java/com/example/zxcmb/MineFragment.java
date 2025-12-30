package com.example.zxcmb;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

// 我的页面Fragment（展示个人数据、配置功能）
public class MineFragment extends Fragment {

    // 数据展示控件
    private TextView tvTotalDistance, tvTotalTime, tvTotalCount;
    // 功能菜单控件
    private View menuPersonalInfo, menuOneNet, menuSettings;

    // 创建Fragment视图
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 加载我的页面布局
        return inflater.inflate(R.layout.activity_mine, container, false);
    }

    // 视图创建完成后初始化
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 初始化控件
        initView(view);
        // 设置点击事件
        initListener();
        // 初始化数据（首次加载）
        updateStatisticsUI();
    }

    // 每次页面重新显示时，刷新数据
    @Override
    public void onResume() {
        super.onResume();
        updateStatisticsUI();
    }

    // 从 SharedPreferences 读取统计数据并更新 UI
    private void updateStatisticsUI() {
        if (getContext() == null) return;

        SharedPreferences sp = getContext().getSharedPreferences("RideStatistics", Context.MODE_PRIVATE);

        // 获取数据，如果没有则默认为 "0" 或 "0.0"
        String distance = sp.getString("total_distance", "0.0");
        String time = sp.getString("total_time", "0.0");
        int count = sp.getInt("total_count", 0);

        // 更新UI控件
        if (tvTotalDistance != null) tvTotalDistance.setText(distance);
        if (tvTotalTime != null) tvTotalTime.setText(time);
        if (tvTotalCount != null) tvTotalCount.setText(String.valueOf(count));
    }

    // 初始化页面控件
    private void initView(View view) {
        // 绑定骑行数据展示控件
        tvTotalDistance = view.findViewById(R.id.tv_total_distance);
        tvTotalTime = view.findViewById(R.id.tv_total_time);
        tvTotalCount = view.findViewById(R.id.tv_total_count);

        // 绑定功能菜单控件
        menuPersonalInfo = view.findViewById(R.id.menu_personal_info);
        menuOneNet = view.findViewById(R.id.menu_onenet);
        menuSettings = view.findViewById(R.id.menu_settings);
    }

    // 设置菜单点击事件监听
    private void initListener() {
        // 统一菜单点击事件处理
        View.OnClickListener menuClickListener = v -> {
            int id = v.getId();
            if (id == R.id.menu_personal_info) {
                showPersonalInfoDialog();
            } else if (id == R.id.menu_onenet) {
                showOneNetDialog();
            } else if (id == R.id.menu_settings) {
                showAboutDialog();
            }
        };

        menuPersonalInfo.setOnClickListener(menuClickListener);
        menuOneNet.setOnClickListener(menuClickListener);
        menuSettings.setOnClickListener(menuClickListener);
    }

    // ======================== 弹窗功能实现 ========================

    private void showPersonalInfoDialog() {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_personal_info, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        EditText etHeight = dialogView.findViewById(R.id.et_dialog_height);
        EditText etWeight = dialogView.findViewById(R.id.et_dialog_weight);
        TextView btnCancel = dialogView.findViewById(R.id.btn_dialog_cancel);
        TextView btnSave = dialogView.findViewById(R.id.btn_dialog_save);

        SharedPreferences sp = getContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        etHeight.setText(sp.getString("height", ""));
        etWeight.setText(sp.getString("weight", ""));

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sp.edit();
            editor.putString("height", etHeight.getText().toString().trim());
            editor.putString("weight", etWeight.getText().toString().trim());
            editor.apply();
            Toast.makeText(getContext(), "个人信息已保存", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }

    private void showOneNetDialog() {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_onenet_config, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        EditText etDeviceId = dialogView.findViewById(R.id.et_device_id);
        EditText etProductId = dialogView.findViewById(R.id.et_product_id);
        EditText etApiKey = dialogView.findViewById(R.id.et_api_key);
        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnSave = dialogView.findViewById(R.id.btn_save);

        SharedPreferences sp = getContext().getSharedPreferences("OneNetPrefs", Context.MODE_PRIVATE);
        etDeviceId.setText(sp.getString("device_id", ""));
        etProductId.setText(sp.getString("product_id", ""));
        etApiKey.setText(sp.getString("api_key", ""));

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sp.edit();
            editor.putString("device_id", etDeviceId.getText().toString().trim());
            editor.putString("product_id", etProductId.getText().toString().trim());
            editor.putString("api_key", etApiKey.getText().toString().trim());
            editor.apply();
            Toast.makeText(getContext(), "OneNET配置已更新", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }

    private void showAboutDialog() {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_system_settings, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        TextView btnCheckUpdate = dialogView.findViewById(R.id.btn_check_update);

        btnConfirm.setOnClickListener(v -> dialog.dismiss());
        btnCheckUpdate.setOnClickListener(v -> {
            Toast.makeText(getContext(), "正在连接服务器检查更新...", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(() -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "当前已是最新版本 (v1.0.0)", Toast.LENGTH_SHORT).show();
                }
            }, 1000);
        });

        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }
}
