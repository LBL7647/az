package com.example.zxcmb;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.constraintlayout.widget.ConstraintLayout;

// 主容器Activity（管理底部导航和Fragment切换）
public class MainContainerActivity extends AppCompatActivity {

    // 底部导航选中状态指示条
    private View homeIndicator;
    private View mineIndicator;
    // 底部导航文字控件
    private TextView homeText;
    private TextView mineText;

    // 导航文字颜色常量
    private final int COLOR_SELECTED = 0xFF2196F3; // 选中状态颜色（蓝色）
    private final int COLOR_UNSELECTED = 0xFF757575; // 未选中状态颜色（灰色）

    // 页面创建初始化
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 加载布局文件（必须先加载布局才能获取控件）
        setContentView(R.layout.activity_main_container);

        // 初始化底部导航控件
        initBottomNav();

        // 首次进入默认显示主页Fragment（避免重建时重复加载）
        if (savedInstanceState == null) {
            switchFragment(new HomeFragment());
        }
    }

    // 初始化底部导航控件及点击事件
    private void initBottomNav() {
        // 获取底部导航按钮根控件
        ConstraintLayout btnHome = findViewById(R.id.btn_home);
        ConstraintLayout btnMine = findViewById(R.id.btn_mine);

        // 检查控件是否存在，避免空指针
        if (btnHome == null || btnMine == null) {
            Log.e("MainContainer", "底部导航按钮控件找不到！检查布局ID是否正确");
            return;
        }

        // 获取导航按钮子控件（文字+指示条）
        // 主页按钮：第0个子控件是文字，第1个是选中指示条
        View homeTextView = btnHome.getChildAt(0);
        homeIndicator = btnHome.getChildAt(1);

        // 我的按钮：第0个子控件是文字，第1个是选中指示条
        View mineTextView = btnMine.getChildAt(0);
        mineIndicator = btnMine.getChildAt(1);

        // 类型转换并兜底处理（避免控件类型错误导致崩溃）
        if (homeTextView instanceof TextView) {
            homeText = (TextView) homeTextView;
        } else {
            Log.e("MainContainer", "主页文字控件不是TextView！");
            homeText = new TextView(this); // 创建兜底控件，避免null
        }

        if (mineTextView instanceof TextView) {
            mineText = (TextView) mineTextView;
        } else {
            Log.e("MainContainer", "我的文字控件不是TextView！");
            mineText = new TextView(this); // 创建兜底控件，避免null
        }

        // 指示条控件兜底处理
        if (homeIndicator == null) {
            homeIndicator = new View(this);
            homeIndicator.setVisibility(View.GONE);
        }
        if (mineIndicator == null) {
            mineIndicator = new View(this);
            mineIndicator.setVisibility(View.GONE);
        }

        // 设置默认选中状态（默认选中主页）
        updateBottomNavStatus(true);

        // 设置主页按钮点击事件
        btnHome.setOnClickListener(v -> {
            updateBottomNavStatus(true); // 更新为选中主页状态
            switchFragment(new HomeFragment()); // 切换到主页Fragment
        });

        // 设置我的按钮点击事件
        btnMine.setOnClickListener(v -> {
            updateBottomNavStatus(false); // 更新为选中我的状态
            switchFragment(new MineFragment()); // 切换到我的Fragment
        });
    }

    // 更新底部导航选中状态UI
    private void updateBottomNavStatus(boolean isHomeSelected) {
        // 检查控件是否为空，避免空指针
        if (homeText == null || mineText == null || homeIndicator == null || mineIndicator == null) {
            Log.e("MainContainer", "底部导航子控件为空，跳过状态更新");
            return;
        }

        if (isHomeSelected) {
            // 主页选中状态
            homeText.setTextColor(COLOR_SELECTED); // 文字设为选中色
            homeIndicator.setVisibility(View.VISIBLE); // 显示选中指示条
            homeIndicator.setBackgroundColor(COLOR_SELECTED); // 指示条设为选中色

            // 我的未选中状态
            mineText.setTextColor(COLOR_UNSELECTED); // 文字设为未选中色
            mineIndicator.setVisibility(View.INVISIBLE); // 隐藏选中指示条
        } else {
            // 我的选中状态
            mineText.setTextColor(COLOR_SELECTED); // 文字设为选中色
            mineIndicator.setVisibility(View.VISIBLE); // 显示选中指示条
            mineIndicator.setBackgroundColor(COLOR_SELECTED); // 指示条设为选中色

            // 主页未选中状态
            homeText.setTextColor(COLOR_UNSELECTED); // 文字设为未选中色
            homeIndicator.setVisibility(View.INVISIBLE); // 隐藏选中指示条
        }
    }

    // 切换Fragment（安全处理，避免崩溃）
    private void switchFragment(Fragment targetFragment) {
        // 检查Activity状态，避免销毁/保存状态后操作
        if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) {
            Log.e("MainContainer", "Activity状态异常，跳过Fragment切换");
            return;
        }

        try {
            // 获取Fragment管理器
            FragmentManager fragmentManager = getSupportFragmentManager();
            // 开启Fragment事务
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            // 替换容器中的Fragment
            transaction.replace(R.id.fragment_container, targetFragment);
            // 提交事务（允许状态丢失，避免状态保存后崩溃）
            transaction.commitNowAllowingStateLoss();
        } catch (Exception e) {
            Log.e("MainContainer", "Fragment切换失败：" + e.getMessage());
        }
    }
}
