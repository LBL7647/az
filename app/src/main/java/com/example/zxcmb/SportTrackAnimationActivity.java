package com.example.zxcmb;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// 运动轨迹动画展示页面
public class SportTrackAnimationActivity extends AppCompatActivity implements View.OnClickListener {
    // 基础配置常量
    private static final String TAG = "TrackAnimation";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int TIME_PER_SEGMENT = 300;
    private static final float MAP_PADDING = 50f;
    private static final int INTERPOLATE_STEPS = 5;
    private static final long MARKER_UPDATE_INTERVAL = 50;
    private static final double MIN_ELEVATION_CHANGE = 0.1;

    // 速度颜色配置
    private static final float SPEED_LOW = 0f, SPEED_MID = 15f, SPEED_HIGH = 30f;
    private static final int COLOR_LOW = Color.rgb(0,255,0), COLOR_MID = Color.rgb(255,255,0), COLOR_HIGH = Color.rgb(255,0,0);

    // 默认坐标
    private static final double DEFAULT_LON = 119.20584606;
    private static final double DEFAULT_LAT = 26.03476263;

    // 地球半径常量
    private static final double EARTH_RADIUS = 6371000;

    // 5公里分段阈值
    private static final double FIVE_KM_THRESHOLD = 5000.0;

    // 核心控件
    private MapView mMapView;
    private AMap mAMap;
    private Button mBtnStartPause, mBtnReset;
    private LinearLayout mLlFullscreen;
    private ImageView mIvBack;
    private TextView mTvSportDetailBtn, mTvSegmentDetailBtn;
    private TextView mTvTimeLocation;
    private TextView mTvDistanceValue;
    private TextView mTvTrainDuration;
    private TextView mTvTotalDuration;
    private TextView mTvAvgSpeed;
    private TextView mTvCalorie;
    private TextView mTvClimbHeight;
    private TextView mTvDescendHeight;

    // 5公里分段UI控件
    private TextView mTvSegment1No, mTvSegment1Km, mTvSegment1Time, mTvSegment1Speed;
    private TextView mTvSegment2No, mTvSegment2Km, mTvSegment2Time, mTvSegment2Speed;
    private TextView mTvSegment3No, mTvSegment3Km, mTvSegment3Time, mTvSegment3Speed;
    private TextView mTvSegment4No, mTvSegment4Km, mTvSegment4Time, mTvSegment4Speed;

    // 页面传递数据
    private String mTimeRangeStr;
    private String mStartTime;
    private String mEndTime;
    private String mRideDate;
    private String mLocation = "福州市";

    // 轨迹数据
    private List<LatLng> mOriginalPoints = new ArrayList<>();
    private List<Double> mSpeedList = new ArrayList<>();
    private List<Double> mAltitudeList = new ArrayList<>();
    private List<Double> mSlopeList = new ArrayList<>();
    private List<List<LatLng>> mSegmentPoints = new ArrayList<>();
    private List<Integer> mSegmentColors = new ArrayList<>();
    private List<Polyline> mDrawnLines = new ArrayList<>();
    private List<Polyline> mDefaultLines = new ArrayList<>();
    private List<Long> mTimestampList = new ArrayList<>();

    // 5公里分段数据
    private List<FiveKmSegment> mFiveKmSegments = new ArrayList<>();
    private int mFastestSegmentIndex = -1;

    // 运动数据
    private double mTotalDistanceKm = 0;
    private long mTotalSeconds = 0;
    private double mAvgSpeed = 0;
    private double mCalorie = 0;
    private int mClimbHeight = 0;
    private int mDescendHeight = 0;
    private double mMaxSlope = 0;
    private double mAvgSlope = 0;

    // 动画控制
    private ValueAnimator mAnimator;
    private boolean isAnimRunning = false;
    private boolean isAnimFinished = false;
    private boolean isMapFullscreen = false;
    private long lastMarkerUpdateTime = 0;
    private LatLng lastMarkerPos = null;

    // 布局参数备份
    private int originalMapWidth, originalMapHeight;
    private int originalMapMarginTop, originalMapMarginBottom, originalMapMarginLeft, originalMapMarginRight;
    private int originalMapTopToBottom, originalMapBottomToTop;

    // 地图标记
    private Marker mStartMarker;
    private Marker mEndMarker;
    private Marker mMovingMarker;

    // 5公里分段数据实体
    private static class FiveKmSegment {
        int segmentNo;
        double distanceKm;
        long durationSeconds;
        double avgSpeedKmH;

        public FiveKmSegment(int segmentNo, double distanceKm, long durationSeconds, double avgSpeedKmH) {
            this.segmentNo = segmentNo;
            this.distanceKm = distanceKm;
            this.durationSeconds = durationSeconds;
            this.avgSpeedKmH = avgSpeedKmH;
        }
    }

    // GPS点模型
    private static class GPSPoint {
        long timestamp;
        double distance;
        double speed;
        String timeStr;
    }

    // 分段数据模型
    private static class SegmentData {
        int segmentNum;
        double distanceKm;
        String time;
        String totalTime;
        double speed;
        boolean isFastest;
        long rawTimeSeconds;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ride_detail);

        // 初始化控件
        initViews();
        // 初始化地图
        initMap(savedInstanceState);
        // 绑定点击事件
        bindClickEvents();
        // 备份地图布局参数
        backupMapLayoutParams();

