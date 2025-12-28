package com.example.zxcmb;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import com.amap.api.maps.AMapException;
import com.amap.api.navi.AMapNavi;
import com.amap.api.navi.AMapNaviListener;
import com.amap.api.navi.AMapNaviView;
import com.amap.api.navi.AMapNaviViewListener;
import com.amap.api.navi.ParallelRoadListener;
import com.amap.api.navi.enums.AMapNaviParallelRoadStatus;
import com.amap.api.navi.model.AMapCalcRouteResult;
import com.amap.api.navi.model.AMapLaneInfo;
import com.amap.api.navi.model.AMapModelCross;
import com.amap.api.navi.model.AMapNaviCameraInfo;
import com.amap.api.navi.model.AMapNaviCross;
import com.amap.api.navi.model.AMapNaviLocation;
import com.amap.api.navi.model.AMapNaviRouteNotifyData;
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo;
import com.amap.api.navi.model.AMapServiceAreaInfo;
import com.amap.api.navi.model.AimLessModeCongestionInfo;
import com.amap.api.navi.model.AimLessModeStat;
import com.amap.api.navi.model.NaviInfo;
import com.amap.api.navi.model.NaviLatLng;

import java.util.ArrayList;
import java.util.List;

public class BaseActivity extends Activity implements AMapNaviListener, AMapNaviViewListener, ParallelRoadListener {

    protected AMapNaviView mAMapNaviView;
    protected AMapNavi mAMapNavi;
    protected NaviLatLng mEndLatlng = new NaviLatLng(40.084894,116.603039);
    protected NaviLatLng mStartLatlng = new NaviLatLng(39.825934,116.342972);
    NaviLatLng p1 = new NaviLatLng(39.831135,116.36056);
    protected final List<NaviLatLng> sList = new ArrayList<>();
    protected final List<NaviLatLng> eList = new ArrayList<>();
    protected List<NaviLatLng> mWayPointList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        try {
            mAMapNavi = AMapNavi.getInstance(getApplicationContext());
            mAMapNavi.addAMapNaviListener(this);
            mAMapNavi.addParallelRoadListener(this);
            mAMapNavi.setUseInnerVoice(true, true);
            mAMapNavi.setEmulatorNaviSpeed(75);
            sList.add(mStartLatlng);
            eList.add(mEndLatlng);
        } catch (AMapException e) {
            e.printStackTrace();
        }
    }

    @Override protected void onResume() { super.onResume(); mAMapNaviView.onResume(); }
    @Override protected void onPause() { super.onPause(); mAMapNaviView.onPause(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        mAMapNaviView.onDestroy();
        if (mAMapNavi != null) {
            mAMapNavi.stopNavi();
            mAMapNavi.destroy();
        }
    }

    // 以下为导航回调的空实现（必须实现但无需修改）
    @Override public void onInitNaviFailure() { Toast.makeText(this, "导航初始化失败", Toast.LENGTH_SHORT).show(); }
    @Override public void onInitNaviSuccess() {}
    @Override public void onStartNavi(int type) {}
    @Override public void onTrafficStatusUpdate() {}
    @Override public void onLocationChange(AMapNaviLocation location) {}
    @Override public void onGetNavigationText(int type, String text) {}
    @Override public void onGetNavigationText(String s) {}
    @Override public void onEndEmulatorNavi() {}
    @Override public void onArriveDestination() {}
    @Override public void onCalculateRouteFailure(int errorInfo) {}
    @Override public void onReCalculateRouteForYaw() {}
    @Override public void onReCalculateRouteForTrafficJam() {}
    @Override public void onArrivedWayPoint(int wayID) {}
    @Override public void onGpsOpenStatus(boolean enabled) {}
    @Override public void onNaviSetting() {}
    @Override public void onNaviMapMode(int naviMode) {}
    @Override public void onNaviCancel() { finish(); }
    @Override public void onNaviTurnClick() {}
    @Override public void onNextRoadClick() {}
    @Override public void onScanViewButtonClick() {}
    @Override public void updateCameraInfo(AMapNaviCameraInfo[] aMapCameraInfos) {}
    @Override public void onServiceAreaUpdate(AMapServiceAreaInfo[] amapServiceAreaInfos) {}
    @Override public void onNaviInfoUpdate(NaviInfo naviinfo) {}
    @Override public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo aMapNaviTrafficFacilityInfo) {}
    @Override public void showCross(AMapNaviCross aMapNaviCross) {}
    @Override public void hideCross() {}
    @Override public void showLaneInfo(AMapLaneInfo[] laneInfos, byte[] laneBackgroundInfo, byte[] laneRecommendedInfo) {}
    @Override public void hideLaneInfo() {}
    @Override public void onCalculateRouteSuccess(int[] ints) {}
    @Override public void notifyParallelRoad(int i) {}
    @Override public void OnUpdateTrafficFacility(AMapNaviTrafficFacilityInfo[] aMapNaviTrafficFacilityInfos) {}
    @Override public void updateAimlessModeStatistics(AimLessModeStat aimLessModeStat) {}
    @Override public void updateAimlessModeCongestionInfo(AimLessModeCongestionInfo aimLessModeCongestionInfo) {}
    @Override public void onPlayRing(int i) {}
    @Override public void onLockMap(boolean isLock) {}
    @Override public void onNaviViewLoaded() {}
    @Override public void onMapTypeChanged(int i) {}
    @Override public void onNaviViewShowMode(int i) {}
    @Override public boolean onNaviBackClick() { return false; }
    @Override public void showModeCross(AMapModelCross aMapModelCross) {}
    @Override public void hideModeCross() {}
    @Override public void updateIntervalCameraInfo(AMapNaviCameraInfo aMapNaviCameraInfo, AMapNaviCameraInfo aMapNaviCameraInfo1, int i) {}
    @Override public void showLaneInfo(AMapLaneInfo aMapLaneInfo) {}
    @Override public void onCalculateRouteSuccess(AMapCalcRouteResult aMapCalcRouteResult) {}
    @Override public void onCalculateRouteFailure(AMapCalcRouteResult result) {
        Log.e("导航错误", "错误码：" + result.getErrorCode() + "，描述：" + result.getErrorDescription());
    }
    @Override public void onNaviRouteNotify(AMapNaviRouteNotifyData aMapNaviRouteNotifyData) {}
    @Override public void onGpsSignalWeak(boolean b) {}
    @Override public void notifyParallelRoad(AMapNaviParallelRoadStatus status) {
        if (status.getmElevatedRoadStatusFlag() == 1) Toast.makeText(this, "当前在高架上", Toast.LENGTH_SHORT).show();
        else if (status.getmElevatedRoadStatusFlag() == 2) Toast.makeText(this, "当前在高架下", Toast.LENGTH_SHORT).show();
    }
}