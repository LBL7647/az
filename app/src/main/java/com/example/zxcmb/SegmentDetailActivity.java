package com.example.zxcmb;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// 分段详情页面（展示1公里/5公里骑行分段数据）
public class SegmentDetailActivity extends AppCompatActivity {
    // 列宽配置（dp）
    private static final int COL_WIDTH_NUM = 60;
    private static final int COL_WIDTH_KM = 80;
    private static final int COL_WIDTH_TIME = 80;
    private static final int COL_WIDTH_SPEED = 80;
    private static final String TAG = "SegmentTimeCheck";

    // UI容器
    private LinearLayout fiveKmContainer;
    private LinearLayout oneKmContainer;

    // 数据存储
    private List<GPSPoint> gpsPointList = new ArrayList<>(); // GPS原始数据列表
    private double totalDistance;     // 总骑行距离（公里）
    private long totalTimeSeconds;    // 总骑行时长（秒）

    // 分段数据
    private List<SegmentData> oneKmSegments = new ArrayList<>();   // 1公里分段数据
    private List<SegmentData> fiveKmSegments = new ArrayList<>();  // 5公里分段数据

    // GPS点模型（存储单条GPS数据）
    private static class GPSPoint {
        long timestamp;   // 时间戳（毫秒）
        double distance;  // 累计距离（米）
        double speed;     // 速度（km/h）
        String timeStr;   // 时间字符串（yyyy-MM-dd HH:mm:ss）
    }

    // 分段数据模型（存储单段骑行数据）
    private static class SegmentData {
        int segmentNum;        // 分段编号
        double distanceKm;     // 该段距离（公里）
        String time;           // 该段用时（MM:SS）
        String totalTime;      // 累计用时（MM:SS）
        double speed;          // 该段平均速度（km/h）
        boolean isFastest;     // 是否为最快分段
        long rawTimeSeconds;   // 分段原始时长（秒）
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_segment_detail);

        // 设置页面标题和返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("5公里分段详情");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 绑定UI容器
        fiveKmContainer = findViewById(R.id.ll_5km_container);
        oneKmContainer = findViewById(R.id.ll_1km_container);