        // 权限检查
        if (checkLocationPermission()) {
            // 接收轨迹数据
            receiveTrackDataFromIntent();
            if (mOriginalPoints.size() >= 2) {
                // 计算运动数据
                calculateAllSportData();
                // 计算5公里分段
                calculateFiveKmSegments();
                // 更新UI显示
                updateSportDataUI();
                // 更新5公里分段UI
                updateFirstFourFiveKmSegmentsUI();
                // 初始化分段数据
                initData();
                // 绘制默认轨迹
                drawDefaultPath();
                // 启用按钮
                mBtnReset.setEnabled(true);
                mBtnStartPause.setEnabled(true);
            } else {
                Toast.makeText(this, "该骑行记录无有效GPS数据", Toast.LENGTH_SHORT).show();
                mBtnReset.setEnabled(false);
                mBtnStartPause.setEnabled(false);
            }
        } else {
            // 请求权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET},
                    PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 初始化所有控件
     */
    private void initViews() {
        // 基础控件
        mMapView = findViewById(R.id.map_view);
        mIvBack = findViewById(R.id.iv_back);
        mLlFullscreen = findViewById(R.id.ll_fullscreen);
        mBtnStartPause = findViewById(R.id.btn_start_pause);
        mBtnReset = findViewById(R.id.btn_reset);

        // 数据显示控件
        mTvTimeLocation = findViewById(R.id.tv_time_location);
        mTvDistanceValue = ((TextView) ((LinearLayout) findViewById(R.id.ll_distance)).getChildAt(0));
        mTvSportDetailBtn = findViewById(R.id.tv_sport_detail_btn);
        mTvSegmentDetailBtn = findViewById(R.id.tv_segment_detail_btn);

        // 运动数据卡片
        LinearLayout llDataCard = findViewById(R.id.ll_data_card);
        mTvTrainDuration = ((TextView) ((LinearLayout) ((LinearLayout) llDataCard.getChildAt(1)).getChildAt(0)).getChildAt(1));
        mTvCalorie = ((TextView) ((LinearLayout) ((LinearLayout) llDataCard.getChildAt(1)).getChildAt(1)).getChildAt(1));
        mTvAvgSpeed = ((TextView) ((LinearLayout) ((LinearLayout) llDataCard.getChildAt(1)).getChildAt(2)).getChildAt(1));
        mTvTotalDuration = ((TextView) ((LinearLayout) ((LinearLayout) llDataCard.getChildAt(2)).getChildAt(0)).getChildAt(1));
        mTvClimbHeight = ((TextView) ((LinearLayout) ((LinearLayout) llDataCard.getChildAt(2)).getChildAt(1)).getChildAt(1));
        mTvDescendHeight = ((TextView) ((LinearLayout) ((LinearLayout) llDataCard.getChildAt(2)).getChildAt(2)).getChildAt(1));

        // 前4个5公里分段控件
        LinearLayout llSegmentDetail = findViewById(R.id.ll_segment_detail);
        LinearLayout llSegment1 = (LinearLayout) llSegmentDetail.getChildAt(2);
        mTvSegment1No = (TextView) llSegment1.getChildAt(0);
        mTvSegment1Km = (TextView) llSegment1.getChildAt(1);
        mTvSegment1Time = (TextView) llSegment1.getChildAt(2);
        mTvSegment1Speed = (TextView) llSegment1.getChildAt(3);

        LinearLayout llSegment2 = (LinearLayout) llSegmentDetail.getChildAt(3);
        mTvSegment2No = (TextView) llSegment2.getChildAt(0);
        mTvSegment2Km = (TextView) llSegment2.getChildAt(1);
        mTvSegment2Time = (TextView) llSegment2.getChildAt(2);
        mTvSegment2Speed = (TextView) llSegment2.getChildAt(3);

        LinearLayout llSegment3 = (LinearLayout) llSegmentDetail.getChildAt(4);
        mTvSegment3No = (TextView) llSegment3.getChildAt(0);
        mTvSegment3Km = (TextView) llSegment3.getChildAt(1);
        mTvSegment3Time = (TextView) llSegment3.getChildAt(2);
        mTvSegment3Speed = (TextView) llSegment3.getChildAt(3);

        LinearLayout llSegment4 = (LinearLayout) llSegmentDetail.getChildAt(5);
        mTvSegment4No = (TextView) llSegment4.getChildAt(0);
        mTvSegment4Km = (TextView) llSegment4.getChildAt(1);
        mTvSegment4Time = (TextView) llSegment4.getChildAt(2);
        mTvSegment4Speed = (TextView) llSegment4.getChildAt(3);

        // 初始状态
        mBtnReset.setEnabled(false);
        mBtnStartPause.setEnabled(false);
        mIvBack.setVisibility(View.GONE);

        Log.d(TAG, "控件初始化完成");
    }

    /**
     * 初始化地图
     */
    private void initMap(Bundle savedInstanceState) {
        mMapView.onCreate(savedInstanceState);
        if (mAMap == null) {
            mAMap = mMapView.getMap();
            mAMap.getUiSettings().setZoomControlsEnabled(false);
            mAMap.getUiSettings().setAllGesturesEnabled(true);
            mMapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        Log.d(TAG, "地图初始化完成");
    }

    /**
     * 绑定点击事件
     */
    private void bindClickEvents() {
        mBtnStartPause.setOnClickListener(this);
        mBtnReset.setOnClickListener(this);
        mLlFullscreen.setOnClickListener(this);
        mIvBack.setOnClickListener(this);
        mTvSportDetailBtn.setOnClickListener(this);
        mTvSegmentDetailBtn.setOnClickListener(this);
        Log.d(TAG, "点击事件绑定完成");
    }

    /**
     * 备份地图布局参数
     */
    private void backupMapLayoutParams() {
        mMapView.post(() -> {
            View flMapContainer = findViewById(R.id.fl_map_container);
            if (flMapContainer != null) {
                ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) flMapContainer.getLayoutParams();
                originalMapWidth = params.width;
                originalMapHeight = params.height;
                originalMapMarginTop = params.topMargin;
                originalMapMarginBottom = params.bottomMargin;
                originalMapMarginLeft = params.leftMargin;
                originalMapMarginRight = params.rightMargin;
                originalMapTopToBottom = params.topToBottom;
                originalMapBottomToTop = params.bottomToTop;
                Log.d(TAG, "地图布局参数备份完成：宽=" + originalMapWidth + " 高=" + originalMapHeight);
            } else {
                Log.e(TAG, "fl_map_container 控件未找到！");
            }
        });
    }

    /**
     * 接收轨迹数据
     */
    private void receiveTrackDataFromIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            Log.e(TAG, "Intent为空，无法接收数据");
            Toast.makeText(this, "Intent为空，无法接收数据", Toast.LENGTH_SHORT).show();
            return;
        }

        // 接收基础数据
        mTimeRangeStr = intent.getStringExtra("TIME_RANGE");
        mStartTime = intent.getStringExtra("START_TIME");
        mEndTime = intent.getStringExtra("END_TIME");
        mRideDate = intent.getStringExtra("RIDE_DATE");
        mTotalSeconds = intent.getLongExtra("TOTAL_SECONDS", 0);

        // 接收轨迹数据
        ArrayList<double[]> trackDataList = null;
        try {
            trackDataList = (ArrayList<double[]>) intent.getSerializableExtra("TRACK_DATA_LIST");
            Log.d(TAG, "接收轨迹数据：" + (trackDataList == null ? "null" : trackDataList.size() + "条"));
        } catch (Exception e) {
            Log.e(TAG, "轨迹数据接收失败：" + e.getMessage());
        }

        if (trackDataList == null || trackDataList.isEmpty()) {
            Log.e(TAG, "轨迹数据为空！");
            Toast.makeText(this, "未接收到轨迹数据", Toast.LENGTH_LONG).show();
            return;
        }

        // 解析轨迹数据
        int replaceCount = 0;
        int validCount = 0;
        mOriginalPoints.clear();
        mSpeedList.clear();
        mAltitudeList.clear();
        mSlopeList.clear();
        mTimestampList.clear();

        // 生成连续时间戳
        long baseTimestamp = System.currentTimeMillis() - mTotalSeconds * 1000;
        long intervalPerPoint = mTotalSeconds * 1000 / Math.max(1, trackDataList.size());

        // 排序轨迹数据
        List<double[]> sortedTrackData = new ArrayList<>(trackDataList);
        int finalValidCount = validCount;
        Collections.sort(sortedTrackData, (a, b) -> {
            long tsA = (a.length >= 5 && a[4] > 0) ? (long) (a[4] * 1000) : (baseTimestamp + finalValidCount * intervalPerPoint);
            long tsB = (b.length >= 5 && b[4] > 0) ? (long) (b[4] * 1000) : (baseTimestamp + finalValidCount * intervalPerPoint);
            return Long.compare(tsA, tsB);
        });

