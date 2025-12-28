package com.example.zxcmb;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MyLocationStyle;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// 首页Fragment（集成地图、天气、蓝牙状态展示）
public class HomeFragment extends Fragment {

    // 蓝牙状态展示控件
    private TextView ivBluetoothIcon;
    private TextView tvBluetoothStatus;

    // 天气展示控件
    private TextView tvTodaySymbol, tvTomorrowSymbol, tvAfterSymbol;
    private TextView tvToday, tvTodayTemp, tvTodayHumidity;
    private TextView tvTomorrowDate, tvTomorrowTemp, tvTomorrowHumidity;
    private TextView tvAfterDate, tvAfterTemp, tvAfterHumidity;

    // 物联网设备接口配置
    private static final String API_URL = "https://iot-api.heclouds.com/device/detail";
    private static final String AUTHORIZATION_HEADER = "version=2018-10-31&res=products%2F4swK0Xmr9t%2Fdevices%2Fgjcs&et=2053320694&method=md5&sign=9wdIcNP7rEj08dfUTzyVBA%3D%3D";
    private static final String PRODUCT_ID = "4swK0Xmr9t";
    private static final String DEVICE_NAME = "gjcs";

    // 天气API配置
    private static final String WEATHER_API_URL = "https://api.seniverse.com/v3/weather/now.json";
    private static final String WEATHER_DAILY_URL = "https://api.seniverse.com/v3/weather/daily.json";
    private static final String WEATHER_API_KEY = "SqmDSxg5C6eh2Ke5N";

    // 高德地图相关
    private MapView mMapView;
    private AMap aMap;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    private BitmapDescriptor customLocationIcon;

    // 网络请求和轮询相关
    private OkHttpClient okHttpClient;
    private int lastDeviceStatus = -1;
    private Handler pollingHandler;

    // 天气数据存储变量
    private double mTodayHumidity = 0.0;
    private double mTomorrowHumidity = 0.0;
    private double mAfterHumidity = 0.0;
    private double mTodayRealTemp = 0.0;
    private double mTodayHighTemp = 0.0;
    private double mTodayLowTemp = 0.0;
    private double mTomorrowHighTemp = 0.0;
    private double mTomorrowLowTemp = 0.0;
    private double mAfterHighTemp = 0.0;
    private double mAfterLowTemp = 0.0;

    // 逆地理编码防抖控制
    private long lastRegeoTime = 0;
    private static final long REGEO_INTERVAL = 30000;
    private static final String AMAP_REGEO_KEY = "8c304d19f80a2483513a301d38ca554e";

    // 创建Fragment视图
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 初始化高德地图隐私政策
        if (getActivity() != null) {
            MapsInitializer.updatePrivacyShow(getActivity(), true, true);
            MapsInitializer.updatePrivacyAgree(getActivity(), true);
        }

        // 加载布局文件
        View rootView = inflater.inflate(R.layout.activity_main, container, false);

        // 沉浸式状态栏适配
        View mainLayout = rootView.findViewById(R.id.main);
        if (mainLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // 绑定蓝牙状态控件
        ivBluetoothIcon = rootView.findViewById(R.id.iv_bluetooth_icon);
        tvBluetoothStatus = rootView.findViewById(R.id.tv_bluetooth_status);

        // 初始化天气展示控件
        initWeatherViews(rootView);

        // 初始化高德地图
        initMap(rootView, savedInstanceState);

        // 初始化功能按钮
        initFunctionButtons(rootView);

        // 初始化网络请求客户端
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        pollingHandler = new Handler(requireActivity().getMainLooper());

        // 检查并请求必要权限
        checkPermissions();

        // 首次加载设备状态和天气数据
        fetchDeviceStatusAndUpdateUI();
        fetchWeatherData();

        // 启动设备状态和天气轮询
        startDeviceStatusPolling();
        startWeatherPolling();

        return rootView;
    }

