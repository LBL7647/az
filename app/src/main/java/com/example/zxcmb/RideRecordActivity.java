package com.example.zxcmb;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences; // 新增导入
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// 骑行记录页面（从OneNET云平台获取骑行数据，解析并展示骑行记录和指标）
public class RideRecordActivity extends AppCompatActivity {
    // ===================== 云平台配置参数（和Python脚本对齐） =====================
    private static final String API_URL = "https://iot-api.heclouds.com/thingmodel/query-device-property-history";
    private static final String AUTHORIZATION_HEADER = "version=2018-10-31&res=products%2F4swK0Xmr9t%2Fdevices%2Fgjcs&et=2053320694&method=md5&sign=9wdIcNP7rEj08dfUTzyVBA%3D%3D";
    private static final String PRODUCT_ID = "4swK0Xmr9t";
    private static final String DEVICE_NAME = "gjcs";
    private static final int LIMIT = 100; // 每页查询条数
    private static final int SORT = 1; // 排序方式
    private static final Pattern PATTERN = Pattern.compile("^\\d{10}([A-Za-z]{5})$"); // 数据后缀匹配正则
    private static final long SEGMENT_DAYS = 7; // 按7天分段查询数据
    private static final String START_DATE_STR = "2025-12-02 00:00:00"; // 数据查询起始时间

    // ===================== 地理计算常量 =====================
    private static final double EARTH_RADIUS = 6371000; // 地球半径(米)

