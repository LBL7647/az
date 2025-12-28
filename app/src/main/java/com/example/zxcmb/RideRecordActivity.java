package com.example.zxcmb;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
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
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

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
    // 设备基础数据（KS/JS）
    private static class DeviceData {
        String suffix; // 数据后缀（唯一标识）
        String time; // 时间字符串

        public DeviceData(String suffix, String time) {
            this.suffix = suffix;
            this.time = time;
        }
    }

    // 骑行时间结果（对应Python的calc_time返回值）
    private static class RideTimeResult {
        String date; // 骑行日期
        String startTime; // 开始时间
        String endTime; // 结束时间
        String duration; // 骑行时长
        String fullStart; // 完整开始时间（含日期）
        String fullEnd; // 完整结束时间（含日期）
        long totalSeconds; // 总时长（秒）

        public RideTimeResult(String date, String startTime, String endTime, String duration, String fullStart, String fullEnd) {
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.duration = duration;
            this.fullStart = fullStart;
            this.fullEnd = fullEnd;
            // 计算总时长（秒）
            try {
                Date start = fullSdf.parse(fullStart);
                Date end = fullSdf.parse(fullEnd);
                this.totalSeconds = (end.getTime() - start.getTime()) / 1000;
            } catch (ParseException e) {
                this.totalSeconds = 0;
            }
        }
    }

    // 定位(ZH)数据模型（支持Parcelable用于Intent传递）
    private static class ZHData implements Parcelable {
        String time; // 时间
        String value; // 原始数据
        Double lat; // 纬度
        Double lon; // 经度
        Double alt; // 高度
        Double speed; // 速度

        public ZHData(String time, String value) {
            this.time = time;
            this.value = value;
            // 解析ZH数值（提取经纬度、高度、速度）
            parseZHValue();
        }

        // 解析ZH的JSON格式数值
        private void parseZHValue() {
            try {
                String valClean = this.value.strip().replace("'", "\"");
                JsonObject jsonObject = gson.fromJson(valClean, JsonObject.class);

                // 提取地理数据
                this.lat = jsonObject.has("lat") ? jsonObject.get("lat").getAsDouble() : null;
                this.lon = jsonObject.has("lon") ? jsonObject.get("lon").getAsDouble() : null;
                this.alt = jsonObject.has("alt") ? jsonObject.get("alt").getAsDouble() : null;
                this.speed = jsonObject.has("speed") ? jsonObject.get("speed").getAsDouble() : null;

                // 数据合法性校验
                if (this.alt != null && this.lat != null && this.alt.equals(this.lat)) {
                    this.alt = null;
                }
                if (this.lat != null && (this.lat < -90 || this.lat > 90)) {
                    this.lat = null;
                }
                if (this.lon != null && (this.lon < -180 || this.lon > 180)) {
                    this.lon = null;
                }
            } catch (Exception e) {
                this.lat = null;
                this.lon = null;
                this.alt = null;
                this.speed = null;
            }
        }

        // Parcelable反序列化构造器
        protected ZHData(Parcel in) {
            time = in.readString();
            value = in.readString();
            lat = in.readDouble();
            lon = in.readDouble();
            alt = in.readDouble();
            speed = in.readDouble();
        }

        // Parcelable创建器
        public static final Creator<ZHData> CREATOR = new Creator<ZHData>() {
            @Override
            public ZHData createFromParcel(Parcel in) {
                return new ZHData(in);
            }

            @Override
            public ZHData[] newArray(int size) {
                return new ZHData[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(time);
            dest.writeString(value);
            dest.writeDouble(lat != null ? lat : 0);
            dest.writeDouble(lon != null ? lon : 0);
            dest.writeDouble(alt != null ? alt : 0);
            dest.writeDouble(speed != null ? speed : 0);
        }

        // 判断是否为有效地理数据
        public boolean isValidGeoData() {
            return lat != null && lon != null && alt != null;
        }
    }

    // 骑行指标模型（总距离、平均速度、累计爬升/下降）
    private static class RideMetrics {
        double totalDistance; // 总距离（公里）
        double avgSpeed;      // 平均速度（km/h）
        double totalClimb;    // 累计爬升（米）
        double totalDescent;  // 累计下降（米）

        public RideMetrics(double totalDistance, double avgSpeed, double totalClimb, double totalDescent) {
            this.totalDistance = totalDistance;
            this.avgSpeed = avgSpeed;
            this.totalClimb = totalClimb;
            this.totalDescent = totalDescent;
        }
    }

    // 骑行记录模型（包含基础信息、数据量、指标）
    private static class RideRecord {
        String suffix; // 唯一标识
        RideTimeResult timeResult; // 时间信息
        int zhCount; // ZH数据点数量
        List<ZHData> zhDataList; // ZH数据列表
        RideMetrics metrics; // 骑行指标

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
        private final List<RideRecord> recordList; // 骑行记录数据

        public RideRecordAdapter(List<RideRecord> recordList) {
            this.recordList = recordList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 加载列表项布局
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ride_record, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RideRecord record = recordList.get(position);
            RideTimeResult rt = record.timeResult;

            // 绑定基础信息
            holder.tvSuffix.setText("ID: " + record.suffix);
            holder.tvDate.setText("🗓️ " + rt.date);
            holder.tvTimeRange.setText("时段：" + rt.startTime + " - " + rt.endTime);
            holder.tvDuration.setText(rt.duration);

            // 绑定数据点数量
            String zhText = record.zhCount > 0 ? record.zhCount + " 个" : "加载中...";
            holder.tvZhCount.setText(zhText);

            // 绑定骑行指标
            if (record.metrics != null) {
                String summary = String.format(Locale.CHINA,
                        "距离：%.2f km | 均速：%.2f km/h",
                        record.metrics.totalDistance, record.metrics.avgSpeed);
                holder.tvMetricsSummary.setText(summary);
                holder.tvMetricsSummary.setVisibility(View.VISIBLE);

                // 更新数据点数量
                if (record.zhCount > 0) {
                    holder.tvZhCount.setText(record.zhCount + " 个");
                }
            } else {
                holder.tvMetricsSummary.setVisibility(View.GONE);
            }

            // 条目点击事件（跳转到详情页）
            holder.itemView.setOnClickListener(v -> {
                RideRecordActivity activity = (RideRecordActivity) holder.itemView.getContext();
                // 确保指标已计算
                if (record.metrics == null) {
                    activity.calculateRideMetrics(record.suffix);
                }
                activity.jumpToDetailPage(record);
            });
        }

        @Override
        public int getItemCount() {
            return recordList.size();
        }

        // 列表项视图持有者
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

        // 刷新列表数据
        public void refresh(List<RideRecord> newData) {
            recordList.clear();
            recordList.addAll(newData);
            notifyDataSetChanged();
        }

        // 更新单条记录的ZH数据和指标
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

        // 检查权限
        if (checkPermissions()) {
            initView(); // 初始化视图
            startQueryData(); // 开始查询数据
        } else {
            // 提示并申请权限
            Toast.makeText(this, "需要网络权限来加载骑行数据", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 检查权限是否已授予
     */
    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 权限请求结果回调
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            List<String> deniedPermissions = new ArrayList<>();
            // 收集被拒绝的权限
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i]);
                }
            }

            if (deniedPermissions.isEmpty()) {
                // 所有权限授予成功
                initView();
                startQueryData();
            } else {
                // 判断是否有权限被"不再询问"拒绝
                boolean shouldShowRationale = false;
                for (String perm : deniedPermissions) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                        shouldShowRationale = true;
                        break;
                    }
                }

                if (shouldShowRationale) {
                    // 普通拒绝，再次申请
                    Toast.makeText(this, "网络权限是加载数据的必要权限，请授予", Toast.LENGTH_SHORT).show();
                    ActivityCompat.requestPermissions(this, deniedPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                } else {
                    // 被"不再询问"拒绝，引导到设置页
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

    /**
     * 初始化UI组件
     */
    private void initView() {
        // 设置Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 绑定UI控件
        loadingContainer = findViewById(R.id.loading_container);
        progressBar = findViewById(R.id.progress_bar);
        rvRideRecords = findViewById(R.id.rv_ride_records);
        tvEmpty = findViewById(R.id.tv_empty);
        tvError = findViewById(R.id.tv_error);

        // 初始化网络客户端
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        // 初始化列表
        adapter = new RideRecordAdapter(rideRecordList);
        rvRideRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRideRecords.setAdapter(adapter);

        // 显示加载状态
        loadingContainer.setVisibility(View.VISIBLE);
        rvRideRecords.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
    }

    /**
     * 开始查询数据（流程：查KS→查JS→匹配→查ZH→计算指标→展示）
     */
    private void startQueryData() {
        try {
            Date startDate = fullSdf.parse(START_DATE_STR);
            Date endDate = new Date();
            // 第一步：查询KS数据（分段+分页）
            querySegmentedData("ks", startDate, endDate, ksDataList, () -> {
                // 第二步：查询JS数据（分段+分页）
                querySegmentedData("js", startDate, endDate, jsDataList, () -> {
                    // 第三步：匹配KS/JS数据，生成骑行记录
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

    /**
     * 分段查询数据（7天为一段，处理分页）
     */
    private void querySegmentedData(String identifier, Date startDate, Date endDate, List<DeviceData> targetList, Runnable onComplete) {
        Set<String> seenSuffix = new HashSet<>(); // 去重集合
        Date currentStart = startDate;

        // 递归处理分段查询
        processSegment(identifier, currentStart, endDate, seenSuffix, targetList, onComplete);
    }

    /**
     * 处理单段数据查询（7天）
     */
    private void processSegment(String identifier, Date currentStart, Date endDate, Set<String> seenSuffix, List<DeviceData> targetList, Runnable onComplete) {
        // 计算当前段结束时间（7天-1秒）
        Date currentEnd = new Date(currentStart.getTime() + SEGMENT_DAYS * 24 * 3600 * 1000 - 1000);
        if (currentEnd.after(endDate)) {
            currentEnd = endDate;
        }

        Log.d("RideRecord", String.format("查询%s分段：%s - %s", identifier, fullSdf.format(currentStart), fullSdf.format(currentEnd)));

        // 最终变量（lambda使用）
        final Date finalCurrentEnd = currentEnd;
        final Date finalCurrentStart = currentStart;

        // 分页查询当前段数据
        queryPagedData(identifier, finalCurrentStart, finalCurrentEnd, seenSuffix, targetList, () -> {
            // 当前段查询完成，处理下一段
            Date nextStart = new Date(finalCurrentEnd.getTime() + 1000);
            if (nextStart.before(endDate)) {
                processSegment(identifier, nextStart, endDate, seenSuffix, targetList, onComplete);
            } else {
                // 所有分段查询完成
                Log.d("RideRecord", identifier + "查询完成，共" + targetList.size() + "条数据");
                mainHandler.post(onComplete);
            }
        });
    }

    /**
     * 分页查询单段数据
     */
    private void queryPagedData(String identifier, Date segmentStart, Date segmentEnd, Set<String> seenSuffix, List<DeviceData> targetList, Runnable onPageComplete) {
        String pageStart = fullSdf.format(segmentStart);
        String segmentEndStr = fullSdf.format(segmentEnd);
        String url = buildApiUrl(identifier, pageStart, segmentEndStr);
        Log.d("RideRecord", "分页查询URL：" + url);

        // 构建请求
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", AUTHORIZATION_HEADER)
                .build();

        // 异步请求
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("RideRecord", identifier + "分页查询失败：" + e.getMessage());
                mainHandler.post(onPageComplete);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseStr = response.body().string();
                    JsonObject root = gson.fromJson(responseStr, JsonObject.class);
                    JsonArray listArray = root.getAsJsonObject("data").getAsJsonArray("list");

                    // 解析当前页数据
                    for (int i = 0; i < listArray.size(); i++) {
                        JsonObject item = listArray.get(i).getAsJsonObject();
                        String value = item.get("value").getAsString().trim();
                        long timeStamp = item.get("time").getAsLong();
                        String timeStr = fullSdf.format(new Date(timeStamp));

                        // 匹配后缀
                        java.util.regex.Matcher matcher = PATTERN.matcher(value);
                        if (matcher.find()) {
                            String suffix = matcher.group(1).toUpperCase();
                            if (!seenSuffix.contains(suffix)) {
                                seenSuffix.add(suffix);
                                targetList.add(new DeviceData(suffix, timeStr));
                            }
                        }
                    }

                    // 判断是否需要继续分页（当前页数据量=100）
                    if (listArray.size() == LIMIT) {
                        // 获取最后一条数据时间，作为下一页起始时间
                        JsonObject lastItem = listArray.get(listArray.size() - 1).getAsJsonObject();
                        long lastTime = lastItem.get("time").getAsLong();
                        String nextPageStart = fullSdf.format(new Date(lastTime));
                        try {
                            // 继续查询下一页
                            queryPagedData(identifier, fullSdf.parse(nextPageStart), segmentEnd, seenSuffix, targetList, onPageComplete);
                        } catch (ParseException e) {
                            Log.e("RideRecord", "分页时间解析失败：" + e.getMessage());
                            mainHandler.post(onPageComplete);
                        }
                    } else {
                        // 分页查询完成
                        mainHandler.post(onPageComplete);
                    }
                } else {
                    Log.e("RideRecord", identifier + "分页响应失败：" + response.code());
                    mainHandler.post(onPageComplete);
                }
            }
        });
    }

    /**
     * 匹配KS/JS数据，生成骑行记录，并自动查询ZH数据
     */
    private void matchRideRecords() {
        // 构建KS数据映射（后缀->时间）
        Map<String, String> ksMap = new HashMap<>();
        for (DeviceData data : ksDataList) {
            ksMap.put(data.suffix, data.time);
        }

        // 匹配JS数据，生成骑行记录
        List<RideRecord> matchedRecords = new ArrayList<>();
        for (DeviceData jsData : jsDataList) {
            String suffix = jsData.suffix;
            if (ksMap.containsKey(suffix)) {
                String ksTime = ksMap.get(suffix);
                String jsTime = jsData.time;
                // 计算骑行时间
                RideTimeResult timeResult = calculateRideTime(ksTime, jsTime);
                rideResultMap.put(suffix, timeResult);
                // 初始化记录（ZH数据量0，指标null）
                matchedRecords.add(new RideRecord(suffix, timeResult, 0, new ArrayList<>(), null));
            }
        }

        // 按开始时间倒序排序
        Collections.sort(matchedRecords, (o1, o2) -> {
            try {
                Date d1 = fullSdf.parse(o1.timeResult.fullStart);
                Date d2 = fullSdf.parse(o2.timeResult.fullStart);
                return d2.compareTo(d1);
            } catch (ParseException e) {
                return 0;
            }
        });

        // 更新UI
        mainHandler.post(() -> {
            loadingContainer.setVisibility(View.VISIBLE); // 继续显示加载（ZH查询中）
            tvEmpty.setVisibility(matchedRecords.isEmpty() ? View.VISIBLE : View.GONE);
            rvRideRecords.setVisibility(matchedRecords.isEmpty() ? View.GONE : View.VISIBLE);
            adapter.refresh(matchedRecords);

            if (!matchedRecords.isEmpty()) {
                Toast.makeText(this, "匹配到" + matchedRecords.size() + "条骑行记录，正在自动加载数据点...", Toast.LENGTH_LONG).show();
            } else {
                loadingContainer.setVisibility(View.GONE);
            }
        });

        // 保存匹配结果
        rideRecordList.clear();
        rideRecordList.addAll(matchedRecords);

        // 批量查询ZH数据
        totalNeedQueryZH = rideRecordList.size();
        currentQueryZHCount = 0;

        if (totalNeedQueryZH == 0) {
            return;
        }

        // 遍历记录，逐个查询ZH数据
        for (int i = 0; i < rideRecordList.size(); i++) {
            int finalI = i;
            RideRecord record = rideRecordList.get(i);
            String suffix = record.suffix;
            RideTimeResult timeResult = record.timeResult;

            // 异步查询ZH数据
            queryZHData(suffix, timeResult.fullStart, timeResult.fullEnd, () -> {
                // 计算骑行指标
                calculateRideMetrics(suffix);
                // 获取查询结果
                List<ZHData> zhDataList = zhDataMap.get(suffix);
                RideMetrics metrics = rideMetricsMap.get(suffix);
                int zhCount = zhDataList != null ? zhDataList.size() : 0;

                // 更新列表UI
                mainHandler.post(() -> {
                    adapter.updateZhCount(finalI, zhCount, zhDataList, metrics);
                    // 计数器+1
                    currentQueryZHCount++;
                    // 所有ZH查询完成
                    if (currentQueryZHCount >= totalNeedQueryZH) {
                        loadingContainer.setVisibility(View.GONE);
                        Toast.makeText(RideRecordActivity.this, "所有数据点加载完成，可直接点击查看详情", Toast.LENGTH_LONG).show();
                    }
                });
            });
        }
    }

    /**
     * 查询ZH数据（带回调）
     */
    private void queryZHData(String suffix, String startStr, String endStr, Runnable onComplete) {
        try {
            Date startDate = fullSdf.parse(startStr);
            Date endDate = fullSdf.parse(endStr);
            List<ZHData> zhDataList = new ArrayList<>();

            // 分段查询ZH数据
            querySegmentedZHData(startDate, endDate, zhDataList, () -> {
                // 保存数据
                zhDataMap.put(suffix, zhDataList);
                // 执行回调
                mainHandler.post(onComplete);
            });
        } catch (ParseException e) {
            Log.e("RideRecord", "ZH查询时间解析失败：" + e.getMessage());
            mainHandler.post(() -> {
                onComplete.run();
                Toast.makeText(this, suffix + "数据点加载失败", Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * 分段查询ZH数据
     */
    private void querySegmentedZHData(Date startDate, Date endDate, List<ZHData> zhDataList, Runnable onComplete) {
        Date currentStart = startDate;

        // 递归处理分段
        processZHSegment(currentStart, endDate, zhDataList, onComplete);
    }

    /**
     * 处理单段ZH查询
     */
    private void processZHSegment(Date currentStart, Date endDate, List<ZHData> zhDataList, Runnable onComplete) {
        Date currentEnd = new Date(currentStart.getTime() + SEGMENT_DAYS * 24 * 3600 * 1000 - 1000);
        if (currentEnd.after(endDate)) {
            currentEnd = endDate;
        }

        // 最终变量
        final Date finalCurrentEnd = currentEnd;
        final Date finalCurrentStart = currentStart;

        // 分页查询当前段ZH数据
        queryPagedZHData(fullSdf.format(finalCurrentStart), fullSdf.format(finalCurrentEnd), zhDataList, () -> {
            Date nextStart = new Date(finalCurrentEnd.getTime() + 1000);
            if (nextStart.before(endDate)) {
                processZHSegment(nextStart, endDate, zhDataList, onComplete);
            } else {
                mainHandler.post(onComplete);
            }
        });
    }

    /**
     * 分页查询ZH数据
     */
    private void queryPagedZHData(String pageStart, String segmentEnd, List<ZHData> zhDataList, Runnable onPageComplete) {
        String url = buildApiUrl("zh", pageStart, segmentEnd);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", AUTHORIZATION_HEADER)
                .build();

        // 异步请求
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("RideRecord", "ZH分页查询失败：" + e.getMessage());
                mainHandler.post(onPageComplete);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseStr = response.body().string();
                    JsonObject root = gson.fromJson(responseStr, JsonObject.class);
                    JsonArray listArray = root.getAsJsonObject("data").getAsJsonArray("list");

                    // 解析ZH数据
                    for (int i = 0; i < listArray.size(); i++) {
                        JsonObject item = listArray.get(i).getAsJsonObject();
                        long timeStamp = item.get("time").getAsLong();
                        String timeStr = fullSdf.format(new Date(timeStamp));
                        String value = item.get("value").getAsString();
                        zhDataList.add(new ZHData(timeStr, value));
                    }

                    // 判断是否分页
                    if (listArray.size() == LIMIT) {
                        JsonObject lastItem = listArray.get(listArray.size() - 1).getAsJsonObject();
                        long lastTime = lastItem.get("time").getAsLong();
                        String nextPageStart = fullSdf.format(new Date(lastTime));
                        queryPagedZHData(nextPageStart, segmentEnd, zhDataList, onPageComplete);
                    } else {
                        mainHandler.post(onPageComplete);
                    }
                } else {
                    Log.e("RideRecord", "ZH分页响应失败：" + response.code());
                    mainHandler.post(onPageComplete);
                }
            }
        });
    }

    /**
     * 计算骑行指标（总距离、平均速度、累计爬升/下降）
     */
    private void calculateRideMetrics(String suffix) {
        List<ZHData> zhDataList = zhDataMap.get(suffix);
        if (zhDataList == null || zhDataList.isEmpty()) {
            rideMetricsMap.put(suffix, new RideMetrics(0.0, 0.0, 0.0, 0.0));
            return;
        }

        // 过滤有效地理数据
        List<ZHData> validZH = new ArrayList<>();
        for (ZHData data : zhDataList) {
            if (data.isValidGeoData()) {
                validZH.add(data);
            }
        }

        // 按时间排序
        Collections.sort(validZH, new Comparator<ZHData>() {
            @Override
            public int compare(ZHData o1, ZHData o2) {
                try {
                    Date t1 = fullSdf.parse(o1.time);
                    Date t2 = fullSdf.parse(o2.time);
                    return t1.compareTo(t2);
                } catch (ParseException e) {
                    return 0;
                }
            }
        });

        // 初始化指标
        double totalDistance = 0.0;
        double avgSpeed = 0.0;
        double totalClimb = 0.0;
        double totalDescent = 0.0;

        if (validZH.size() >= 2) {
            // 计算总距离（Haversine公式）
            for (int i = 1; i < validZH.size(); i++) {
                ZHData prev = validZH.get(i - 1);
                ZHData curr = validZH.get(i);
                totalDistance += haversineDistance(prev.lat, prev.lon, curr.lat, curr.lon);
            }

            // 计算平均速度
            List<Double> speedList = new ArrayList<>();
            for (ZHData data : validZH) {
                if (data.speed != null && data.speed > 0) {
                    speedList.add(data.speed);
                }
            }
            if (!speedList.isEmpty()) {
                double sum = 0.0;
                for (double speed : speedList) {
                    sum += speed;
                }
                avgSpeed = sum / speedList.size();
            }

            // 计算累计爬升/下降
            double currentAlt = validZH.get(0).alt;
            for (int i = 1; i < validZH.size(); i++) {
                double nextAlt = validZH.get(i).alt;
                double altDiff = nextAlt - currentAlt;

                // 仅统计大于1米的高度变化
                if (altDiff > 1.0) {
                    totalClimb += altDiff;
                } else if (altDiff < -1.0) {
                    totalDescent += Math.abs(altDiff);
                }

                currentAlt = nextAlt;
            }
        }

        // 单位转换和四舍五入
        RideMetrics metrics = new RideMetrics(
                Math.round((totalDistance / 1000) * 100.0) / 100.0, // 总距离（公里）
                Math.round(avgSpeed * 100.0) / 100.0, // 平均速度
                Math.round(totalClimb * 10.0) / 10.0, // 累计爬升
                Math.round(totalDescent * 10.0) / 10.0 // 累计下降
        );

        // 保存指标
        rideMetricsMap.put(suffix, metrics);

        // 打印日志
        Log.d("RideMetrics", String.format(Locale.CHINA,
                "[%s] 总距离：%.2fkm | 平均速度：%.2fkm/h | 爬升：%.1fm | 下降：%.1fm",
                suffix, metrics.totalDistance, metrics.avgSpeed, metrics.totalClimb, metrics.totalDescent));
    }

    /**
     * 跳转到骑行详情/轨迹动画页面
     */
    private void jumpToDetailPage(RideRecord record) {
        Intent intent = new Intent(this, SportTrackAnimationActivity.class);
        intent.putExtra("RIDE_SUFFIX", record.suffix);
        intent.putExtra("RIDE_DATE", record.timeResult.date);
        intent.putExtra("TOTAL_SECONDS", record.timeResult.totalSeconds);
        // 传递时间范围
        String timeRange = record.timeResult.date + " " + record.timeResult.startTime + " - " + record.timeResult.endTime;
        intent.putExtra("TIME_RANGE", timeRange);

        // 传递骑行指标
        if (record.metrics != null) {
            intent.putExtra("TOTAL_DISTANCE", record.metrics.totalDistance);
            intent.putExtra("AVG_SPEED", record.metrics.avgSpeed);
            intent.putExtra("TOTAL_CLIMB", record.metrics.totalClimb);
            intent.putExtra("TOTAL_DESCENT", record.metrics.totalDescent);
        }

        // 整理轨迹数据
        ArrayList<double[]> trackDataList = new ArrayList<>();
        if (record.zhDataList != null && !record.zhDataList.isEmpty()) {
            for (ZHData zhData : record.zhDataList) {
                try {
                    if (zhData.isValidGeoData()) {
                        // 4维数据：经纬度+速度+海拔
                        trackDataList.add(new double[]{
                                zhData.lon != null ? zhData.lon : 0,
                                zhData.lat != null ? zhData.lat : 0,
                                zhData.speed != null ? zhData.speed : 0,
                                zhData.alt != null ? zhData.alt : 0
                        });
                    }
                } catch (Exception e) {
                    Log.e("JumpDetail", "GPS解析失败：" + zhData.value + " | 错误：" + e.getMessage());
                    continue;
                }
            }
        }

        // 传递轨迹数据
        intent.putExtra("TRACK_DATA_LIST", trackDataList);

        // 空数据提示
        if (trackDataList.isEmpty()) {
            Toast.makeText(this, "该骑行记录无有效GPS数据", Toast.LENGTH_SHORT).show();
            return;
        }

        // 启动详情页
        startActivity(intent);
    }

    // ===================== 工具方法 =====================
    /**
     * 构建API请求URL
     */
    private String buildApiUrl(String identifier, String startStr, String endStr) {
        long startTime = timeToTimestamp(startStr);
        long endTime = timeToTimestamp(endStr);

        return API_URL + "?" +
                "product_id=" + PRODUCT_ID + "&" +
                "device_name=" + DEVICE_NAME + "&" +
                "identifier=" + identifier + "&" +
                "start_time=" + startTime + "&" +
                "end_time=" + endTime + "&" +
                "sort=" + SORT + "&" +
                "limit=" + LIMIT;
    }

    /**
     * 时间字符串转时间戳
     */
    private long timeToTimestamp(String timeStr) {
        try {
            return fullSdf.parse(timeStr).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 计算骑行时间（开始时间、结束时间、时长）
     */
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
            return new RideTimeResult(
                    dateSdf.format(start),
                    timeSdf.format(start),
                    timeSdf.format(end),
                    duration,
                    startStr,
                    endStr
            );
        } catch (ParseException e) {
            e.printStackTrace();
            return new RideTimeResult(
                    "未知",
                    "00:00:00",
                    "00:00:00",
                    "00:00:00",
                    startStr,
                    endStr
            );
        }
    }

    /**
     * Haversine公式计算两点间距离（米）
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        // 转换为弧度
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        // 计算差值
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;

        // Haversine公式
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * 页面销毁时清理资源
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消所有网络请求
        if (okHttpClient != null) {
            okHttpClient.dispatcher().cancelAll();
        }
        // 移除所有回调
        mainHandler.removeCallbacksAndMessages(null);
    }
}