    // 初始化高德地图
    private void initMap(View rootView, Bundle savedInstanceState) {
        mMapView = rootView.findViewById(R.id.map);
        if (mMapView != null) {
            mMapView.onCreate(savedInstanceState);

            // 获取地图实例
            if (aMap == null) {
                aMap = mMapView.getMap();
                // 配置地图参数
                setupMapConfig();
            }
        }
    }

    // 配置地图显示和交互参数
    private void setupMapConfig() {
        if (aMap == null) return;

        // 地图UI交互配置
        aMap.getUiSettings().setZoomControlsEnabled(true);
        aMap.getUiSettings().setZoomGesturesEnabled(true);
        aMap.getUiSettings().setScrollGesturesEnabled(true);
        aMap.getUiSettings().setRotateGesturesEnabled(true);
        aMap.getUiSettings().setTiltGesturesEnabled(true);
        aMap.getUiSettings().setMyLocationButtonEnabled(true);
        aMap.getUiSettings().setScaleControlsEnabled(true);

        // 加载自定义定位图标
        Bitmap rawBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_my_location);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(rawBitmap, 80, 80, true);
        customLocationIcon = BitmapDescriptorFactory.fromBitmap(scaledBitmap);
        rawBitmap.recycle();

        // 设置定位蓝点样式
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER);
        myLocationStyle.strokeColor(Color.argb(0, 0, 0, 0));
        myLocationStyle.radiusFillColor(Color.argb(0, 0, 0, 0));
        myLocationStyle.strokeWidth(5.0f);
        myLocationStyle.myLocationIcon(customLocationIcon);
        aMap.setMyLocationStyle(myLocationStyle);

        // 定位监听
        if (hasLocationPermissions()) {
            aMap.setOnMyLocationChangeListener(location -> {
                if (location != null) {
                    // 移动地图到当前位置
                    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(location.getLatitude(), location.getLongitude()),
                            18));

                    // 防抖处理：30秒内只解析一次逆地理编码
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastRegeoTime > REGEO_INTERVAL) {
                        getRegeoFromAMap(location.getLatitude(), location.getLongitude());
                        lastRegeoTime = currentTime;
                    }
                }
            });
            // 启用定位功能
            enableMyLocation();
        }

        // 设置默认缩放级别
        aMap.moveCamera(CameraUpdateFactory.zoomTo(15));
    }

    // 高德地图逆地理编码（经纬度转地址）
    private void getRegeoFromAMap(double lat, double lng) {
        String regeoUrl = "https://restapi.amap.com/v3/geocode/regeo" +
                "?key=" + AMAP_REGEO_KEY +
                "&location=" + lng + "," + lat +
                "&extensions=base" +
                "&batch=false" +
                "&roadlevel=0";

        // 发起网络请求
        Request request = new Request.Builder().url(regeoUrl).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 请求失败提示
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "区县解析失败", Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() == null || !isAdded() || getActivity() == null) return;

                String responseBody = response.body().string();
                // 主线程更新UI
                getActivity().runOnUiThread(() -> {
                    try {
                        JSONObject resultObj = new JSONObject(responseBody);
                        if ("1".equals(resultObj.getString("status"))) {
                            JSONObject regeoObj = resultObj.getJSONObject("regeocode");
                            JSONObject addressComponent = regeoObj.getJSONObject("addressComponent");
                            String district = addressComponent.getString("district");
                            String province = addressComponent.getString("province");

                            // 处理空值情况
                            if (district.isEmpty() || district.equals("[]")) {
                                JSONArray cityArray = addressComponent.getJSONArray("city");
                                if (cityArray.length() > 0) {
                                    district = cityArray.getString(0);
                                } else {
                                    district = addressComponent.getString("province");
                                }
                            }
                            if (district.isEmpty()) district = "未知区域";

                            // 更新城市显示
                            TextView btnCity = getView().findViewById(R.id.btn_city);
                            if (btnCity != null) {
                                btnCity.setText(province + "\n" + district);
                            }
                        } else {
                            String info = resultObj.optString("info", "解析失败");
                            Toast.makeText(requireContext(), "区县解析失败：" + info, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(requireContext(), "区县解析异常", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // 检查并请求应用所需权限
    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        boolean needRequest = false;
        // 检查权限是否已授予
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        // 需要请求权限
        if (needRequest) {
            ActivityCompat.requestPermissions(requireActivity(), permissions, PERMISSION_REQUEST_CODE);
        } else {
            // 权限已授予，启用定位
            enableMyLocation();
        }
    }

    // 检查定位权限是否已授予
    private boolean hasLocationPermissions() {
        for (String permission : LOCATION_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // 启用地图定位功能
    private void enableMyLocation() {
        if (aMap != null && hasLocationPermissions()) {
            aMap.setMyLocationEnabled(true);
        }
    }

    // 权限请求结果回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            boolean locationDenied = false;

            // 检查权限授予结果
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    // 检查定位权限是否被拒绝
                    if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) ||
                            permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        locationDenied = true;
                    }
                }
            }

            // 处理权限结果
            if (allGranted) {
                enableMyLocation();
                Toast.makeText(requireContext(), "权限授予成功，地图定位功能已开启", Toast.LENGTH_SHORT).show();
            } else {
                if (locationDenied) {
                    Toast.makeText(requireContext(), "定位权限被拒绝，无法显示当前位置", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "部分权限被拒绝，部分功能可能受限", Toast.LENGTH_SHORT).show();
                }
                setupMapConfig();
            }
        }
    }

    // 初始化天气展示控件
    private void initWeatherViews(View rootView) {
        tvTodaySymbol = rootView.findViewById(R.id.tv_today_symbol);
        tvToday = rootView.findViewById(R.id.tv_today);
        tvTodayTemp = rootView.findViewById(R.id.tv_today_temp);
        tvTodayHumidity = rootView.findViewById(R.id.tv_today_humidity);

        tvTomorrowSymbol = rootView.findViewById(R.id.tv_tomorrow_symbol);
        tvTomorrowDate = rootView.findViewById(R.id.tv_tomorrow_date);
        tvTomorrowTemp = rootView.findViewById(R.id.tv_tomorrow_temp);
        tvTomorrowHumidity = rootView.findViewById(R.id.tv_tomorrow_humidity);

        tvAfterSymbol = rootView.findViewById(R.id.tv_after_symbol);
        tvAfterDate = rootView.findViewById(R.id.tv_after_date);
        tvAfterTemp = rootView.findViewById(R.id.tv_after_temp);
        tvAfterHumidity = rootView.findViewById(R.id.tv_after_humidity);
    }

    // 初始化功能按钮点击事件
    private void initFunctionButtons(View rootView) {
        // 蓝牙设备按钮
        rootView.findViewById(R.id.btn_bluetooth).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), BluetoothDeviceActivity.class));
        });

        // 开始导航按钮
        rootView.findViewById(R.id.btn_navigate).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), NavigateActivity.class);
            startActivity(intent);
        });

        // 骑行记录按钮
        rootView.findViewById(R.id.btn_records).setOnClickListener(v -> {
            Log.d("HomeFragment", "点击骑行记录按钮，跳转到 RideRecordActivity");
            try {
                Intent intent = new Intent(requireContext(), RideRecordActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                Log.e("HomeFragment", "跳转失败：" + e.getMessage());
                Toast.makeText(requireContext(), "跳转失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // 城市切换按钮
        rootView.findViewById(R.id.btn_city).setOnClickListener(v -> {
            Toast.makeText(requireContext(), "当前区域：" + ((TextView) v).getText(), Toast.LENGTH_SHORT).show();
        });

        // 隐藏底部导航栏
        rootView.findViewById(R.id.nav_bottom).setVisibility(View.GONE);
    }

    // 获取实时天气数据
    private void fetchWeatherData() {
        String nowUrl = WEATHER_API_URL + "?key=" + WEATHER_API_KEY +
                "&location=ip&language=zh-Hans&unit=c";

        // 发起实时天气请求
        Request nowRequest = new Request.Builder().url(nowUrl).build();
        okHttpClient.newCall(nowRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 请求失败则获取每日天气预报
                fetchDailyWeather();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() == null) {
                    fetchDailyWeather();
                    return;
                }

                String nowBody = response.body().string();

                try {
                    // 解析实时温度
                    JSONObject nowObj = new JSONObject(nowBody);
                    if (nowObj.has("results")) {
                        JSONObject resultObj = nowObj.getJSONArray("results").getJSONObject(0);
                        if (resultObj.has("now")) {
                            JSONObject nowData = resultObj.getJSONObject("now");
                            mTodayRealTemp = Double.parseDouble(nowData.getString("temperature"));
                        }
                    }
                } catch (Exception e) {
                    mTodayRealTemp = 30.0;
                }

                // 获取每日天气预报
                fetchDailyWeather();
            }
        });
    }

    // 获取3天天气预报数据
    private void fetchDailyWeather() {
        String dailyUrl = WEATHER_DAILY_URL + "?key=" + WEATHER_API_KEY +
                "&location=ip&language=zh-Hans&unit=c&start=0&days=3";

        // 发起每日天气请求
        Request dailyRequest = new Request.Builder().url(dailyUrl).build();
        okHttpClient.newCall(dailyRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 请求失败设置默认天气UI
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(HomeFragment.this::setDefaultWeatherUI);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() == null || !isAdded() || getActivity() == null) return;

                String dailyBody = response.body().string();
                // 主线程更新天气UI
                getActivity().runOnUiThread(() -> {
                    try {
                        JSONObject dailyObj = new JSONObject(dailyBody);
                        if (dailyObj.has("results")) {
                            JSONArray dailyArray = dailyObj.getJSONArray("results").getJSONObject(0).getJSONArray("daily");

                            // 解析今日天气
                            if (dailyArray.length() > 0) {
                                JSONObject todayData = dailyArray.getJSONObject(0);
                                mTodayHighTemp = Double.parseDouble(todayData.getString("high"));
                                mTodayLowTemp = Double.parseDouble(todayData.getString("low"));
                                if (todayData.has("humidity")) {
                                    mTodayHumidity = Double.parseDouble(todayData.getString("humidity"));
                                }
                                int todayCode = Integer.parseInt(todayData.getString("code_day"));
                                updateTodayWeather(todayData, todayCode);
                            }

                            // 解析明日天气
                            if (dailyArray.length() > 1) {
                                JSONObject tomorrowData = dailyArray.getJSONObject(1);
                                mTomorrowHighTemp = Double.parseDouble(tomorrowData.getString("high"));
                                mTomorrowLowTemp = Double.parseDouble(tomorrowData.getString("low"));
                                if (tomorrowData.has("humidity")) {
                                    mTomorrowHumidity = Double.parseDouble(tomorrowData.getString("humidity"));
                                }
                                int tomorrowCode = Integer.parseInt(tomorrowData.getString("code_day"));
                                updateTomorrowWeather(tomorrowData, tomorrowCode);
                            }

                            // 解析后天天气
                            if (dailyArray.length() > 2) {
                                JSONObject afterData = dailyArray.getJSONObject(2);
                                mAfterHighTemp = Double.parseDouble(afterData.getString("high"));
                                mAfterLowTemp = Double.parseDouble(afterData.getString("low"));
                                if (afterData.has("humidity")) {
                                    mAfterHumidity = Double.parseDouble(afterData.getString("humidity"));
                                }
                                int afterCode = Integer.parseInt(afterData.getString("code_day"));
                                updateAfterWeather(afterData, afterCode);
                            }
                        } else {
                            setDefaultWeatherUI();
                        }
                    } catch (Exception e) {
                        setDefaultWeatherUI();
                    }
                });
            }
        });
    }

    // 天气代码转换为对应符号
    private String getWeatherSymbol(int weatherCode) {
        switch (weatherCode) {
            case 0: return "☀️";
            case 1: return "🌙";
            case 2: return "☀️";
            case 3: return "🌙";
            case 4: return "☁️";
            case 5: return "⛅";
            case 6: return "⛅";
            case 7: return "☁️";
            case 8: return "☁️";
            case 9: return "🌫️";
            case 10: return "🌧️";
            case 11: return "⛈️";
            case 12: return "⛈️❄️";
            case 13: return "🌦️";
            case 14: return "🌧️";
            case 15: return "🌧️";
            case 16: return "🌧️";
            case 17: return "🌧️";
            case 18: return "🌧️";
            case 19: return "❄️🌧️";
            case 20: return "🌨️";
            case 21: return "❄️";
            case 22: return "❄️";
            case 23: return "❄️";
            case 24: return "❄️";
            case 25: return "❄️";
            case 26: return "💨";
            case 27: return "💨";
            case 28: return "🌪️";
            case 29: return "🌪️";
            case 30: return "🌫️";
            case 31: return "😷";
            case 32: return "💨";
            case 33: return "💨";
            case 34: return "🌀";
            case 35: return "🌀";
            case 36: return "🌪️";
            case 37: return "❄️";
            case 38: return "🔥";
            default: return "❓";
        }
    }

    // 更新今日天气UI展示
    private void updateTodayWeather(JSONObject dayData, int weatherCode) throws Exception {
        String date = dayData.getString("date");
        String displayDate = formatDate(date);
        String tempText = (int) mTodayLowTemp + "-" + (int) mTodayHighTemp + "℃";
        String humidityText = "湿度" + (int) mTodayHumidity + "%";
        String symbol = getWeatherSymbol(weatherCode);

        if (tvTodaySymbol != null) tvTodaySymbol.setText(symbol);
        if (tvToday != null) tvToday.setText(displayDate);
        if (tvTodayTemp != null) tvTodayTemp.setText(tempText);
        if (tvTodayHumidity != null) tvTodayHumidity.setText(humidityText);
    }

    // 更新明日天气UI展示
    private void updateTomorrowWeather(JSONObject dayData, int weatherCode) throws Exception {
        String date = dayData.getString("date");
        String displayDate = formatDate(date);
        String tempText = (int) mTomorrowLowTemp + "-" + (int) mTomorrowHighTemp + "℃";
        String humidityText = "湿度" + (int) mTomorrowHumidity + "%";
        String symbol = getWeatherSymbol(weatherCode);

        if (tvTomorrowSymbol != null) tvTomorrowSymbol.setText(symbol);
        if (tvTomorrowDate != null) tvTomorrowDate.setText(displayDate);
        if (tvTomorrowTemp != null) tvTomorrowTemp.setText(tempText);
        if (tvTomorrowHumidity != null) tvTomorrowHumidity.setText(humidityText);
    }

    // 更新后天天气UI展示
    private void updateAfterWeather(JSONObject dayData, int weatherCode) throws Exception {
        String date = dayData.getString("date");
        String displayDate = formatDate(date);
        String tempText = (int) mAfterLowTemp + "-" + (int) mAfterHighTemp + "℃";
        String humidityText = "湿度" + (int) mAfterHumidity + "%";
        String symbol = getWeatherSymbol(weatherCode);

        if (tvAfterSymbol != null) tvAfterSymbol.setText(symbol);
        if (tvAfterDate != null) tvAfterDate.setText(displayDate);
        if (tvAfterTemp != null) tvAfterTemp.setText(tempText);
        if (tvAfterHumidity != null) tvAfterHumidity.setText(humidityText);
    }

    // 设置默认天气UI（请求失败时）
    private void setDefaultWeatherUI() {
        // 今日默认显示
        if (tvTodaySymbol != null) tvTodaySymbol.setText("❓");
        if (tvToday != null)
            tvToday.setText("今天 " + new SimpleDateFormat("MM/dd", Locale.CHINA).format(new Date()));
        if (tvTodayTemp != null)
            tvTodayTemp.setText((int) mTodayLowTemp + "-" + (int) mTodayHighTemp + "℃");
        if (tvTodayHumidity != null)
            tvTodayHumidity.setText("湿度" + (int) mTodayHumidity + "%");

        // 明日默认显示
        if (tvTomorrowSymbol != null) tvTomorrowSymbol.setText("❓");
        if (tvTomorrowDate != null)
            tvTomorrowDate.setText("明天 " + new SimpleDateFormat("MM/dd", Locale.CHINA).format(new Date(System.currentTimeMillis() + 86400000)));
        if (tvTomorrowTemp != null)
            tvTomorrowTemp.setText((int) mTomorrowLowTemp + "-" + (int) mTomorrowHighTemp + "℃");
        if (tvTomorrowHumidity != null)
            tvTomorrowHumidity.setText("湿度" + (int) mTomorrowHumidity + "%");

        // 后天默认显示
        if (tvAfterSymbol != null) tvAfterSymbol.setText("❓");
        if (tvAfterDate != null)
            tvAfterDate.setText("后天 " + new SimpleDateFormat("MM/dd", Locale.CHINA).format(new Date(System.currentTimeMillis() + 172800000)));
        if (tvAfterTemp != null)
            tvAfterTemp.setText((int) mAfterLowTemp + "-" + (int) mAfterHighTemp + "℃");
        if (tvAfterHumidity != null)
            tvAfterHumidity.setText("湿度" + (int) mAfterHumidity + "%");
    }

    // 格式化日期显示（今天/明天/后天 + 月/日）
    private String formatDate(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            Date date = sdf.parse(dateStr);
            Date today = new Date();

            SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.CHINA);
            int targetDay = Integer.parseInt(dayFormat.format(date));
            int todayDay = Integer.parseInt(dayFormat.format(today));

            String monthDay = dateStr.substring(5).replace("-", "/");

            // 判断日期类型
            if (targetDay == todayDay) return "今天 " + monthDay;
            else if (targetDay == todayDay + 1 || (todayDay == 31 && targetDay == 1))
                return "明天 " + monthDay;
            else if (targetDay == todayDay + 2 || (todayDay == 30 && targetDay == 1) || (todayDay == 31 && targetDay == 2))
                return "后天 " + monthDay;
            else return monthDay;
        } catch (Exception e) {
            return dateStr.substring(5).replace("-", "/");
        }
    }

    // 启动设备状态轮询（5秒一次）
    private void startDeviceStatusPolling() {
        pollingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                    fetchDeviceStatusAndUpdateUI();
                    startDeviceStatusPolling();
                }
            }
        }, 5000);
    }

    // 启动天气轮询（30分钟一次）
    private void startWeatherPolling() {
        pollingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded() && getActivity() != null && !getActivity().isFinishing()) {
                    fetchWeatherData();
                    startWeatherPolling();
                }
            }
        }, 1800000);
    }

    // 获取设备状态并更新UI
    private void fetchDeviceStatusAndUpdateUI() {
        String requestUrl = API_URL + "?product_id=" + PRODUCT_ID + "&device_name=" + DEVICE_NAME;
        Request request = new Request.Builder()
                .url(requestUrl)
                .addHeader("Authorization", AUTHORIZATION_HEADER)
                .build();

        // 发起设备状态请求
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 请求失败，更新为离线状态
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        updateBluetoothUI("离线", Color.parseColor("#999999"), "🔴");
                        if (lastDeviceStatus != 0) {
                            showStatusToast("设备已离线");
                            lastDeviceStatus = 0;
                        }
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() == null || !isAdded() || getActivity() == null) return;

                String responseBody = response.body().string();
                // 主线程更新设备状态UI
                getActivity().runOnUiThread(() -> {
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        if (jsonObject.getInt("code") == 0) {
                            int statusCode = jsonObject.getJSONObject("data").getInt("status");
                            // 状态变化时显示提示
                            if (lastDeviceStatus != -1 && lastDeviceStatus != statusCode) {
                                if (statusCode == 1)
                                    showStatusToast("设备已上线");
                                else if (statusCode == 0)
                                    showStatusToast("设备已离线");
                                else if (statusCode == 2)
                                    showStatusToast("设备未激活");
                            }
                            lastDeviceStatus = statusCode;
                            // 根据状态更新UI
                            switch (statusCode) {
                                case 1:
                                    updateBluetoothUI("在线", Color.parseColor("#2196F3"), "🔵");
                                    break;
                                case 0:
                                    updateBluetoothUI("离线", Color.parseColor("#999999"), "🔴");
                                    break;
                                case 2:
                                    updateBluetoothUI("未激活", Color.parseColor("#FF9800"), "🟡");
                                    break;
                                default:
                                    updateBluetoothUI("未知", Color.parseColor("#666666"), "⚫");
                            }
                        }
                    } catch (Exception e) {
                        updateBluetoothUI("解析错误", Color.parseColor("#F44336"), "🔴");
                    }
                });
            }
        });
    }

    // 更新蓝牙状态UI展示
    private void updateBluetoothUI(String statusText, int textColor, String iconText) {
        if (tvBluetoothStatus != null) {
            tvBluetoothStatus.setText(statusText);
            tvBluetoothStatus.setTextColor(textColor);
        }
        if (ivBluetoothIcon != null) ivBluetoothIcon.setText(iconText);
    }

    // 显示设备状态提示
    private void showStatusToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    // 检查单个权限状态
    private int checkSelfPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getActivity() != null) {
            return getActivity().checkSelfPermission(permission);
        }
        return PackageManager.PERMISSION_GRANTED;
    }

    // Fragment恢复时执行
    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) {
            mMapView.onResume();
        }
    }

    // Fragment暂停时执行
    @Override
    public void onPause() {
        super.onPause();
        if (mMapView != null) {
            mMapView.onPause();
        }
    }

    // 保存Fragment状态
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mMapView != null) {
            mMapView.onSaveInstanceState(outState);
        }
    }

    // Fragment视图销毁时执行
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 销毁地图
        if (mMapView != null) {
            mMapView.onDestroy();
        }

        // 释放自定义图标资源
        if (customLocationIcon != null) {
            customLocationIcon.recycle();
            customLocationIcon = null;
        }

        // 停止所有轮询任务
        if (pollingHandler != null) {
            pollingHandler.removeCallbacksAndMessages(null);
        }

        // 取消所有网络请求
        if (okHttpClient != null) {
            okHttpClient.dispatcher().cancelAll();
        }

        // 清空控件引用
        ivBluetoothIcon = null;
        tvBluetoothStatus = null;
        tvTodaySymbol = null;
        tvToday = null;
        tvTodayTemp = null;
        tvTodayHumidity = null;
        tvTomorrowSymbol = null;
        tvTomorrowDate = null;
        tvTomorrowTemp = null;
        tvTomorrowHumidity = null;
        tvAfterSymbol = null;
        tvAfterDate = null;
        tvAfterTemp = null;
        tvAfterHumidity = null;

        // 清空地图引用
        mMapView = null;
        aMap = null;

        // 重置变量
        lastDeviceStatus = -1;
        lastRegeoTime = 0;
        mTodayHumidity = 0.0;
        mTomorrowHumidity = 0.0;
        mAfterHumidity = 0.0;
        mTodayRealTemp = 0.0;
        mTodayHighTemp = 0.0;
        mTodayLowTemp = 0.0;
        mTomorrowHighTemp = 0.0;
        mTomorrowLowTemp = 0.0;
        mAfterHighTemp = 0.0;
        mAfterLowTemp = 0.0;
    }

    // Fragment销毁时执行
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMapView != null) {
            mMapView.onDestroy();
        }
    }
}