    // ===================== 权限配置 =====================
    private static final int PERMISSION_REQUEST_CODE = 1002;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
    };

    // ===================== UI组件 =====================
    private LinearLayout loadingContainer; // 加载状态容器（包含进度条和提示）
    private ProgressBar progressBar; // 加载进度条
    private RecyclerView rvRideRecords; // 骑行记录列表
    private TextView tvEmpty; // 无数据提示
    private TextView tvError; // 错误提示
    private RideRecordAdapter adapter; // 列表适配器

    // ===================== 工具类 & 数据存储 =====================
    private OkHttpClient okHttpClient; // 网络请求客户端
    private static final Gson gson = new Gson(); // JSON解析工具
    private final Handler mainHandler = new Handler(Looper.getMainLooper()); // 主线程处理器
    private final List<RideRecord> rideRecordList = new ArrayList<>(); // 骑行记录列表
    private final List<DeviceData> ksDataList = new ArrayList<>(); // 开始(KS)数据列表
    private final List<DeviceData> jsDataList = new ArrayList<>(); // 结束(JS)数据列表
    private final Map<String, RideTimeResult> rideResultMap = new HashMap<>(); // 骑行时间匹配结果
    private final Map<String, List<ZHData>> zhDataMap = new HashMap<>(); // 定位(ZH)数据列表
    private final Map<String, RideMetrics> rideMetricsMap = new HashMap<>(); // 骑行指标数据
    private int totalNeedQueryZH = 0; // 需查询ZH数据的记录总数
    private int currentQueryZHCount = 0; // 已完成ZH查询的记录数

    // ===================== 时间格式化工具 =====================
    private static final SimpleDateFormat fullSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA); // 完整时间格式
    private final SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA); // 日期格式
    private final SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm:ss", Locale.CHINA); // 时间格式

    // ===================== 数据模型 =====================
    private static class DeviceData {
        String suffix;
        String time;
        public DeviceData(String suffix, String time) {
            this.suffix = suffix;
            this.time = time;
        }
    }

    private static class RideTimeResult {
        String date;
        String startTime;
        String endTime;
        String duration;
        String fullStart;
        String fullEnd;
        long totalSeconds;

        public RideTimeResult(String date, String startTime, String endTime, String duration, String fullStart, String fullEnd) {
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.duration = duration;
            this.fullStart = fullStart;
            this.fullEnd = fullEnd;
            try {
                Date start = fullSdf.parse(fullStart);
                Date end = fullSdf.parse(fullEnd);
                this.totalSeconds = (end.getTime() - start.getTime()) / 1000;
            } catch (ParseException e) {
                this.totalSeconds = 0;
            }
        }
    }

    private static class ZHData implements Parcelable {
        String time;
        String value;
        Double lat;
        Double lon;
        Double alt;
        Double speed;

        public ZHData(String time, String value) {
            this.time = time;
            this.value = value;
            parseZHValue();
        }

        private void parseZHValue() {
            try {
                String valClean = this.value.strip().replace("'", "\"");
                JsonObject jsonObject = gson.fromJson(valClean, JsonObject.class);
                this.lat = jsonObject.has("lat") ? jsonObject.get("lat").getAsDouble() : null;
                this.lon = jsonObject.has("lon") ? jsonObject.get("lon").getAsDouble() : null;
                this.alt = jsonObject.has("alt") ? jsonObject.get("alt").getAsDouble() : null;
                this.speed = jsonObject.has("speed") ? jsonObject.get("speed").getAsDouble() : null;

                if (this.alt != null && this.lat != null && this.alt.equals(this.lat)) this.alt = null;
                if (this.lat != null && (this.lat < -90 || this.lat > 90)) this.lat = null;
                if (this.lon != null && (this.lon < -180 || this.lon > 180)) this.lon = null;
            } catch (Exception e) {
                this.lat = null; this.lon = null; this.alt = null; this.speed = null;
            }
        }

        protected ZHData(Parcel in) {
            time = in.readString();
            value = in.readString();
            lat = in.readDouble();
            lon = in.readDouble();
            alt = in.readDouble();
            speed = in.readDouble();
        }

        public static final Creator<ZHData> CREATOR = new Creator<ZHData>() {
            @Override
            public ZHData createFromParcel(Parcel in) { return new ZHData(in); }
            @Override
            public ZHData[] newArray(int size) { return new ZHData[size]; }
        };

        @Override
        public int describeContents() { return 0; }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(time);
            dest.writeString(value);
            dest.writeDouble(lat != null ? lat : 0);
            dest.writeDouble(lon != null ? lon : 0);
            dest.writeDouble(alt != null ? alt : 0);
            dest.writeDouble(speed != null ? speed : 0);
        }

        public boolean isValidGeoData() { return lat != null && lon != null && alt != null; }
    }

    private static class RideMetrics {
        double totalDistance;
        double avgSpeed;
        double totalClimb;
        double totalDescent;

        public RideMetrics(double totalDistance, double avgSpeed, double totalClimb, double totalDescent) {
            this.totalDistance = totalDistance;
            this.avgSpeed = avgSpeed;
            this.totalClimb = totalClimb;
            this.totalDescent = totalDescent;
        }
    }

    private static class RideRecord {
        String suffix;
        RideTimeResult timeResult;
        int zhCount;
        List<ZHData> zhDataList;
        RideMetrics metrics;

        public RideRecord(String suffix, RideTimeResult timeResult, int zhCount, List<ZHData> zhDataList, RideMetrics metrics) {
            this.suffix = suffix;
            this.timeResult = timeResult;
            this.zhCount = zhCount;
            this.zhDataList = zhDataList;
            this.metrics = metrics;
        }
    }

    // ===================== 列表适配器 =====================
    private static class RideRecordAdapter extends RecyclerView.Adapter<RideRecordAdapter.ViewHolder> {
        private final List<RideRecord> recordList;

        public RideRecordAdapter(List<RideRecord> recordList) {
            this.recordList = recordList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ride_record, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RideRecord record = recordList.get(position);
            RideTimeResult rt = record.timeResult;

            holder.tvSuffix.setText("ID: " + record.suffix);
            holder.tvDate.setText("🗓️ " + rt.date);
            holder.tvTimeRange.setText("时段：" + rt.startTime + " - " + rt.endTime);
            holder.tvDuration.setText(rt.duration);

            String zhText = record.zhCount > 0 ? record.zhCount + " 个" : "加载中...";
            holder.tvZhCount.setText(zhText);

            if (record.metrics != null) {
                String summary = String.format(Locale.CHINA, "距离：%.2f km | 均速：%.2f km/h", record.metrics.totalDistance, record.metrics.avgSpeed);
                holder.tvMetricsSummary.setText(summary);
                holder.tvMetricsSummary.setVisibility(View.VISIBLE);
                if (record.zhCount > 0) holder.tvZhCount.setText(record.zhCount + " 个");
            } else {
                holder.tvMetricsSummary.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                RideRecordActivity activity = (RideRecordActivity) holder.itemView.getContext();
                if (record.metrics == null) activity.calculateRideMetrics(record.suffix);
                activity.jumpToDetailPage(record);
            });
        }

        @Override
        public int getItemCount() { return recordList.size(); }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSuffix, tvDate, tvTimeRange, tvDuration, tvZhCount, tvMetricsSummary;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvSuffix = itemView.findViewById(R.id.tv_suffix);
                tvDate = itemView.findViewById(R.id.tv_date);
                tvTimeRange = itemView.findViewById(R.id.tv_time_range);
                tvDuration = itemView.findViewById(R.id.tv_duration);
                tvZhCount = itemView.findViewById(R.id.tv_zh_count);
                tvMetricsSummary = itemView.findViewById(R.id.tv_metrics_summary);
            }
        }

        public void refresh(List<RideRecord> newData) {
            recordList.clear();
            recordList.addAll(newData);
            notifyDataSetChanged();
        }

        public void updateZhCount(int position, int zhCount, List<ZHData> zhDataList, RideMetrics metrics) {
            RideRecord record = recordList.get(position);
            RideRecord newRecord = new RideRecord(record.suffix, record.timeResult, zhCount, zhDataList, metrics);
            recordList.set(position, newRecord);
            notifyItemChanged(position);
        }
    }

    // ===================== 生命周期方法 =====================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ride_record);

        if (checkPermissions()) {
            initView();
            startQueryData();
        } else {
            Toast.makeText(this, "需要网络权限来加载骑行数据", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            List<String> deniedPermissions = new ArrayList<>();
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) deniedPermissions.add(permissions[i]);
            }

            if (deniedPermissions.isEmpty()) {
                initView();
                startQueryData();
            } else {
                boolean shouldShowRationale = false;
                for (String perm : deniedPermissions) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                        shouldShowRationale = true;
                        break;
                    }
                }
                if (shouldShowRationale) {
                    Toast.makeText(this, "网络权限是加载数据的必要权限，请授予", Toast.LENGTH_SHORT).show();
                    ActivityCompat.requestPermissions(this, deniedPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("权限被拒绝")
                            .setMessage("需要网络权限才能加载骑行数据，请前往设置开启")
                            .setPositiveButton("去设置", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            })
                            .setNegativeButton("取消", (dialog, which) -> finish())
                            .setCancelable(false)
                            .show();
                }
            }
        }
    }

    private void initView() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        loadingContainer = findViewById(R.id.loading_container);
        progressBar = findViewById(R.id.progress_bar);
        rvRideRecords = findViewById(R.id.rv_ride_records);
        tvEmpty = findViewById(R.id.tv_empty);
        tvError = findViewById(R.id.tv_error);

        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        adapter = new RideRecordAdapter(rideRecordList);
        rvRideRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRideRecords.setAdapter(adapter);

        loadingContainer.setVisibility(View.VISIBLE);
        rvRideRecords.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
    }

    // ===================== 核心逻辑：数据统计保存 =====================

    /**
     * 保存统计数据到 SharedPreferences
     * 每次匹配完成或计算完一条轨迹的里程后调用
     */
    private void saveRideStatistics() {
        double totalDistance = 0.0;
        long totalSeconds = 0;
        int totalCount = rideRecordList.size();

        for (RideRecord record : rideRecordList) {
            // 累加时长
            if (record.timeResult != null) {
                totalSeconds += record.timeResult.totalSeconds;
            }
            // 累加距离（优先使用已计算的指标，否则尝试从缓存获取）
            if (record.metrics != null) {
                totalDistance += record.metrics.totalDistance;
            } else {
                RideMetrics cachedMetrics = rideMetricsMap.get(record.suffix);
                if (cachedMetrics != null) {
                    totalDistance += cachedMetrics.totalDistance;
                }
            }
        }

        // 写入本地存储
        SharedPreferences sp = getSharedPreferences("RideStatistics", MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("total_distance", String.format(Locale.CHINA, "%.1f", totalDistance));
        editor.putString("total_time", String.format(Locale.CHINA, "%.1f", totalSeconds / 3600.0)); // 存为小时
        editor.putInt("total_count", totalCount);
        editor.apply();

        Log.d("RideRecord", "统计更新：距离=" + totalDistance + ", 时长=" + (totalSeconds / 3600.0) + "h, 次数=" + totalCount);
    }

    private void startQueryData() {
        try {
            Date startDate = fullSdf.parse(START_DATE_STR);
            Date endDate = new Date();
            querySegmentedData("ks", startDate, endDate, ksDataList, () -> {
                querySegmentedData("js", startDate, endDate, jsDataList, () -> {
                    matchRideRecords();
                });
            });
        } catch (ParseException e) {
            Log.e("RideRecord", "时间解析失败：" + e.getMessage());
            mainHandler.post(() -> {
                loadingContainer.setVisibility(View.GONE);
                tvError.setVisibility(View.VISIBLE);
                Toast.makeText(this, "时间初始化失败", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void querySegmentedData(String identifier, Date startDate, Date endDate, List<DeviceData> targetList, Runnable onComplete) {
        Set<String> seenSuffix = new HashSet<>();
        Date currentStart = startDate;
        processSegment(identifier, currentStart, endDate, seenSuffix, targetList, onComplete);
    }

    private void processSegment(String identifier, Date currentStart, Date endDate, Set<String> seenSuffix, List<DeviceData> targetList, Runnable onComplete) {
        Date currentEnd = new Date(currentStart.getTime() + SEGMENT_DAYS * 24 * 3600 * 1000 - 1000);
        if (currentEnd.after(endDate)) currentEnd = endDate;

        final Date finalCurrentEnd = currentEnd;
        final Date finalCurrentStart = currentStart;

        queryPagedData(identifier, finalCurrentStart, finalCurrentEnd, seenSuffix, targetList, () -> {
            Date nextStart = new Date(finalCurrentEnd.getTime() + 1000);
            if (nextStart.before(endDate)) {
                processSegment(identifier, nextStart, endDate, seenSuffix, targetList, onComplete);
            } else {
                mainHandler.post(onComplete);
            }
        });
    }

    private void queryPagedData(String identifier, Date segmentStart, Date segmentEnd, Set<String> seenSuffix, List<DeviceData> targetList, Runnable onPageComplete) {
        String pageStart = fullSdf.format(segmentStart);
        String segmentEndStr = fullSdf.format(segmentEnd);
        String url = buildApiUrl(identifier, pageStart, segmentEndStr);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", AUTHORIZATION_HEADER)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(onPageComplete);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseStr = response.body().string();
                    JsonObject root = gson.fromJson(responseStr, JsonObject.class);
                    JsonArray listArray = root.getAsJsonObject("data").getAsJsonArray("list");

                    for (int i = 0; i < listArray.size(); i++) {
                        JsonObject item = listArray.get(i).getAsJsonObject();
                        String value = item.get("value").getAsString().trim();
                        long timeStamp = item.get("time").getAsLong();
                        String timeStr = fullSdf.format(new Date(timeStamp));

                        java.util.regex.Matcher matcher = PATTERN.matcher(value);
                        if (matcher.find()) {
                            String suffix = matcher.group(1).toUpperCase();
                            if (!seenSuffix.contains(suffix)) {
                                seenSuffix.add(suffix);
                                targetList.add(new DeviceData(suffix, timeStr));
                            }
                        }
                    }

                    if (listArray.size() == LIMIT) {
                        JsonObject lastItem = listArray.get(listArray.size() - 1).getAsJsonObject();
                        long lastTime = lastItem.get("time").getAsLong();
                        String nextPageStart = fullSdf.format(new Date(lastTime));
                        try {
                            queryPagedData(identifier, fullSdf.parse(nextPageStart), segmentEnd, seenSuffix, targetList, onPageComplete);
                        } catch (ParseException e) {
                            mainHandler.post(onPageComplete);
                        }
                    } else {
                        mainHandler.post(onPageComplete);
                    }
                } else {
                    mainHandler.post(onPageComplete);
                }
            }
        });
    }

    private void matchRideRecords() {
        Map<String, String> ksMap = new HashMap<>();
        for (DeviceData data : ksDataList) {
            ksMap.put(data.suffix, data.time);
        }

        List<RideRecord> matchedRecords = new ArrayList<>();
        for (DeviceData jsData : jsDataList) {
            String suffix = jsData.suffix;
            if (ksMap.containsKey(suffix)) {
                String ksTime = ksMap.get(suffix);
                String jsTime = jsData.time;
                RideTimeResult timeResult = calculateRideTime(ksTime, jsTime);
                rideResultMap.put(suffix, timeResult);
                matchedRecords.add(new RideRecord(suffix, timeResult, 0, new ArrayList<>(), null));
            }
        }

        Collections.sort(matchedRecords, (o1, o2) -> {
            try {
                Date d1 = fullSdf.parse(o1.timeResult.fullStart);
                Date d2 = fullSdf.parse(o2.timeResult.fullStart);
                return d2.compareTo(d1);
            } catch (ParseException e) {
                return 0;
            }
        });

        mainHandler.post(() -> {
            loadingContainer.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(matchedRecords.isEmpty() ? View.VISIBLE : View.GONE);
            rvRideRecords.setVisibility(matchedRecords.isEmpty() ? View.GONE : View.VISIBLE);
            adapter.refresh(matchedRecords);

            // 【新增】列表匹配完成，先保存一次基础数据（次数和时长）
            saveRideStatistics();

            if (!matchedRecords.isEmpty()) {
                Toast.makeText(this, "匹配到" + matchedRecords.size() + "条骑行记录，正在自动加载数据点...", Toast.LENGTH_LONG).show();
            } else {
                loadingContainer.setVisibility(View.GONE);
            }
        });

        rideRecordList.clear();
        rideRecordList.addAll(matchedRecords);

        totalNeedQueryZH = rideRecordList.size();
        currentQueryZHCount = 0;

        if (totalNeedQueryZH == 0) return;

        for (int i = 0; i < rideRecordList.size(); i++) {
            int finalI = i;
            RideRecord record = rideRecordList.get(i);
            String suffix = record.suffix;
            RideTimeResult timeResult = record.timeResult;

            queryZHData(suffix, timeResult.fullStart, timeResult.fullEnd, () -> {
                calculateRideMetrics(suffix);

                // 【新增】每次计算完一条记录的里程，更新一次总统计
                saveRideStatistics();

                List<ZHData> zhDataList = zhDataMap.get(suffix);
                RideMetrics metrics = rideMetricsMap.get(suffix);
                int zhCount = zhDataList != null ? zhDataList.size() : 0;

                mainHandler.post(() -> {
                    adapter.updateZhCount(finalI, zhCount, zhDataList, metrics);
                    currentQueryZHCount++;
                    if (currentQueryZHCount >= totalNeedQueryZH) {
                        loadingContainer.setVisibility(View.GONE);
                        Toast.makeText(RideRecordActivity.this, "所有数据点加载完成，可直接点击查看详情", Toast.LENGTH_LONG).show();
                    }
                });
            });
        }
    }

    private void queryZHData(String suffix, String startStr, String endStr, Runnable onComplete) {
        try {
            Date startDate = fullSdf.parse(startStr);
            Date endDate = fullSdf.parse(endStr);
            List<ZHData> zhDataList = new ArrayList<>();
            querySegmentedZHData(startDate, endDate, zhDataList, () -> {
                zhDataMap.put(suffix, zhDataList);
                mainHandler.post(onComplete);
            });
        } catch (ParseException e) {
            mainHandler.post(() -> {
                onComplete.run();
                Toast.makeText(this, suffix + "数据点加载失败", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void querySegmentedZHData(Date startDate, Date endDate, List<ZHData> zhDataList, Runnable onComplete) {
        Date currentStart = startDate;
        processZHSegment(currentStart, endDate, zhDataList, onComplete);
    }

    private void processZHSegment(Date currentStart, Date endDate, List<ZHData> zhDataList, Runnable onComplete) {
        Date currentEnd = new Date(currentStart.getTime() + SEGMENT_DAYS * 24 * 3600 * 1000 - 1000);
        if (currentEnd.after(endDate)) currentEnd = endDate;

        final Date finalCurrentEnd = currentEnd;
        final Date finalCurrentStart = currentStart;

        queryPagedZHData(fullSdf.format(finalCurrentStart), fullSdf.format(finalCurrentEnd), zhDataList, () -> {
            Date nextStart = new Date(finalCurrentEnd.getTime() + 1000);
            if (nextStart.before(endDate)) {
                processZHSegment(nextStart, endDate, zhDataList, onComplete);
            } else {
                mainHandler.post(onComplete);
            }
        });
    }

    private void queryPagedZHData(String pageStart, String segmentEnd, List<ZHData> zhDataList, Runnable onPageComplete) {
        String url = buildApiUrl("zh", pageStart, segmentEnd);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", AUTHORIZATION_HEADER)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(onPageComplete);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseStr = response.body().string();
                    JsonObject root = gson.fromJson(responseStr, JsonObject.class);
                    JsonArray listArray = root.getAsJsonObject("data").getAsJsonArray("list");

                    for (int i = 0; i < listArray.size(); i++) {
                        JsonObject item = listArray.get(i).getAsJsonObject();
                        long timeStamp = item.get("time").getAsLong();
                        String timeStr = fullSdf.format(new Date(timeStamp));
                        String value = item.get("value").getAsString();
                        zhDataList.add(new ZHData(timeStr, value));
                    }

                    if (listArray.size() == LIMIT) {
                        JsonObject lastItem = listArray.get(listArray.size() - 1).getAsJsonObject();
                        long lastTime = lastItem.get("time").getAsLong();
                        String nextPageStart = fullSdf.format(new Date(lastTime));
                        queryPagedZHData(nextPageStart, segmentEnd, zhDataList, onPageComplete);
                    } else {
                        mainHandler.post(onPageComplete);
                    }
                } else {
                    mainHandler.post(onPageComplete);
                }
            }
        });
    }

    private void calculateRideMetrics(String suffix) {
        List<ZHData> zhDataList = zhDataMap.get(suffix);
        if (zhDataList == null || zhDataList.isEmpty()) {
            rideMetricsMap.put(suffix, new RideMetrics(0.0, 0.0, 0.0, 0.0));
            return;
        }

        List<ZHData> validZH = new ArrayList<>();
        for (ZHData data : zhDataList) {
            if (data.isValidGeoData()) validZH.add(data);
        }

        Collections.sort(validZH, (o1, o2) -> {
            try {
                return fullSdf.parse(o1.time).compareTo(fullSdf.parse(o2.time));
            } catch (ParseException e) {
                return 0;
            }
        });

        double totalDistance = 0.0;
        double avgSpeed = 0.0;
        double totalClimb = 0.0;
        double totalDescent = 0.0;

        if (validZH.size() >= 2) {
            for (int i = 1; i < validZH.size(); i++) {
                ZHData prev = validZH.get(i - 1);
                ZHData curr = validZH.get(i);
                totalDistance += haversineDistance(prev.lat, prev.lon, curr.lat, curr.lon);
            }

            List<Double> speedList = new ArrayList<>();
            for (ZHData data : validZH) {
                if (data.speed != null && data.speed > 0) speedList.add(data.speed);
            }
            if (!speedList.isEmpty()) {
                double sum = 0.0;
                for (double speed : speedList) sum += speed;
                avgSpeed = sum / speedList.size();
            }

            double currentAlt = validZH.get(0).alt;
            for (int i = 1; i < validZH.size(); i++) {
                double nextAlt = validZH.get(i).alt;
                double altDiff = nextAlt - currentAlt;
                if (altDiff > 1.0) totalClimb += altDiff;
                else if (altDiff < -1.0) totalDescent += Math.abs(altDiff);
                currentAlt = nextAlt;
            }
        }

        RideMetrics metrics = new RideMetrics(
                Math.round((totalDistance / 1000) * 100.0) / 100.0,
                Math.round(avgSpeed * 100.0) / 100.0,
                Math.round(totalClimb * 10.0) / 10.0,
                Math.round(totalDescent * 10.0) / 10.0
        );

        rideMetricsMap.put(suffix, metrics);
    }

    private void jumpToDetailPage(RideRecord record) {
        Intent intent = new Intent(this, SportTrackAnimationActivity.class);
        intent.putExtra("RIDE_SUFFIX", record.suffix);
        intent.putExtra("RIDE_DATE", record.timeResult.date);
        intent.putExtra("TOTAL_SECONDS", record.timeResult.totalSeconds);
        String timeRange = record.timeResult.date + " " + record.timeResult.startTime + " - " + record.timeResult.endTime;
        intent.putExtra("TIME_RANGE", timeRange);

        if (record.metrics != null) {
            intent.putExtra("TOTAL_DISTANCE", record.metrics.totalDistance);
            intent.putExtra("AVG_SPEED", record.metrics.avgSpeed);
            intent.putExtra("TOTAL_CLIMB", record.metrics.totalClimb);
            intent.putExtra("TOTAL_DESCENT", record.metrics.totalDescent);
        }

        ArrayList<double[]> trackDataList = new ArrayList<>();
        if (record.zhDataList != null && !record.zhDataList.isEmpty()) {
            for (ZHData zhData : record.zhDataList) {
                try {
                    if (zhData.isValidGeoData()) {
                        trackDataList.add(new double[]{
                                zhData.lon != null ? zhData.lon : 0,
                                zhData.lat != null ? zhData.lat : 0,
                                zhData.speed != null ? zhData.speed : 0,
                                zhData.alt != null ? zhData.alt : 0
                        });
                    }
                } catch (Exception e) {
                    continue;
                }
            }
        }

        intent.putExtra("TRACK_DATA_LIST", trackDataList);

        if (trackDataList.isEmpty()) {
            Toast.makeText(this, "该骑行记录无有效GPS数据", Toast.LENGTH_SHORT).show();
            return;
        }

        startActivity(intent);
    }

    private String buildApiUrl(String identifier, String startStr, String endStr) {
        long startTime = timeToTimestamp(startStr);
        long endTime = timeToTimestamp(endStr);
        return API_URL + "?product_id=" + PRODUCT_ID + "&device_name=" + DEVICE_NAME + "&identifier=" + identifier + "&start_time=" + startTime + "&end_time=" + endTime + "&sort=" + SORT + "&limit=" + LIMIT;
    }

    private long timeToTimestamp(String timeStr) {
        try {
            return fullSdf.parse(timeStr).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    private RideTimeResult calculateRideTime(String startStr, String endStr) {
        try {
            Date start = fullSdf.parse(startStr);
            Date end = fullSdf.parse(endStr);
            long diff = end.getTime() - start.getTime();
            long seconds = diff / 1000;
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;
            String duration = String.format(Locale.CHINA, "%02d:%02d:%02d", hours, minutes, secs);
            return new RideTimeResult(dateSdf.format(start), timeSdf.format(start), timeSdf.format(end), duration, startStr, endStr);
        } catch (ParseException e) {
            return new RideTimeResult("未知", "00:00:00", "00:00:00", "00:00:00", startStr, endStr);
        }
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (okHttpClient != null) okHttpClient.dispatcher().cancelAll();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