        // 数据处理流程
        receiveGPSDataFromIntent(); // 接收传递的GPS数据
        sortGPSPointsByTime();      // 按时间排序GPS点
        calculateSegments();        // 计算分段数据
        calibrateTotalTime();       // 校准总时长
        fillFiveKmData();           // 填充5公里分段UI
        fillOneKmData();            // 填充1公里分段UI
    }

    /**
     * 接收从上个页面传递的GPS数据
     */
    private void receiveGPSDataFromIntent() {
        // 获取传递的数组数据
        long[] timestamps = getIntent().getLongArrayExtra("GPS_TIMESTAMPS");
        double[] distances = getIntent().getDoubleArrayExtra("GPS_DISTANCES");
        double[] speeds = getIntent().getDoubleArrayExtra("GPS_SPEEDS");
        totalDistance = getIntent().getDoubleExtra("TOTAL_DISTANCE", 0);
        totalTimeSeconds = getIntent().getLongExtra("TOTAL_TIME", 0);

        // 构建GPS点列表
        if (timestamps != null && distances != null && speeds != null
                && timestamps.length == distances.length && distances.length == speeds.length) {
            for (int i = 0; i < timestamps.length; i++) {
                GPSPoint point = new GPSPoint();
                point.timestamp = timestamps[i];
                point.distance = distances[i];
                // 过滤无效速度
                point.speed = (speeds[i] > 0 && speeds[i] < 200) ? speeds[i] : 0;
                point.timeStr = timestampToDateTime(point.timestamp);
                gpsPointList.add(point);

                // 打印GPS点日志
                Log.d(TAG, "GPS点" + i + "：");
                Log.d(TAG, "  原始时间戳(ms)=" + point.timestamp);
                Log.d(TAG, "  原始时间=" + point.timeStr);
                Log.d(TAG, "  相对起始时间=" + getRelativeSeconds(point.timestamp) + "秒");
                Log.d(TAG, "  累计距离=" + point.distance + "米");
                Log.d(TAG, "  GPS原始速度=" + point.speed + "km/h");
            }
        }
        Log.d(TAG, "接收GPS数据：共" + gpsPointList.size() + "个点，总距离=" + totalDistance +
                "km，训练总时长=" + totalTimeSeconds + "秒（" + formatSeconds(totalTimeSeconds) + "）");
    }

    /**
     * 按时间戳排序GPS点
     */
    private void sortGPSPointsByTime() {
        Collections.sort(gpsPointList, new Comparator<GPSPoint>() {
            @Override
            public int compare(GPSPoint o1, GPSPoint o2) {
                return Long.compare(o1.timestamp, o2.timestamp);
            }
        });
        Log.d(TAG, "GPS点已按时间戳排序，首个点时间戳=" + gpsPointList.get(0).timestamp +
                " 原始时间=" + gpsPointList.get(0).timeStr);
    }

    /**
     * 计算1公里和5公里分段数据
     */
    private void calculateSegments() {
        if (gpsPointList.size() < 2) return;

        calculateOneKmSegments();   // 计算1公里分段
        calculateFiveKmSegments();  // 计算5公里分段

        // 打印校准前日志
        long actualTotalTimeMs = gpsPointList.get(gpsPointList.size() - 1).timestamp
                - gpsPointList.get(0).timestamp;
        long actualTotalTimeSec = Math.round((double) actualTotalTimeMs / 1000);
        Log.d(TAG, "\n===== 校准前总时间校验 =====");
        Log.d(TAG, "GPS点原始总时长(秒)：" + actualTotalTimeSec + "（" + formatSeconds(actualTotalTimeSec) + "）");
        Log.d(TAG, "外部训练总时长(秒)：" + totalTimeSeconds + "（" + formatSeconds(totalTimeSeconds) + "）");
        Log.d(TAG, "校准前分段累加总时长(秒)：" + (oneKmSegments.isEmpty() ? 0 :
                getOneKmTotalSeconds()) + "（" + formatSeconds(getOneKmTotalSeconds()) + "）");
        Log.d(TAG, "时长偏差(秒)：" + Math.abs(totalTimeSeconds - getOneKmTotalSeconds()));
    }

    /**
     * 校准分段总时长，匹配外部传入的训练总时长
     */
    private void calibrateTotalTime() {
        if (oneKmSegments.isEmpty() || totalTimeSeconds <= 0) return;

        // 获取当前分段累加总时长
        long currentTotal = getOneKmTotalSeconds();
        if (currentTotal == totalTimeSeconds) {
            Log.d(TAG, "分段总时长已匹配训练总时长，无需校准");
            return;
        }

        // 计算校准比率
        double ratio = (double) totalTimeSeconds / currentTotal;
        Log.d(TAG, "\n===== 开始校准分段时长 =====");
        Log.d(TAG, "校准前总时长=" + currentTotal + "秒，目标总时长=" + totalTimeSeconds + "秒");
        Log.d(TAG, "校准比率=" + ratio);

        // 按比率校准每个分段
        long calibratedTotal = 0;
        int lastIndex = oneKmSegments.size() - 1;
        for (int i = 0; i < oneKmSegments.size(); i++) {
            SegmentData seg = oneKmSegments.get(i);
            if (i == lastIndex) {
                // 最后一段直接补足到目标时长
                seg.rawTimeSeconds = totalTimeSeconds - calibratedTotal;
                seg.rawTimeSeconds = Math.max(1, seg.rawTimeSeconds);
            } else {
                // 前N-1段按比率校准
                long calibratedSec = Math.round(seg.rawTimeSeconds * ratio);
                seg.rawTimeSeconds = Math.max(1, calibratedSec);
            }
            // 更新格式化时间
            seg.time = formatSeconds(seg.rawTimeSeconds);
            calibratedTotal += seg.rawTimeSeconds;
            seg.totalTime = formatSeconds(calibratedTotal);

            // 打印校准日志
            Log.d(TAG, "校准后第" + seg.segmentNum + "公里：");
            Log.d(TAG, "  原始秒数=" + seg.rawTimeSeconds + " → 格式化=" + seg.time);
            Log.d(TAG, "  累计秒数=" + calibratedTotal + " → 格式化=" + seg.totalTime);
        }

        // 重新计算5公里分段
        recalculateFiveKmSegments();

        // 打印校准后日志
        Log.d(TAG, "\n===== 校准后总时间校验 =====");
        Log.d(TAG, "校准后分段累加总时长(秒)：" + getOneKmTotalSeconds() + "（" + formatSeconds(getOneKmTotalSeconds()) + "）");
        Log.d(TAG, "外部训练总时长(秒)：" + totalTimeSeconds + "（" + formatSeconds(totalTimeSeconds) + "）");
        Log.d(TAG, "校准后偏差(秒)：" + Math.abs(totalTimeSeconds - getOneKmTotalSeconds()));
    }

    /**
     * 重新计算5公里分段（基于校准后的1公里数据）
     */
    private void recalculateFiveKmSegments() {
        fiveKmSegments.clear();
        if (oneKmSegments.isEmpty()) return;

        int current5km = 1;
        int startKmIdx = 0;
        long total5kmElapsed = 0;
        double maxSpeed = 0;
        int fastestIndex = -1;

        Log.d(TAG, "\n重新计算5公里分段（校准后）：共" + oneKmSegments.size() + "个1公里分段");

        // 循环计算5公里分段
        while (startKmIdx < oneKmSegments.size()) {
            double accumulatedKm = 0.0;
            long accumulatedTime = 0;
            double accumulatedSpeedSum = 0;
            int speedCount = 0;
            int endKmIdx = startKmIdx;

            // 累加1公里分段数据
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

            // 累加总时间
            total5kmElapsed += accumulatedTime;

            // 构建5公里分段数据
            SegmentData seg = new SegmentData();
            seg.segmentNum = current5km;
            seg.distanceKm = Math.round(accumulatedKm * 100.0) / 100.0;
            seg.time = formatSeconds(accumulatedTime);
            seg.totalTime = formatSeconds(total5kmElapsed);
            seg.speed = fiveKmAvgSpeed;
            seg.isFastest = false;
            seg.rawTimeSeconds = accumulatedTime;
            fiveKmSegments.add(seg);

            // 记录最快分段
            if (fiveKmAvgSpeed > maxSpeed) {
                maxSpeed = fiveKmAvgSpeed;
                fastestIndex = fiveKmSegments.size() - 1;
            }

            current5km++;
            startKmIdx = endKmIdx + 1;
        }

        // 标记最快分段
        if (fastestIndex >= 0 && fastestIndex < fiveKmSegments.size()) {
            fiveKmSegments.get(fastestIndex).isFastest = true;
        }

        Log.d(TAG, "5公里分段重新计算完成，共" + fiveKmSegments.size() + "个分段");
    }

    /**
     * 获取1公里分段累加总时长（秒）
     */
    private long getOneKmTotalSeconds() {
        long total = 0;
        for (SegmentData seg : oneKmSegments) {
            total += seg.rawTimeSeconds;
        }
        return total;
    }

    /**
     * 计算1公里分段数据
     */
    private void calculateOneKmSegments() {
        oneKmSegments.clear();
        if (gpsPointList.isEmpty()) return;

        int currentKm = 1;
        double targetKmDist = 1000; // 1公里=1000米
        int startIdx = 0;
        long startTimestamp = gpsPointList.get(0).timestamp;
        long actualTotalElapsed = 0;
        double maxSpeed = 0;
        int fastestIndex = -1;

        Log.d(TAG, "\n开始计算1公里分段：");
        Log.d(TAG, "起始时间戳=" + startTimestamp + " 原始时间=" + timestampToDateTime(startTimestamp));
        Log.d(TAG, "目标距离=" + targetKmDist + "米");

        // 遍历GPS点计算分段
        for (int i = 1; i < gpsPointList.size(); i++) {
            GPSPoint currPoint = gpsPointList.get(i);
            double currDist = currPoint.distance;

            // 达到1公里距离时切割分段
            if (currDist >= targetKmDist) {
                GPSPoint prevPoint = gpsPointList.get(i-1);
                double prevDist = prevPoint.distance;

                // 插值计算精准的1公里时间戳
                double distDiff = targetKmDist - prevDist;
                double totalDistDiff = currDist - prevDist;
                long timeDiffMs = currPoint.timestamp - prevPoint.timestamp;
                long preciseTimeMs = prevPoint.timestamp + (long) (timeDiffMs * (distDiff / totalDistDiff));

                // 获取该分段的GPS点
                List<GPSPoint> segmentPoints = gpsPointList.subList(startIdx, i + 1);
                if (segmentPoints.isEmpty()) continue;

                // 计算平均速度
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

                // 累加总时间
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

                // 打印分段日志
                Log.d(TAG, "\n第" + currentKm + "公里分段：");
                Log.d(TAG, "  起始原始时间戳=" + startTimestamp + " → " + timestampToDateTime(startTimestamp));
                Log.d(TAG, "  结束原始时间戳=" + currPoint.timestamp + " → " + currPoint.timeStr);
                Log.d(TAG, "  插值后精准时间戳=" + preciseTimeMs + " → " + timestampToDateTime(preciseTimeMs));
                Log.d(TAG, "  原始时间差=" + (currPoint.timestamp - startTimestamp) + "ms");
                Log.d(TAG, "  插值后时间差=" + segmentTimeMs + "ms");
                Log.d(TAG, "  最终秒数=" + segmentTimeSeconds + " → 格式化=" + seg.time);
                Log.d(TAG, "  实际距离=" + actualKm + "km 平均速度=" + segmentAvgSpeed + "km/h");
                Log.d(TAG, "  累计时间=" + actualTotalElapsed + "秒 → 格式化=" + seg.totalTime);

                // 记录最快分段
                if (segmentAvgSpeed > maxSpeed) {
                    maxSpeed = segmentAvgSpeed;
                    fastestIndex = oneKmSegments.size() - 1;
                }

                // 准备下一个分段
                currentKm++;
                targetKmDist = currentKm * 1000;
                startIdx = i;
                startTimestamp = preciseTimeMs;
            }
        }

        // 处理最后不足1公里的部分
        if (startIdx < gpsPointList.size() - 1) {
            List<GPSPoint> lastSegment = gpsPointList.subList(startIdx, gpsPointList.size());
            if (lastSegment.size() < 2) return;

            GPSPoint lastPoint = gpsPointList.get(gpsPointList.size() - 1);
            double lastDist = lastPoint.distance - (currentKm - 1) * 1000;
            if (lastDist <= 0) return;

            // 计算最后一段平均速度
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

            // 计算最后一段用时
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

            // 打印最后一段日志
            Log.d(TAG, "\n最后不足1公里分段：");
            Log.d(TAG, "  起始原始时间戳=" + startTimestamp + " → " + timestampToDateTime(startTimestamp));
            Log.d(TAG, "  结束原始时间戳=" + lastPoint.timestamp + " → " + lastPoint.timeStr);
            Log.d(TAG, "  原始时间差=" + lastSegmentTimeMs + "ms");
            Log.d(TAG, "  最终秒数=" + lastTimeSeconds + " → 格式化=" + seg.time);
            Log.d(TAG, "  实际距离=" + seg.distanceKm + "km 平均速度=" + lastAvgSpeed + "km/h");
            Log.d(TAG, "  累计时间=" + actualTotalElapsed + "秒 → 格式化=" + seg.totalTime);

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
    }

    /**
     * 计算5公里分段数据
     */
    private void calculateFiveKmSegments() {
        fiveKmSegments.clear();
        if (oneKmSegments.isEmpty()) return;

        int current5km = 1;
        int startKmIdx = 0;
        long total5kmElapsed = 0;
        double maxSpeed = 0;
        int fastestIndex = -1;

        Log.d(TAG, "\n开始计算5公里分段：共" + oneKmSegments.size() + "个1公里分段");

        // 循环计算5公里分段
        while (startKmIdx < oneKmSegments.size()) {
            double accumulatedKm = 0.0;
            long accumulatedTime = 0;
            double accumulatedSpeedSum = 0;
            int speedCount = 0;
            int endKmIdx = startKmIdx;

            // 累加1公里分段数据
            for (int i = startKmIdx; i < oneKmSegments.size(); i++) {
                SegmentData oneKmSeg = oneKmSegments.get(i);
                accumulatedKm += oneKmSeg.distanceKm;
                accumulatedTime += oneKmSeg.rawTimeSeconds;
                accumulatedSpeedSum += oneKmSeg.speed;
                speedCount++;
                endKmIdx = i;

                // 打印累加日志
                Log.d(TAG, "累计5公里-第" + (i - startKmIdx + 1) + "段：");
                Log.d(TAG, "  1公里段" + oneKmSeg.segmentNum + "：");
                Log.d(TAG, "    原始耗时=" + oneKmSeg.rawTimeSeconds + "秒 → 格式化=" + oneKmSeg.time);
                Log.d(TAG, "    平均速度=" + oneKmSeg.speed + "km/h");
                Log.d(TAG, "  累计距离=" + accumulatedKm + "km 累计原始时间=" + accumulatedTime + "秒");

                if (accumulatedKm >= 5.0) break;
            }

            // 计算平均速度
            double fiveKmAvgSpeed = 0;
            if (speedCount > 0) {
                fiveKmAvgSpeed = Math.round(accumulatedSpeedSum / speedCount * 10.0) / 10.0;
            }

            // 累加总时间
            total5kmElapsed += accumulatedTime;

            // 构建5公里分段数据
            SegmentData seg = new SegmentData();
            seg.segmentNum = current5km;
            seg.distanceKm = Math.round(accumulatedKm * 100.0) / 100.0;
            seg.time = formatSeconds(accumulatedTime);
            seg.totalTime = formatSeconds(total5kmElapsed);
            seg.speed = fiveKmAvgSpeed;
            seg.isFastest = false;
            seg.rawTimeSeconds = accumulatedTime;
            fiveKmSegments.add(seg);

            // 打印5公里分段日志
            Log.d(TAG, "\n第" + current5km + "个5公里分段：");
            Log.d(TAG, "  覆盖1公里段范围：" + (startKmIdx + 1) + " ~ " + (endKmIdx + 1));
            Log.d(TAG, "  累计距离=" + seg.distanceKm + "km");
            Log.d(TAG, "  累计原始时间=" + accumulatedTime + "秒 → 格式化=" + seg.time);
            Log.d(TAG, "  平均速度=" + fiveKmAvgSpeed + "km/h");
            Log.d(TAG, "  总累计时间=" + total5kmElapsed + "秒 → 格式化=" + seg.totalTime);

            // 记录最快分段
            if (fiveKmAvgSpeed > maxSpeed) {
                maxSpeed = fiveKmAvgSpeed;
                fastestIndex = fiveKmSegments.size() - 1;
            }

            // 准备下一个分段
            current5km++;
            startKmIdx = endKmIdx + 1;
        }

        // 标记最快分段
        if (fastestIndex >= 0 && fastestIndex < fiveKmSegments.size()) {
            fiveKmSegments.get(fastestIndex).isFastest = true;
            Log.d(TAG, "\n最快5公里分段：第" + (fastestIndex + 1) + "段，速度=" + maxSpeed + "km/h");
        }
    }

    /**
     * 填充5公里分段数据到UI
     */
    private void fillFiveKmData() {
        if (fiveKmSegments.isEmpty()) {
            addEmptyView(fiveKmContainer, "暂无5公里分段数据");
            return;
        }

        // 遍历填充每个分段
        for (int i = 0; i < fiveKmSegments.size(); i++) {
            SegmentData seg = fiveKmSegments.get(i);
            LinearLayout item = createSegmentItem(seg.isFastest);

            // 添加各列数据
            item.addView(createFixedWidthTextView(String.valueOf(seg.segmentNum), COL_WIDTH_NUM, seg.isFastest));
            item.addView(createKmFixedView(String.valueOf(seg.distanceKm), seg.isFastest));
            item.addView(createFixedWidthTextView(seg.time, COL_WIDTH_TIME, seg.isFastest));
            item.addView(createFixedWidthTextView(seg.totalTime, COL_WIDTH_TIME, seg.isFastest));
            item.addView(createSpeedFixedView(String.valueOf(seg.speed), seg.isFastest));

            fiveKmContainer.addView(item);
            // 添加分隔线（最后一个不加）
            addDivider(fiveKmContainer, i != fiveKmSegments.size() - 1);
        }
    }

    /**
     * 填充1公里分段数据到UI
     */
    private void fillOneKmData() {
        if (oneKmSegments.isEmpty()) {
            addEmptyView(oneKmContainer, "暂无公里分段数据");
            return;
        }

        // 遍历填充每个分段
        for (int i = 0; i < oneKmSegments.size(); i++) {
            SegmentData seg = oneKmSegments.get(i);
            LinearLayout item = createSegmentItem(seg.isFastest);

            // 添加各列数据
            item.addView(createFixedWidthTextView(String.valueOf(seg.segmentNum), COL_WIDTH_NUM, seg.isFastest));
            item.addView(createKmFixedView(String.valueOf(seg.distanceKm), seg.isFastest));
            item.addView(createFixedWidthTextView(seg.time, COL_WIDTH_TIME, seg.isFastest));
            item.addView(createFixedWidthTextView(seg.totalTime, COL_WIDTH_TIME, seg.isFastest));
            item.addView(createSpeedFixedView(String.valueOf(seg.speed), seg.isFastest));

            oneKmContainer.addView(item);
            // 添加分隔线（最后一个不加）
            addDivider(oneKmContainer, i != oneKmSegments.size() - 1);
        }
    }

    // ---------------------- UI工具方法 ----------------------
    /**
     * 创建分段列表项容器
     */
    private LinearLayout createSegmentItem(boolean isFastest) {
        LinearLayout item = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        item.setLayoutParams(params);
        item.setPadding(0, 12, 0, 12);
        // 最快分段设置背景色
        if (isFastest) {
            item.setBackgroundColor(Color.parseColor("#00C853"));
        }
        return item;
    }

    /**
     * 创建固定宽度文本视图
     */
    private TextView createFixedWidthTextView(String text, int widthDp, boolean isFastest) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp2px(widthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tv.setLayoutParams(params);
        tv.setGravity(Gravity.CENTER);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setSingleLine(true);
        // 最快分段文字白色，否则黑色
        tv.setTextColor(isFastest ? Color.WHITE : Color.parseColor("#333333"));
        return tv;
    }

    /**
     * 创建公里数显示视图（包含最快标签）
     */
    private LinearLayout createKmFixedView(String kmValue, boolean isFastest) {
        LinearLayout kmLayout = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp2px(COL_WIDTH_KM),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        kmLayout.setLayoutParams(params);
        kmLayout.setOrientation(LinearLayout.HORIZONTAL);
        kmLayout.setGravity(Gravity.CENTER_VERTICAL);

        // 最快标签
        TextView fastestTag = new TextView(this);
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        tagParams.setMarginStart(dp2px(5));
        if (isFastest) {
            fastestTag.setText("最快");
            fastestTag.setTextSize(12);
            fastestTag.setTextColor(Color.WHITE);
        } else {
            fastestTag.setVisibility(View.INVISIBLE);
            tagParams.width = dp2px(30);
        }
        kmLayout.addView(fastestTag, tagParams);

        // 公里数文本
        TextView kmTv = new TextView(this);
        LinearLayout.LayoutParams kmParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        kmParams.gravity = Gravity.CENTER;
        kmTv.setLayoutParams(kmParams);
        kmTv.setText(kmValue);
        kmTv.setTextSize(14);
        kmTv.setGravity(Gravity.CENTER);
        kmTv.setTextColor(isFastest ? Color.WHITE : Color.parseColor("#333333"));
        kmLayout.addView(kmTv);

        return kmLayout;
    }

    /**
     * 创建速度显示视图
     */
    private LinearLayout createSpeedFixedView(String speedValue, boolean isFastest) {
        LinearLayout speedLayout = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp2px(COL_WIDTH_SPEED),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        speedLayout.setLayoutParams(params);
        speedLayout.setGravity(Gravity.CENTER);
        speedLayout.setOrientation(LinearLayout.VERTICAL);

        TextView speedTv = new TextView(this);
        speedTv.setText(speedValue);
        speedTv.setTextSize(14);
        speedTv.setGravity(Gravity.CENTER);
        speedTv.setTextColor(isFastest ? Color.WHITE : Color.parseColor("#2196F3"));
        speedLayout.addView(speedTv);

        return speedLayout;
    }

    /**
     * 添加分隔线
     */
    private void addDivider(LinearLayout container, boolean needDivider) {
        if (needDivider) {
            View divider = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
            );
            divider.setLayoutParams(params);
            divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
            container.addView(divider);
        }
    }

    /**
     * 添加空数据提示视图
     */
    private void addEmptyView(LinearLayout container, String text) {
        TextView emptyView = new TextView(this);
        emptyView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        emptyView.setText(text);
        emptyView.setPadding(0, 20, 0, 20);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Color.parseColor("#999999"));
        container.addView(emptyView);
    }

    // ---------------------- 工具方法 ----------------------
    /**
     * dp转px
     */
    private int dp2px(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    /**
     * 毫秒时间戳转yyyy-MM-dd HH:mm:ss格式字符串
     */
    private String timestampToDateTime(long timestampMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestampMs));
    }

    /**
     * 计算相对时间（相对于第一个GPS点的秒数）
     */
    private long getRelativeSeconds(long timestampMs) {
        if (gpsPointList.isEmpty()) return 0;
        long startMs = gpsPointList.get(0).timestamp;
        return Math.round((double) (timestampMs - startMs) / 1000);
    }

    /**
     * 格式化秒数为MM:SS格式
     */
    private String formatSeconds(long seconds) {
        seconds = Math.max(1, seconds);
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    /**
     * 解析MM:SS格式字符串为秒数
     */
    private long timeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return 1;
        String[] parts = timeStr.split(":");
        if (parts.length != 2) return 1;

        long m = 0, s = 0;
        try {
            m = Long.parseLong(parts[0].trim());
            s = Long.parseLong(parts[1].trim());
            if (s < 0 || s > 59) s = 0;
            if (m < 0) m = 0;
        } catch (NumberFormatException e) {
            return 1;
        }

        long total = m * 60 + s;
        return Math.max(1, total);
    }

    /**
     * 返回按钮点击事件
     */
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
