package com.example.zxcmb;

import android.os.Bundle;
import android.widget.TextView;

import com.amap.api.navi.enums.NaviType;
import com.amap.api.navi.model.NaviLatLng;

// 基础导航功能页面
public class BasicNaviActivity extends BaseActivity {

    // 导航信息展示控件
    private TextView tvRoadName;
    private TextView tvRemainingDistance;
    private TextView tvRemainingTime;
    private TextView tvTurnHint;

    // 页面创建时执行
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置布局文件
        setContentView(R.layout.activity_basic_navi);

        // 绑定导航视图并设置监听
        mAMapNaviView = findViewById(R.id.navi_view);
        mAMapNaviView.setAMapNaviViewListener(this);

        // 绑定导航信息展示控件
        tvRoadName = findViewById(R.id.tv_road_name);
        tvRemainingDistance = findViewById(R.id.tv_remaining_distance);
        tvRemainingTime = findViewById(R.id.tv_remaining_time);
        tvTurnHint = findViewById(R.id.tv_turn_hint);

        // 获取跳转传递的导航坐标和模式参数
        double startLat = getIntent().getDoubleExtra("start_lat", 0);
        double startLng = getIntent().getDoubleExtra("start_lng", 0);
        double endLat = getIntent().getDoubleExtra("end_lat", 0);
        double endLng = getIntent().getDoubleExtra("end_lng", 0);
        int naviMode = getIntent().getIntExtra("navi_mode", NaviType.EMULATOR);

        // 清空起点终点集合并设置新的起点终点
        sList.clear();
        eList.clear();
        sList.add(new NaviLatLng(startLat, startLng));
        eList.add(new NaviLatLng(endLat, endLng));

        // 设置导航路线计算策略
        int strategy = 0;
        try {
            // 策略参数：高速优先、避免收费、避免拥堵、避免高速、避免轮渡
            strategy = mAMapNavi.strategyConvert(true, false, false, false, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 计算驾车导航路线
        mAMapNavi.calculateDriveRoute(sList, eList, mWayPointList, strategy);

        // 启动导航
        mAMapNavi.startNavi(naviMode);
    }

    // 导航信息更新时回调
    @Override
    public void onNaviInfoUpdate(com.amap.api.navi.model.NaviInfo naviinfo) {
        super.onNaviInfoUpdate(naviinfo);
        // 更新导航信息展示
//        if (naviinfo != null) {
//            if (tvRoadName != null) {
//                tvRoadName.setText("当前道路：" + naviinfo.getRoadName());
//            }
//            if (tvRemainingDistance != null) {
//                tvRemainingDistance.setText("剩余距离：" + naviinfo.getPathRemainDistance() + "米");
//            }
//            if (tvRemainingTime != null) {
//                tvRemainingTime.setText("剩余时间：" + naviinfo.getPathRemainTime() + "秒");
//            }
//        }
    }

    // 导航语音文字更新时回调
    @Override
    public void onGetNavigationText(String text) {
        super.onGetNavigationText(text);
        // 更新转向提示文字
        if (tvTurnHint != null) {
            tvTurnHint.setText("前方提示：" + text);
        }
    }
}
