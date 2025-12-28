package com.example.zxcmb;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.utils.MPPointD;

import java.util.ArrayList;

public class SportDataDetailActivity extends AppCompatActivity {
    // 数据相关
    private float[] speedData;
    private float[] relativeAltitudeData;
    private long totalSportSeconds;
    private String[] fixedTimeLabels;

    // 海拔基准值
    private float initialAltitude;

    // 图表控件
    private BarChart speedChart;
    private BarChart altitudeChart;
    private PopupWindow dataPopup;

    // 统计数据
    private double avgSpeed;
    private double maxSpeed;
    private int totalClimb;
    private double maxRelativeAltitude;

    // 图表配置常量
    private static final float CHART_X_TEXT_SIZE = 11f;
    private static final float CHART_Y_TEXT_SIZE = 10f;
    private static final int CHART_GRID_COLOR = Color.parseColor("#EEEEEE");
    private static final int CHART_LABEL_COLOR = Color.parseColor("#666666");
    private static final int CHART_X_LABEL_COUNT = 4;
    private static final float CHART_BAR_WIDTH_RATIO_SMALL = 0.7f;
    private static final float CHART_BAR_WIDTH_RATIO_LARGE = 0.8f;
    private static final float SPEED_MIN_BASE = 0f;
    private static final float MARGIN_RATIO = 0.06f;
    private static final float MIN_MARGIN = 1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sport_data_detail);

        // 接收数据
        receiveRealDataFromIntent();

        // 生成时间标签
        generateFixed4TimeLabels();

        // 配置标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("运动数据详情");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 初始化控件
        speedChart = findViewById(R.id.bar_chart_speed);
        altitudeChart = findViewById(R.id.bar_chart_altitude);
        TextView tvSpeedStats = findViewById(R.id.tv_speed_stats);
        TextView tvAltitudeStats = findViewById(R.id.tv_altitude_stats);

        // 设置统计数据显示
        tvSpeedStats.setText(String.format("平均 %.1f   最快 %.1f", avgSpeed, maxSpeed));
        tvAltitudeStats.setText(String.format("累计爬升 %d   最高 +%.0f米", totalClimb, maxRelativeAltitude));

        // 初始化弹窗
        initDataPopup();

        // 初始化图表
        initChartFramework(speedChart, speedData, Color.parseColor("#7B68EE"), Color.parseColor("#5A48B7"), true);
        initChartFramework(altitudeChart, relativeAltitudeData, Color.parseColor("#FF6B35"), Color.parseColor("#D95529"), false);

        // 设置图表点击事件
        setChartClickListener(speedChart, true);
        setChartClickListener(altitudeChart, false);
    }

    /**
     * 接收从上个页面传递的真实数据
     */
    private void receiveRealDataFromIntent() {
        // 获取基础数据
        speedData = getIntent().getFloatArrayExtra("REAL_SPEED_DATA");
        float[] absoluteAltitude = getIntent().getFloatArrayExtra("REAL_ALTITUDE_DATA");
        totalSportSeconds = getIntent().getLongExtra("TOTAL_SPORT_SECONDS", 900);

        // 数据校验兜底
        if (totalSportSeconds <= 0 || totalSportSeconds > 3600) {
            totalSportSeconds = 900;
        }

        // 获取统计数据
        avgSpeed = getIntent().getDoubleExtra("AVG_SPEED", calculateAverage(speedData));
        maxSpeed = getIntent().getDoubleExtra("MAX_SPEED", calculateMax(speedData));
        totalClimb = getIntent().getIntExtra("TOTAL_CLIMB", 0);

        // 数据兜底
        if (speedData == null || speedData.length == 0) {
            speedData = generateDefaultData(15, 25, 40);
        }
        if (absoluteAltitude == null || absoluteAltitude.length == 0) {
            absoluteAltitude = generateDefaultData(15, 50, 150);
        }

        // 计算相对海拔
        initialAltitude = absoluteAltitude[0];
        relativeAltitudeData = new float[absoluteAltitude.length];
        float maxRelative = 0;
        for (int i = 0; i < absoluteAltitude.length; i++) {
            relativeAltitudeData[i] = absoluteAltitude[i] - initialAltitude;
            if (relativeAltitudeData[i] > maxRelative) {
                maxRelative = relativeAltitudeData[i];
            }
        }
        maxRelativeAltitude = maxRelative;

        // 统一数据长度
        speedData = resizeArray(speedData, relativeAltitudeData.length);
    }

    /**
     * 生成4个固定的时间标签
     */
    private void generateFixed4TimeLabels() {
        fixedTimeLabels = new String[4];
        int totalMinutes = (int) (totalSportSeconds / 60);
        int interval = totalMinutes / 3;

        fixedTimeLabels[0] = String.format("%02d:00", 0);
        fixedTimeLabels[1] = String.format("%02d:00", interval);
        fixedTimeLabels[2] = String.format("%02d:00", interval * 2);
        fixedTimeLabels[3] = String.format("%02d:00", totalMinutes);
    }

    /**
     * 初始化图表基础配置
     */
    private void initChartFramework(BarChart chart, float[] data, int barColor, int highlightColor, boolean isSpeed) {
        // 基础配置
        chart.setDrawBarShadow(false);
        chart.setDrawValueAboveBar(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.setScaleEnabled(false);
        chart.setHighlightPerTapEnabled(true);

        // 设置内边距
        chart.setExtraLeftOffset(5f);
        chart.setExtraRightOffset(5f);
        chart.setExtraBottomOffset(5f);
        chart.setExtraTopOffset(5f);

        // 隐藏描述和图例
        Description description = new Description();
        description.setEnabled(false);
        chart.setDescription(description);
        Legend legend = chart.getLegend();
        legend.setEnabled(false);

        // X轴配置
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(CHART_X_LABEL_COUNT, true);
        xAxis.setTextSize(CHART_X_TEXT_SIZE);
        xAxis.setTextColor(CHART_LABEL_COLOR);
        xAxis.setLabelRotationAngle(0f);

        // X轴显示优化
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setDrawLabels(true);
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(data.length - 0.5f);

        // X轴标签格式化
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int labelIndex = Math.round(value / data.length * (CHART_X_LABEL_COUNT - 1));
                labelIndex = Math.max(0, Math.min(CHART_X_LABEL_COUNT - 1, labelIndex));
                return fixedTimeLabels[labelIndex];
            }
        });

        // Y轴配置
        YAxis leftYAxis = chart.getAxisLeft();
        leftYAxis.setDrawGridLines(true);
        leftYAxis.setGridColor(CHART_GRID_COLOR);
        leftYAxis.setTextColor(CHART_LABEL_COLOR);
        leftYAxis.setTextSize(CHART_Y_TEXT_SIZE);

        // 计算数据极值
        float dataMin = calculateMin(data);
        float dataMax = (float) calculateMax(data);
        float dataRange = dataMax - (isSpeed ? SPEED_MIN_BASE : dataMin);

        // 动态计算留白
        float dynamicMargin = dataRange * MARGIN_RATIO;
        dynamicMargin = Math.max(dynamicMargin, MIN_MARGIN);

        // 设置Y轴范围
        float minValue, maxValue;
        if (isSpeed) {
            minValue = SPEED_MIN_BASE - dynamicMargin;
            maxValue = dataMax + dynamicMargin;
        } else {
            minValue = dataMin - dynamicMargin;
            maxValue = dataMax + dynamicMargin;
        }
        leftYAxis.setAxisMinimum(minValue);
        leftYAxis.setAxisMaximum(maxValue);
        chart.getAxisRight().setEnabled(false);

        // 构建图表数据
        ArrayList<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            entries.add(new BarEntry(i, data[i]));
        }
        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColor(barColor);
        dataSet.setBarBorderWidth(0f);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(true);
        dataSet.setHighLightColor(highlightColor);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(data.length > 20 ? CHART_BAR_WIDTH_RATIO_SMALL : CHART_BAR_WIDTH_RATIO_LARGE);
        chart.setData(barData);
        chart.invalidate();
    }

    /**
     * 设置图表点击事件监听
     */
    private void setChartClickListener(BarChart chart, boolean isSpeed) {
        chart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override
            public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}

            @Override
            public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
                if (lastPerformedGesture == ChartTouchListener.ChartGesture.SINGLE_TAP) {
                    MPPointD chartPoint = chart.getTransformer(YAxis.AxisDependency.LEFT).getValuesByTouchPoint(me.getX(), me.getY());
                    int index = Math.round((float) chartPoint.x);

                    float[] targetData = isSpeed ? speedData : relativeAltitudeData;
                    if (index >= 0 && index < targetData.length) {
                        chart.highlightValue(new Highlight(index, 0, 0));
                        int totalMinutes = (int) (totalSportSeconds / 60);
                        int currentMin = (int) (index / (float) targetData.length * totalMinutes);
                        String time = String.format("%02d:00", currentMin);

                        if (isSpeed) {
                            updateSpeedPopupContent(targetData[index], time);
                        } else {
                            updateAltitudePopupContent(targetData[index], time);
                        }
                        showPopup(chart, me.getX(), me.getY());
                    } else {
                        dismissPopup();
                        chart.highlightValues(null);
                    }
                } else {
                    dismissPopup();
                    chart.highlightValues(null);
                }
            }

            @Override
            public void onChartLongPressed(MotionEvent me) {}
            @Override
            public void onChartDoubleTapped(MotionEvent me) {}
            @Override
            public void onChartSingleTapped(MotionEvent me) {}
            @Override
            public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {}
            @Override
            public void onChartScale(MotionEvent me, float scaleX, float scaleY) {}
            @Override
            public void onChartTranslate(MotionEvent me, float dX, float dY) {}
        });
    }

    /**
     * 更新速度弹窗内容
     */
    private void updateSpeedPopupContent(float speed, String time) {
        if (dataPopup == null) return;
        TextView tvType = dataPopup.getContentView().findViewById(R.id.tv_data_type);
        TextView tvValue = dataPopup.getContentView().findViewById(R.id.tv_data_value);
        TextView tvTime = dataPopup.getContentView().findViewById(R.id.tv_data_time);

        tvType.setText("速度");
        tvValue.setText(String.format("%.1f km/h", speed));
        tvTime.setText(time);

        tvType.setTypeface(Typeface.DEFAULT_BOLD);
        tvValue.setTypeface(Typeface.DEFAULT_BOLD);
    }

    /**
     * 更新海拔弹窗内容
     */
    private void updateAltitudePopupContent(float relativeAlt, String time) {
        if (dataPopup == null) return;
        TextView tvType = dataPopup.getContentView().findViewById(R.id.tv_data_type);
        TextView tvValue = dataPopup.getContentView().findViewById(R.id.tv_data_value);
        TextView tvTime = dataPopup.getContentView().findViewById(R.id.tv_data_time);

        tvType.setText("海拔");
        tvValue.setText(String.format("%+.0f m", relativeAlt));
        tvTime.setText(time);

        tvType.setTypeface(Typeface.DEFAULT_BOLD);
        tvValue.setTypeface(Typeface.DEFAULT_BOLD);
    }

    /**
     * 初始化数据弹窗
     */
    private void initDataPopup() {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_chart_detail, null);
        dataPopup = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        dataPopup.setBackgroundDrawable(getResources().getDrawable(android.R.color.transparent));
        dataPopup.setAnimationStyle(android.R.style.Animation_Dialog);
        dataPopup.setOnDismissListener(() -> {
            speedChart.highlightValues(null);
            altitudeChart.highlightValues(null);
        });
    }

    /**
     * 显示弹窗
     */
    private void showPopup(BarChart chart, float x, float y) {
        View contentView = dataPopup.getContentView();
        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = contentView.getMeasuredWidth();
        int h = contentView.getMeasuredHeight();

        int[] loc = new int[2];
        chart.getLocationOnScreen(loc);
        int screenX = loc[0] + (int) x - w/2;
        int screenY = loc[1] + (int) y - h - dp2px(10);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenX = Math.max(0, Math.min(screenX, dm.widthPixels - w));
        screenY = Math.max(loc[1], Math.min(screenY, dm.heightPixels - h));

        if (!dataPopup.isShowing()) {
            dataPopup.showAtLocation(chart, 0, screenX, screenY);
        } else {
            dataPopup.update(screenX, screenY, w, h);
        }
    }

    /**
     * 关闭弹窗
     */
    private void dismissPopup() {
        if (dataPopup != null && dataPopup.isShowing()) {
            dataPopup.dismiss();
        }
    }

    /**
     * 生成默认数据
     */
    private float[] generateDefaultData(int count, float min, float max) {
        float[] data = new float[count];
        for (int i = 0; i < count; i++) {
            data[i] = min + (float) Math.random() * (max - min);
        }
        return data;
    }

    /**
     * 计算平均值
     */
    private double calculateAverage(float[] data) {
        if (data == null || data.length == 0) return 0;
        double sum = 0;
        for (float v : data) sum += v;
        return sum / data.length;
    }

    /**
     * 计算最大值
     */
    private double calculateMax(float[] data) {
        if (data == null || data.length == 0) return 0;
        double max = data[0];
        for (float v : data) {
            if (v > max) max = v;
        }
        return max;
    }

    /**
     * 计算最小值
     */
    private float calculateMin(float[] data) {
        if (data == null || data.length == 0) return 0;
        float min = data[0];
        for (float v : data) {
            if (v < min) min = v;
        }
        return min;
    }

    /**
     * 调整数组长度
     */
    private float[] resizeArray(float[] original, int newLength) {
        float[] newArray = new float[newLength];
        System.arraycopy(original, 0, newArray, 0, Math.min(original.length, newLength));
        if (original.length > 0) {
            float lastValue = original[original.length - 1];
            for (int i = original.length; i < newLength; i++) {
                newArray[i] = lastValue;
            }
        }
        return newArray;
    }

    /**
     * dp转px
     */
    private int dp2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * 返回按钮处理
     */
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    /**
     * 销毁时清理资源
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissPopup();
        dataPopup = null;
    }
}