        // 解析每个点
        for (int i = 0; i < sortedTrackData.size(); i++) {
            double[] data = sortedTrackData.get(i);
            if (data == null || data.length < 3) {
                Log.w(TAG, "第" + i + "条数据不完整：" + data);
                continue;
            }

            double lon = data[0];
            double lat = data[1];
            double speed = data[2];
            double alt = data.length >= 4 ? data[3] : 0.0;

            // 生成时间戳
            long timestamp;
            if (data.length >= 5 && data[4] > 0) {
                timestamp = (long) (data[4] * 1000);
            } else {
                timestamp = baseTimestamp + i * intervalPerPoint;
            }

            // 修复无效经纬度
            boolean isInvalid = false;
            if (lon == 0 || lat == 0 || Double.isNaN(lon) || Double.isNaN(lat) ||
                    lon < -180 || lon > 180 || lat < -90 || lat > 90) {
                lon = DEFAULT_LON + (i % 100) * 0.0001;
                lat = DEFAULT_LAT + (i % 100) * 0.0001;
                replaceCount++;
                isInvalid = true;
                Log.w(TAG, "第" + i + "条数据无效，替换为：" + lon + "," + lat);
            }

            // 添加有效数据
            mOriginalPoints.add(new LatLng(lat, lon));
            mSpeedList.add(speed);
            mAltitudeList.add(alt);
            mTimestampList.add(timestamp);
            validCount++;
        }

        Log.d(TAG, "数据解析完成：总条数=" + trackDataList.size() + " 有效条数=" + validCount +
                " 修复条数=" + replaceCount);
        if (replaceCount > 0) {
            Toast.makeText(this, "自动修复了" + replaceCount + "个无效定位点", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 计算所有运动数据
     */
    private void calculateAllSportData() {
        // 计算总距离
        mTotalDistanceKm = calculateTotalDistance(mOriginalPoints);
        Log.d(TAG, "总距离：" + mTotalDistanceKm + " KM");

        // 计算平均速度
        calculateAvgSpeed();

        // 计算海拔和坡度
        calculateElevationAndSlope();

        // 计算卡路里
        mCalorie = mTotalDistanceKm * 60 * 1.05;
        mCalorie = mCalorie * (1 + Math.abs(mAvgSlope) / 100 * 5);

        Log.d(TAG, "运动数据计算完成：");
        Log.d(TAG, "平均速度：" + mAvgSpeed + " KM/H");
        Log.d(TAG, "卡路里：" + mCalorie + " 千卡");
        Log.d(TAG, "爬升高度：" + mClimbHeight + " 米");
        Log.d(TAG, "下降高度：" + mDescendHeight + " 米");
    }

    /**
     * 计算5公里分段数据
     */
    private void calculateFiveKmSegments() {
        mFiveKmSegments.clear();
        mFastestSegmentIndex = -1;

        if (mOriginalPoints.size() < 2 || mTimestampList.size() < 2) {
            Log.w(TAG, "数据不足，无法计算5公里分段");
            return;
        }

        // 构建GPS点列表
        List<GPSPoint> gpsPointList = new ArrayList<>();
        for (int i = 0; i < mOriginalPoints.size(); i++) {
            GPSPoint point = new GPSPoint();
            point.timestamp = mTimestampList.get(i);
            point.distance = i == 0 ? 0 : calculateDistance(mOriginalPoints.get(i-1), mOriginalPoints.get(i));
            point.speed = mSpeedList.get(i);
            point.timeStr = timestampToDateTime(point.timestamp);
            gpsPointList.add(point);
        }

        // 计算累计距离
        double[] accumulatedDistances = calculateAccumulatedDistances();

        // 计算1公里分段
        List<SegmentData> oneKmSegments = calculateOneKmSegments(gpsPointList, accumulatedDistances);

        // 校准总时长
        calibrateTotalTime(oneKmSegments);

        // 计算5公里分段
        mFiveKmSegments = calculateFiveKmSegmentsFromOneKm(oneKmSegments);

        // 找出最快分段
        findFastestSegment();

        Log.d(TAG, "5公里分段计算完成，共" + mFiveKmSegments.size() + "段，最快分段索引：" + mFastestSegmentIndex);
    }

    /**
     * 计算1公里分段
     */
    private List<SegmentData> calculateOneKmSegments(List<GPSPoint> gpsPointList, double[] accumulatedDistances) {
        List<SegmentData> oneKmSegments = new ArrayList<>();
        if (gpsPointList.isEmpty()) return oneKmSegments;

        int currentKm = 1;
        double targetKmDist = 1000;
        int startIdx = 0;
        long startTimestamp = gpsPointList.get(0).timestamp;
        long actualTotalElapsed = 0;
        double maxSpeed = 0;
        int fastestIndex = -1;

        Log.d(TAG, "\n开始计算1公里分段：");
        Log.d(TAG, "起始时间戳=" + startTimestamp + " 原始时间=" + timestampToDateTime(startTimestamp));

        for (int i = 1; i < gpsPointList.size(); i++) {
            GPSPoint currPoint = gpsPointList.get(i);
            double currDist = accumulatedDistances[i];

            if (currDist >= targetKmDist) {
                GPSPoint prevPoint = gpsPointList.get(i-1);
                double prevDist = accumulatedDistances[i-1];

                // 插值计算精准时间戳
                double distDiff = targetKmDist - prevDist;
                double totalDistDiff = currDist - prevDist;
                long timeDiffMs = currPoint.timestamp - prevPoint.timestamp;
                long preciseTimeMs = prevPoint.timestamp + (long) (timeDiffMs * (distDiff / totalDistDiff));

                // 计算平均速度
                List<GPSPoint> segmentPoints = gpsPointList.subList(startIdx, i + 1);
                double segmentAvgSpeed = 0;
                int speedCount = 0;
                for (GPSPoint p : segmentPoints) {
                    if (p.speed > 0) {
                        segmentAvgSpeed += p.speed;
                        speedCount++;
                    }
                }
                if (speedCount > 0) {
                    segmentAvgSpeed = Math.round(segmentAvgSpeed / speedCount * 10.0) / 10.0;
                }

                // 计算分段用时
                long segmentTimeMs = preciseTimeMs - startTimestamp;
                long segmentTimeSeconds = Math.round((double) segmentTimeMs / 1000);
                segmentTimeSeconds = Math.max(1, segmentTimeSeconds);
                actualTotalElapsed += segmentTimeSeconds;

                // 计算实际距离
                double actualKm = Math.min(1.0, Math.round((currDist - (currentKm - 1) * 1000) / 1000 * 100.0) / 100.0);

                // 构建分段数据
                SegmentData seg = new SegmentData();
                seg.segmentNum = currentKm;
                seg.distanceKm = actualKm;
                seg.time = formatSeconds(segmentTimeSeconds);
                seg.totalTime = formatSeconds(actualTotalElapsed);
                seg.speed = segmentAvgSpeed;
                seg.isFastest = false;
                seg.rawTimeSeconds = segmentTimeSeconds;
                oneKmSegments.add(seg);

                // 记录最快分段
                if (segmentAvgSpeed > maxSpeed) {
                    maxSpeed = segmentAvgSpeed;
                    fastestIndex = oneKmSegments.size() - 1;
                }

                // 准备下一段
                currentKm++;
                targetKmDist = currentKm * 1000;
                startIdx = i;
                startTimestamp = preciseTimeMs;
            }
        }

        // 处理最后不足1公里的部分
        if (startIdx < gpsPointList.size() - 1) {
            List<GPSPoint> lastSegment = gpsPointList.subList(startIdx, gpsPointList.size());
            if (lastSegment.size() < 2) return oneKmSegments;

            GPSPoint lastPoint = gpsPointList.get(gpsPointList.size() - 1);
            double lastDist = accumulatedDistances[accumulatedDistances.length - 1] - (currentKm - 1) * 1000;
            if (lastDist <= 0) return oneKmSegments;

            // 计算平均速度
            double lastAvgSpeed = 0;
            int speedCount = 0;
            for (GPSPoint p : lastSegment) {
                if (p.speed > 0) {
                    lastAvgSpeed += p.speed;
                    speedCount++;
                }
            }
            if (speedCount > 0) {
                lastAvgSpeed = Math.round(lastAvgSpeed / speedCount * 10.0) / 10.0;
            }

            // 计算用时
            long lastSegmentTimeMs = lastPoint.timestamp - startTimestamp;
            long lastTimeSeconds = Math.round((double) lastSegmentTimeMs / 1000);
            lastTimeSeconds = Math.max(1, lastTimeSeconds);
            actualTotalElapsed += lastTimeSeconds;

            // 构建最后一段数据
            SegmentData seg = new SegmentData();
            seg.segmentNum = currentKm;
            seg.distanceKm = Math.round(lastDist / 1000 * 100.0) / 100.0;
            seg.time = formatSeconds(lastTimeSeconds);
            seg.totalTime = formatSeconds(actualTotalElapsed);
            seg.speed = lastAvgSpeed;
            seg.isFastest = false;
            seg.rawTimeSeconds = lastTimeSeconds;
            oneKmSegments.add(seg);

            // 检查最快分段
            if (lastAvgSpeed > maxSpeed) {
                fastestIndex = oneKmSegments.size() - 1;
            }
        }

        // 标记最快分段
        if (fastestIndex >= 0 && fastestIndex < oneKmSegments.size()) {
            oneKmSegments.get(fastestIndex).isFastest = true;
            Log.d(TAG, "\n最快1公里分段：第" + (fastestIndex + 1) + "段，速度=" + maxSpeed + "km/h");
        }

        return oneKmSegments;
    }

    /**
     * 校准分段总时长
     */
    private void calibrateTotalTime(List<SegmentData> oneKmSegments) {
        if (oneKmSegments.isEmpty() || mTotalSeconds <= 0) return;

        long currentTotal = getOneKmTotalSeconds(oneKmSegments);
        if (currentTotal == mTotalSeconds) {
            Log.d(TAG, "分段总时长已匹配训练总时长，无需校准");
            return;
        }

        double ratio = (double) mTotalSeconds / currentTotal;
        Log.d(TAG, "\n===== 开始校准分段时长 =====");
        Log.d(TAG, "校准前总时长=" + currentTotal + "秒，目标总时长=" + mTotalSeconds + "秒");
        Log.d(TAG, "校准比率=" + ratio);

        long calibratedTotal = 0;
        int lastIndex = oneKmSegments.size() - 1;
        for (int i = 0; i < oneKmSegments.size(); i++) {
            SegmentData seg = oneKmSegments.get(i);
            if (i == lastIndex) {
                seg.rawTimeSeconds = mTotalSeconds - calibratedTotal;
                seg.rawTimeSeconds = Math.max(1, seg.rawTimeSeconds);
            } else {
                long calibratedSec = Math.round(seg.rawTimeSeconds * ratio);
                seg.rawTimeSeconds = Math.max(1, calibratedSec);
            }
            seg.time = formatSeconds(seg.rawTimeSeconds);
            calibratedTotal += seg.rawTimeSeconds;
            seg.totalTime = formatSeconds(calibratedTotal);
        }

        Log.d(TAG, "\n===== 校准后总时间校验 =====");
        Log.d(TAG, "校准后分段累加总时长(秒)：" + getOneKmTotalSeconds(oneKmSegments));
        Log.d(TAG, "外部训练总时长(秒)：" + mTotalSeconds);
    }

    /**
     * 从1公里分段计算5公里分段
     */
    private List<FiveKmSegment> calculateFiveKmSegmentsFromOneKm(List<SegmentData> oneKmSegments) {
        List<FiveKmSegment> fiveKmSegments = new ArrayList<>();
        if (oneKmSegments.isEmpty()) return fiveKmSegments;

        int current5km = 1;
        int startKmIdx = 0;
        double maxSpeed = 0;
        int fastestIndex = -1;

        Log.d(TAG, "\n重新计算5公里分段（校准后）：共" + oneKmSegments.size() + "个1公里分段");

        while (startKmIdx < oneKmSegments.size()) {
            double accumulatedKm = 0.0;
            long accumulatedTime = 0;
            double accumulatedSpeedSum = 0;
            int speedCount = 0;
            int endKmIdx = startKmIdx;

            for (int i = startKmIdx; i < oneKmSegments.size(); i++) {
                SegmentData oneKmSeg = oneKmSegments.get(i);
                accumulatedKm += oneKmSeg.distanceKm;
                accumulatedTime += oneKmSeg.rawTimeSeconds;
                accumulatedSpeedSum += oneKmSeg.speed;
                speedCount++;
                endKmIdx = i;

                if (accumulatedKm >= 5.0) break;
            }

            // 计算平均速度
            double fiveKmAvgSpeed = 0;
            if (speedCount > 0) {
                fiveKmAvgSpeed = Math.round(accumulatedSpeedSum / speedCount * 10.0) / 10.0;
            }

            // 构建5公里分段
            FiveKmSegment seg = new FiveKmSegment(current5km, accumulatedKm, accumulatedTime, fiveKmAvgSpeed);
            fiveKmSegments.add(seg);

            // 记录最快分段
            if (fiveKmAvgSpeed > maxSpeed) {
                maxSpeed = fiveKmAvgSpeed;
                fastestIndex = fiveKmSegments.size() - 1;
            }

            current5km++;
            startKmIdx = endKmIdx + 1;
        }

        Log.d(TAG, "5公里分段重新计算完成，共" + fiveKmSegments.size() + "个分段");
        return fiveKmSegments;
    }

    /**
     * 找出最快分段
     */
    private void findFastestSegment() {
        double maxSpeed = 0;
        for (int i = 0; i < mFiveKmSegments.size(); i++) {
            FiveKmSegment seg = mFiveKmSegments.get(i);
            if (seg.avgSpeedKmH > maxSpeed) {
                maxSpeed = seg.avgSpeedKmH;
                mFastestSegmentIndex = i;
            }
        }
    }

    /**
     * 获取1公里分段总时长
     */
    private long getOneKmTotalSeconds(List<SegmentData> oneKmSegments) {
        long total = 0;
        for (SegmentData seg : oneKmSegments) {
            total += seg.rawTimeSeconds;
        }
        return total;
    }

    /**
     * 更新前4个5公里分段UI
     */
    private void updateFirstFourFiveKmSegmentsUI() {
        runOnUiThread(() -> {
            // 分段1
            if (mFiveKmSegments.size() >= 1) {
                FiveKmSegment seg1 = mFiveKmSegments.get(0);
                mTvSegment1No.setText(String.valueOf(seg1.segmentNo));
                mTvSegment1Km.setText(String.format(Locale.getDefault(), "%.2f", seg1.distanceKm));
                mTvSegment1Time.setText(formatDurationOnly(seg1.durationSeconds));
                mTvSegment1Speed.setText(String.format(Locale.getDefault(), "%.1f", seg1.avgSpeedKmH));

                if (mFastestSegmentIndex == 0) {
                    mTvSegment1Km.setText("最快 " + String.format(Locale.getDefault(), "%.2f", seg1.distanceKm));
                    ((LinearLayout) findViewById(R.id.ll_segment1)).setBackgroundColor(Color.parseColor("#00C853"));
                    mTvSegment1No.setTextColor(Color.WHITE);
                    mTvSegment1Km.setTextColor(Color.WHITE);
                    mTvSegment1Time.setTextColor(Color.WHITE);
                    mTvSegment1Speed.setTextColor(Color.WHITE);
                }
            }

            // 分段2
            if (mFiveKmSegments.size() >= 2) {
                FiveKmSegment seg2 = mFiveKmSegments.get(1);
                mTvSegment2No.setText(String.valueOf(seg2.segmentNo));
                mTvSegment2Km.setText(String.format(Locale.getDefault(), "%.2f", seg2.distanceKm));
                mTvSegment2Time.setText(formatDurationOnly(seg2.durationSeconds));
                mTvSegment2Speed.setText(String.format(Locale.getDefault(), "%.1f", seg2.avgSpeedKmH));

                if (mFastestSegmentIndex == 1) {
                    mTvSegment2Km.setText("最快 " + String.format(Locale.getDefault(), "%.2f", seg2.distanceKm));
                    ((LinearLayout) findViewById(R.id.ll_segment2)).setBackgroundColor(Color.parseColor("#00C853"));
                    mTvSegment2No.setTextColor(Color.WHITE);
                    mTvSegment2Km.setTextColor(Color.WHITE);
                    mTvSegment2Time.setTextColor(Color.WHITE);
                    mTvSegment2Speed.setTextColor(Color.WHITE);
                }
            }

            // 分段3
            if (mFiveKmSegments.size() >= 3) {
                FiveKmSegment seg3 = mFiveKmSegments.get(2);
                mTvSegment3No.setText(String.valueOf(seg3.segmentNo));
                mTvSegment3Km.setText(String.format(Locale.getDefault(), "%.2f", seg3.distanceKm));
                mTvSegment3Time.setText(formatDurationOnly(seg3.durationSeconds));
                mTvSegment3Speed.setText(String.format(Locale.getDefault(), "%.1f", seg3.avgSpeedKmH));

                if (mFastestSegmentIndex == 2) {
                    mTvSegment3Km.setText("最快 " + String.format(Locale.getDefault(), "%.2f", seg3.distanceKm));
                    ((LinearLayout) findViewById(R.id.ll_segment3)).setBackgroundColor(Color.parseColor("#00C853"));
                    mTvSegment3No.setTextColor(Color.WHITE);
                    mTvSegment3Km.setTextColor(Color.WHITE);
                    mTvSegment3Time.setTextColor(Color.WHITE);
                    mTvSegment3Speed.setTextColor(Color.WHITE);
                }
            }

            // 分段4
            if (mFiveKmSegments.size() >= 4) {
                FiveKmSegment seg4 = mFiveKmSegments.get(3);
                mTvSegment4No.setText(String.valueOf(seg4.segmentNo));
                mTvSegment4Km.setText(String.format(Locale.getDefault(), "%.2f", seg4.distanceKm));
                mTvSegment4Time.setText(formatDurationOnly(seg4.durationSeconds));
                mTvSegment4Speed.setText(String.format(Locale.getDefault(), "%.1f", seg4.avgSpeedKmH));

                if (mFastestSegmentIndex == 3) {
                    mTvSegment4Km.setText("最快 " + String.format(Locale.getDefault(), "%.2f", seg4.distanceKm));
                    ((LinearLayout) findViewById(R.id.ll_segment4)).setBackgroundColor(Color.parseColor("#00C853"));
                    mTvSegment4No.setTextColor(Color.WHITE);
                    mTvSegment4Km.setTextColor(Color.WHITE);
                    mTvSegment4Time.setTextColor(Color.WHITE);
                    mTvSegment4Speed.setTextColor(Color.WHITE);
                }
            }
        });
    }

    /**
     * 计算总距离
     */
    private double calculateTotalDistance(List<LatLng> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }

        double totalMeters = 0.0;
        for (int i = 1; i < points.size(); i++) {
            LatLng p1 = points.get(i - 1);
            LatLng p2 = points.get(i);
            totalMeters += calculateDistance(p1, p2);
        }

        return Math.round(totalMeters / 1000.0 * 10000.0) / 10000.0;
    }

    /**
     * 计算平均速度
     */
    private void calculateAvgSpeed() {
        mAvgSpeed = 0.0;
        if (!mSpeedList.isEmpty()) {
            double totalSpeed = 0.0;
            int validSpeedCount = 0;
            for (double speed : mSpeedList) {
                if (speed > 0) {
                    totalSpeed += speed;
                    validSpeedCount++;
                }
            }
            if (validSpeedCount > 0) {
                mAvgSpeed = totalSpeed / validSpeedCount;
            }
        }
    }

    /**
     * 计算海拔和坡度
     */
    private void calculateElevationAndSlope() {
        mClimbHeight = 0;
        mDescendHeight = 0;
        mMaxSlope = 0;
        mAvgSlope = 0;

        if (mOriginalPoints.size() < 2 || mAltitudeList.size() < 2) {
            mClimbHeight = (int) (mTotalDistanceKm * 5);
            mDescendHeight = (int) (mTotalDistanceKm * 4);
            return;
        }

        double totalSlope = 0.0;
        int slopeCount = 0;

        for (int i = 1; i < mOriginalPoints.size(); i++) {
            LatLng p1 = mOriginalPoints.get(i - 1);
            LatLng p2 = mOriginalPoints.get(i);

            double horizontalDistance = calculateDistance(p1, p2);
            if (horizontalDistance < 1) {
                continue;
            }

            double alt1 = mAltitudeList.get(i - 1);
            double alt2 = mAltitudeList.get(i);
            double elevationChange = alt2 - alt1;

            if (elevationChange > MIN_ELEVATION_CHANGE) {
                mClimbHeight += (int) Math.round(elevationChange);
            } else if (elevationChange < -MIN_ELEVATION_CHANGE) {
                mDescendHeight += (int) Math.round(Math.abs(elevationChange));
            }

            double slope = (elevationChange / horizontalDistance) * 100;
            totalSlope += slope;
            slopeCount++;

            if (Math.abs(slope) > Math.abs(mMaxSlope)) {
                mMaxSlope = slope;
            }
        }

        if (slopeCount > 0) {
            mAvgSlope = totalSlope / slopeCount;
        }

        mClimbHeight = Math.max(0, mClimbHeight);
        mDescendHeight = Math.max(0, mDescendHeight);
    }

    /**
     * 更新运动数据UI
     */
    private void updateSportDataUI() {
        runOnUiThread(() -> {
            // 时间地点
            String timeLocationStr;
            if (mTimeRangeStr != null && !mTimeRangeStr.isEmpty()) {
                timeLocationStr = mTimeRangeStr + " " + mLocation;
            } else {
                if (mStartTime != null && !mStartTime.isEmpty() && mEndTime != null && !mEndTime.isEmpty()) {
                    timeLocationStr = mStartTime + " - " + mEndTime + " " + mLocation;
                } else {
                    String durationStr = formatDurationOnly(mTotalSeconds);
                    String defaultDate = mRideDate != null ? mRideDate : "2024/10/17";
                    timeLocationStr = defaultDate + " " + durationStr + " " + mLocation;
                }
            }
            if (mTvTimeLocation != null) {
                mTvTimeLocation.setText(timeLocationStr);
            }

            // 距离
            if (mTvDistanceValue != null) {
                mTvDistanceValue.setText(String.format(Locale.getDefault(), "%.2f", mTotalDistanceKm));
            }

            // 时长
            String durationStr = formatDurationOnly(mTotalSeconds);
            if (mTvTrainDuration != null) {
                mTvTrainDuration.setText(durationStr);
            }
            if (mTvTotalDuration != null) {
                long totalWithRest = mTotalSeconds + 42;
                mTvTotalDuration.setText(formatDurationOnly(totalWithRest));
            }

            // 平均速度
            if (mTvAvgSpeed != null) {
                mTvAvgSpeed.setText(String.format(Locale.getDefault(), "%.2f 千米/时", mAvgSpeed));
            }

            // 卡路里
            if (mTvCalorie != null) {
                mTvCalorie.setText((int) Math.round(mCalorie) + " 千卡");
            }

            // 爬升/下降
            if (mTvClimbHeight != null) {
                mTvClimbHeight.setText(mClimbHeight + " 米");
            }
            if (mTvDescendHeight != null) {
                mTvDescendHeight.setText(mDescendHeight + " 米");
            }
        });
    }

    /**
     * 初始化分段数据
     */
    private void initData() {
        mSegmentPoints.clear();
        mSegmentColors.clear();
        Log.d(TAG, "开始初始化分段数据，原始点数量：" + mOriginalPoints.size());

        for (int i = 1; i < mOriginalPoints.size(); i++) {
            LatLng p1 = mOriginalPoints.get(i - 1);
            LatLng p2 = mOriginalPoints.get(i);

            double currentRawSpeed = mSpeedList.get(i - 1);
            int segmentColor = getColorBySpeed(currentRawSpeed);
            mSegmentColors.add(segmentColor);

            // 插值生成平滑点
            List<LatLng> segment = new ArrayList<>();
            for (int step = 0; step <= INTERPOLATE_STEPS; step++) {
                float ratio = (float) step / INTERPOLATE_STEPS;
                segment.add(new LatLng(
                        p1.latitude + (p2.latitude - p1.latitude) * ratio,
                        p1.longitude + (p2.longitude - p1.longitude) * ratio
                ));
            }
            mSegmentPoints.add(segment);

            Log.d(TAG, "分段 " + (i-1) + " 初始化：原始速度=" + String.format("%.1f", currentRawSpeed) + " km/h | 颜色=" + Integer.toHexString(segmentColor));
        }
        Log.d(TAG, "分段数据初始化完成：分段数=" + mSegmentPoints.size());
    }

    /**
     * 绘制默认轨迹
     */
    private void drawDefaultPath() {
        clearDefaultPath();
        Log.d(TAG, "开始绘制默认轨迹，分段数：" + mSegmentPoints.size());

        // 绘制底轨
        for (int i = 0; i < mSegmentPoints.size(); i++) {
            int color = mSegmentColors.get(i);
            int alphaColor = Color.argb(128, Color.red(color), Color.green(color), Color.blue(color));
            Polyline polyline = mAMap.addPolyline(new PolylineOptions()
                    .addAll(mSegmentPoints.get(i))
                    .width(8f)
                    .color(alphaColor)
                    .setDottedLine(false));
            mDefaultLines.add(polyline);
        }

        // 添加起点终点标记
        if (mStartMarker == null) {
            mStartMarker = mAMap.addMarker(new MarkerOptions()
                    .position(mOriginalPoints.get(0))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .title("起点")
                    .anchor(0.5f, 0.5f));
        }
        if (mEndMarker == null) {
            mEndMarker = mAMap.addMarker(new MarkerOptions()
                    .position(mOriginalPoints.get(mOriginalPoints.size() - 1))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .title("终点")
                    .anchor(0.5f, 0.5f));
        }

        // 定位到轨迹区域
        LatLngBounds bounds = calculatePathBounds();
        if (bounds != null) {
            int padding = (int) (MAP_PADDING * getResources().getDisplayMetrics().density);
            mAMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
        } else {
            if (!mOriginalPoints.isEmpty()) {
                mAMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mOriginalPoints.get(0), 15));
            }
        }
    }

    /**
     * 计算轨迹边界
     */
    private LatLngBounds calculatePathBounds() {
        if (mOriginalPoints.isEmpty()) {
            Log.w(TAG, "原始轨迹点为空，无法计算边界");
            return null;
        }

        LatLngBounds.Builder builder = LatLngBounds.builder();
        for (LatLng point : mOriginalPoints) {
            builder.include(point);
        }
        for (List<LatLng> segment : mSegmentPoints) {
            for (LatLng point : segment) {
                builder.include(point);
            }
        }
        return builder.build();
    }

    /**
     * 根据速度获取颜色
     */
    private int getColorBySpeed(double speed) {
        float s = Math.max(SPEED_LOW, Math.min(SPEED_HIGH, (float) speed));
        if (s <= SPEED_MID) {
            float ratio = (s - SPEED_LOW) / (SPEED_MID - SPEED_LOW);
            return Color.rgb(
                    (int) (Color.red(COLOR_LOW) + ratio * (Color.red(COLOR_MID) - Color.red(COLOR_LOW))),
                    (int) (Color.green(COLOR_LOW) + ratio * (Color.green(COLOR_MID) - Color.green(COLOR_LOW))),
                    (int) (Color.blue(COLOR_LOW) + ratio * (Color.blue(COLOR_MID) - Color.blue(COLOR_LOW)))
            );
        } else {
            float ratio = (s - SPEED_MID) / (SPEED_HIGH - SPEED_MID);
            return Color.rgb(
                    (int) (Color.red(COLOR_MID) + ratio * (Color.red(COLOR_HIGH) - Color.red(COLOR_MID))),
                    (int) (Color.green(COLOR_MID) + ratio * (Color.green(COLOR_HIGH) - Color.green(COLOR_MID))),
                    (int) (Color.blue(COLOR_MID) + ratio * (Color.blue(COLOR_HIGH) - Color.blue(COLOR_MID)))
            );
        }
    }

    /**
     * 开始/暂停动画
     */
    private void toggleAnimation() {
        if (isAnimFinished) {
            Toast.makeText(this, "请先重置", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mAnimator == null || !mAnimator.isRunning()) {
            clearDraws();
            clearDefaultPath();
            startAnimation();
            mBtnStartPause.setText("暂停");
            isAnimRunning = true;
        } else {
            mAnimator.pause();
            mBtnStartPause.setText("继续");
            isAnimRunning = false;
        }
    }

    /**
     * 执行动画
     */
    private void startAnimation() {
        drawStartMarker();

        mAnimator = ValueAnimator.ofFloat(0f, mSegmentPoints.size());
        mAnimator.setDuration(mSegmentPoints.size() * TIME_PER_SEGMENT);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.setFrameDelay(16);

        final int[] lastSegment = {0};
        final float[] lastProgress = {0f};
        mAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            if (Math.abs(progress - lastProgress[0]) < 0.01) return;
            lastProgress[0] = progress;

            int currSeg = (int) Math.floor(progress);
            float segRatio = progress - currSeg;

            // 绘制已完成分段
            while (lastSegment[0] < currSeg && lastSegment[0] < mSegmentPoints.size()) {
                int segIndex = lastSegment[0];
                List<LatLng> segPoints = mSegmentPoints.get(segIndex);
                double segRawSpeed = mSpeedList.get(segIndex);
                int color = mSegmentColors.get(segIndex);

                mDrawnLines.add(mAMap.addPolyline(new PolylineOptions()
                        .addAll(segPoints)
                        .width(15f)
                        .color(color)
                ));
                lastSegment[0]++;
            }

            // 绘制当前分段
            if (currSeg < mSegmentPoints.size() && segRatio > 0) {
                List<LatLng> currPoints = mSegmentPoints.get(currSeg);
                int drawCount = (int) Math.ceil(segRatio * currPoints.size());
                if (drawCount > 0 && drawCount <= currPoints.size()) {
                    if (mDrawnLines.size() > lastSegment[0]) {
                        mDrawnLines.get(lastSegment[0]).remove();
                        mDrawnLines.remove(lastSegment[0]);
                    }
                    List<LatLng> partial = currPoints.subList(0, drawCount);

                    double currRawSpeed = mSpeedList.get(currSeg);
                    int color = mSegmentColors.get(currSeg);

                    mDrawnLines.add(lastSegment[0], mAMap.addPolyline(new PolylineOptions()
                            .addAll(partial)
                            .width(15f)
                            .color(color)
                    ));

                    // 更新移动标记
                    long currentTime = System.currentTimeMillis();
                    LatLng currentPos = partial.get(partial.size() - 1);
                    if (lastMarkerPos == null || !lastMarkerPos.equals(currentPos) || currentTime - lastMarkerUpdateTime > MARKER_UPDATE_INTERVAL) {
                        drawMovingMarker(currentPos, currSeg);
                        if (!isMapFullscreen) {
                            mAMap.animateCamera(CameraUpdateFactory.newLatLng(currentPos), 50, null);
                        }
                        lastMarkerUpdateTime = currentTime;
                        lastMarkerPos = currentPos;
                    }
                }
            }
        });

        // 动画监听
        mAnimator.addListener(new android.animation.Animator.AnimatorListener() {
            @Override
            public void onAnimationEnd(android.animation.Animator animator) {
                isAnimFinished = true;
                isAnimRunning = false;
                mBtnStartPause.setText("完成");
                drawEndMarker();

                LatLngBounds bounds = calculatePathBounds();
                if (bounds != null) {
                    int padding = (int) (MAP_PADDING * getResources().getDisplayMetrics().density);
                    mAMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding), 1000, null);
                }
            }

            @Override
            public void onAnimationStart(android.animation.Animator animator) {}

            @Override
            public void onAnimationCancel(android.animation.Animator animator) {}

            @Override
            public void onAnimationRepeat(android.animation.Animator animator) {}
        });
        mAnimator.start();
    }

    /**
     * 绘制起点标记
     */
    private void drawStartMarker() {
        if (mStartMarker == null) {
            mStartMarker = mAMap.addMarker(new MarkerOptions()
                    .position(mOriginalPoints.get(0))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .title("起点")
                    .anchor(0.5f, 0.5f));
        }
    }

    /**
     * 绘制终点标记
     */
    private void drawEndMarker() {
        if (mEndMarker == null) {
            mEndMarker = mAMap.addMarker(new MarkerOptions()
                    .position(mOriginalPoints.get(mOriginalPoints.size() - 1))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .title("终点")
                    .anchor(0.5f, 0.5f));
        }
    }

    /**
     * 绘制移动标记
     */
    private void drawMovingMarker(LatLng pos, int currentSegmentIndex) {
        if (mMovingMarker == null) {
            mMovingMarker = mAMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    .anchor(0.5f, 0.5f)
                    .setFlat(true));
        } else {
            mMovingMarker.setPosition(pos);
        }
    }

    /**
     * 切换全屏
     */
    private void toggleMapFullscreen() {
        View llTopHeader = findViewById(R.id.ll_top_header);
        View tvTimeLocation = findViewById(R.id.tv_time_location);
        View llDistance = findViewById(R.id.ll_distance);
        View llDataCard = findViewById(R.id.ll_data_card);
        View llSegmentDetail = findViewById(R.id.ll_segment_detail);
        View flMapContainer = findViewById(R.id.fl_map_container);

        if (flMapContainer == null) return;

        ConstraintLayout.LayoutParams mapParams = (ConstraintLayout.LayoutParams) flMapContainer.getLayoutParams();

        if (!isMapFullscreen) {
            isMapFullscreen = true;

            mapParams.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
            mapParams.height = ConstraintLayout.LayoutParams.MATCH_PARENT;
            mapParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            mapParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            mapParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            mapParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            mapParams.setMargins(0, 0, 0, 0);
            flMapContainer.setLayoutParams(mapParams);

            if (llTopHeader != null) llTopHeader.setVisibility(View.GONE);
            if (tvTimeLocation != null) tvTimeLocation.setVisibility(View.GONE);
            if (llDistance != null) llDistance.setVisibility(View.GONE);
            if (llDataCard != null) llDataCard.setVisibility(View.GONE);
            if (llSegmentDetail != null) llSegmentDetail.setVisibility(View.GONE);

            if (mIvBack != null) mIvBack.setVisibility(View.VISIBLE);
            if (mLlFullscreen != null) mLlFullscreen.setVisibility(View.GONE);

            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
        } else {
            isMapFullscreen = false;

            mapParams.width = originalMapWidth;
            mapParams.height = originalMapHeight;
            mapParams.topToBottom = originalMapTopToBottom;
            mapParams.bottomToTop = originalMapBottomToTop;
            mapParams.setMargins(originalMapMarginLeft, originalMapMarginTop,
                    originalMapMarginRight, originalMapMarginBottom);
            flMapContainer.setLayoutParams(mapParams);

            if (llTopHeader != null) llTopHeader.setVisibility(View.VISIBLE);
            if (tvTimeLocation != null) tvTimeLocation.setVisibility(View.VISIBLE);
            if (llDistance != null) llDistance.setVisibility(View.VISIBLE);
            if (llDataCard != null) llDataCard.setVisibility(View.VISIBLE);
            if (llSegmentDetail != null) llSegmentDetail.setVisibility(View.VISIBLE);

            if (mIvBack != null) mIvBack.setVisibility(View.GONE);
            if (mLlFullscreen != null) mLlFullscreen.setVisibility(View.VISIBLE);

            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            if (getSupportActionBar() != null) {
                getSupportActionBar().show();
            }
        }
    }

    /**
     * 重置动画
     */
    private void resetAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        clearDraws();
        clearDefaultPath();
        drawDefaultPath();

        if (mMovingMarker != null) {
            mMovingMarker.remove();
            mMovingMarker = null;
        }
        lastMarkerPos = null;
        lastMarkerUpdateTime = 0;

        isAnimRunning = false;
        isAnimFinished = false;
        mBtnStartPause.setText("开始");
    }

    /**
     * 清空动态轨迹
     */
    private void clearDraws() {
        for (Polyline line : mDrawnLines) {
            line.remove();
        }
        mDrawnLines.clear();
    }

    /**
     * 清空默认轨迹
     */
    private void clearDefaultPath() {
        for (Polyline line : mDefaultLines) {
            line.remove();
        }
        mDefaultLines.clear();
        if (mStartMarker != null) {
            mStartMarker.remove();
            mStartMarker = null;
        }
        if (mEndMarker != null) {
            mEndMarker.remove();
            mEndMarker = null;
        }
    }

    /**
     * 跳转到分段详情
     */
    private void jumpToSegmentDetail() {
        if (mOriginalPoints.isEmpty() || mTimestampList.isEmpty()) {
            Toast.makeText(this, "无GPS数据，无法查看分段详情", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, SegmentDetailActivity.class);
        double[] accumulatedDistances = calculateAccumulatedDistances();

        long[] timestampsArray = new long[mTimestampList.size()];
        for (int i = 0; i < mTimestampList.size(); i++) {
            timestampsArray[i] = mTimestampList.get(i);
        }

        double[] speedsArray = new double[mSpeedList.size()];
        for (int i = 0; i < mSpeedList.size(); i++) {
            speedsArray[i] = mSpeedList.get(i);
        }

        intent.putExtra("GPS_TIMESTAMPS", timestampsArray);
        intent.putExtra("GPS_DISTANCES", accumulatedDistances);
        intent.putExtra("GPS_SPEEDS", speedsArray);
        intent.putExtra("TOTAL_DISTANCE", mTotalDistanceKm);
        intent.putExtra("TOTAL_TIME", mTotalSeconds);

        startActivity(intent);
    }

    /**
     * 计算累计距离
     */
    private double[] calculateAccumulatedDistances() {
        double[] distances = new double[mOriginalPoints.size()];
        distances[0] = 0.0;

        for (int i = 1; i < mOriginalPoints.size(); i++) {
            LatLng p1 = mOriginalPoints.get(i-1);
            LatLng p2 = mOriginalPoints.get(i);
            double segmentDistance = calculateDistance(p1, p2);
            distances[i] = distances[i-1] + segmentDistance;
        }
        return distances;
    }

    /**
     * 计算两点距离
     */
    private double calculateDistance(LatLng p1, LatLng p2) {
        double lat1 = Math.toRadians(p1.latitude);
        double lon1 = Math.toRadians(p1.longitude);
        double lat2 = Math.toRadians(p2.latitude);
        double lon2 = Math.toRadians(p2.longitude);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return EARTH_RADIUS * c;
    }

    /**
     * 时间戳转字符串
     */
    private String timestampToDateTime(long timestampMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestampMs));
    }

    /**
     * 格式化时长（分:秒）
     */
    private String formatDurationOnly(long totalSeconds) {
        totalSeconds = Math.max(1, totalSeconds);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    /**
     * 格式化秒数
     */
    private String formatSeconds(long seconds) {
        seconds = Math.max(1, seconds);
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    /**
     * 检查权限
     */
    private boolean checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * 权限回调
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                receiveTrackDataFromIntent();
                if (mOriginalPoints.size() >= 2) {
                    calculateAllSportData();
                    calculateFiveKmSegments();
                    updateSportDataUI();
                    updateFirstFourFiveKmSegmentsUI();
                    initData();
                    drawDefaultPath();
                    mBtnReset.setEnabled(true);
                    mBtnStartPause.setEnabled(true);
                } else {
                    Toast.makeText(this, "该骑行记录无有效GPS数据", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "需要定位和网络权限才能显示轨迹", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * 点击事件处理
     */
    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_start_pause) {
            toggleAnimation();
        } else if (id == R.id.btn_reset) {
            resetAnimation();
        } else if (id == R.id.ll_fullscreen) {
            toggleMapFullscreen();
        } else if (id == R.id.iv_back) {
            toggleMapFullscreen();
        } else if (id == R.id.tv_segment_detail_btn) {
            jumpToSegmentDetail();
        } else if (id == R.id.tv_sport_detail_btn) {
            Intent intent = new Intent(SportTrackAnimationActivity.this, SportDataDetailActivity.class);
            // 转换速度列表为float数组
            float[] speedArray = new float[mSpeedList.size()];
            for (int i = 0; i < mSpeedList.size(); i++) {
                speedArray[i] = (float) mSpeedList.get(i).doubleValue();
            }
            // 转换海拔列表为float数组
            float[] altitudeArray = new float[mAltitudeList.size()];
            for (int i = 0; i < mAltitudeList.size(); i++) {
                altitudeArray[i] = (float) mAltitudeList.get(i).doubleValue();
            }
            // 传递数据
            intent.putExtra("REAL_SPEED_DATA", speedArray);
            intent.putExtra("REAL_ALTITUDE_DATA", altitudeArray);
            intent.putExtra("TOTAL_SPORT_SECONDS", mTotalSeconds);
            intent.putExtra("AVG_SPEED", mAvgSpeed);
            intent.putExtra("MAX_SPEED", calculateMaxSpeed());
            intent.putExtra("TOTAL_CLIMB", mClimbHeight);
            intent.putExtra("MAX_ALTITUDE", calculateMaxAltitude());
            startActivity(intent);
        }
    }

    // 计算最大速度
    private double calculateMaxSpeed() {
        if (mSpeedList.isEmpty()) return 0;
        double max = 0;
        for (double speed : mSpeedList) {
            if (speed > max) max = speed;
        }
        return max;
    }

    // 计算最高海拔
    private double calculateMaxAltitude() {
        if (mAltitudeList.isEmpty()) return 0;
        double max = 0;
        for (double alt : mAltitudeList) {
            if (alt > max) max = alt;
        }
        return max;
    }

    /**
     * 生命周期
     */
    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
        if (isAnimRunning && mAnimator != null) {
            mAnimator.pause();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
        if (mAnimator != null) {
            mAnimator.cancel();
        }
    }
}
