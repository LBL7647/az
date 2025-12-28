package com.example.zxcmb;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.navi.enums.NaviType;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.geocoder.GeocodeResult;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 导航设置页面（支持POI搜索、地图选点、GPS定位、导航模式选择）
public class NavigateActivity extends AppCompatActivity implements PoiSearch.OnPoiSearchListener, GeocodeSearch.OnGeocodeSearchListener, AdapterView.OnItemClickListener {

    // 日志标签
    private static final String TAG = "NavigateActivity";
    // 权限请求码
    private static final int REQUEST_CODE_CORE_PERMISSIONS = 1001;
    private static final int REQUEST_CODE_OPTIONAL_PERMISSIONS = 1002;

    // 地图核心控件
    private MapView mapView;
    private AMap aMap;
    // 导航模式选择控件
    private RadioGroup rgNaviMode;
    // 开始导航按钮
    private Button btnStartNavi;
    // 起终点输入框
    private EditText etStart, etEnd;
    // 搜索按钮
    private ImageView btnSearchStart, btnSearchEnd;
    // 地图选点按钮
    private ImageView btnSelectStart, btnSelectEnd;

    // 自定义地图控制按钮（定位、缩放）
    private ImageView btnMyLocation, btnZoomIn, btnZoomOut;

    // POI搜索建议弹窗相关
    private PopupWindow suggestionPopup;
    private ListView suggestionListView;
    private ArrayAdapter<String> suggestionAdapter;
    private List<String> suggestionList = new ArrayList<>();
    private Map<String, LatLonPoint> addressLatLonMap = new HashMap<>();
    // 标记当前搜索的是起点/终点
    private boolean isSearchingStart = false;

    // 地图选点标记
    private boolean isSelectingStart = false;
    private boolean isSelectingEnd = false;
    // 起终点标记点
    private Marker startMarker, endMarker;
    // 地理编码搜索工具
    private GeocodeSearch geocodeSearch;

    // 坐标存储
    private LatLonPoint startLatLon, endLatLon;
    // 实时GPS坐标
    private LatLng realTimeGpsLatLng;
    // 核心权限授予标记
    private boolean isCorePermissionGranted = false;
    // 自定义定位图标
    private BitmapDescriptor customLocationIcon;

    // 自定义起终点标记图标
    private BitmapDescriptor startIcon;
    private BitmapDescriptor endIcon;

    // 首次定位标记
    private boolean isFirstLocation = true;

    // 权限数组（按系统版本区分）
    private static final String[] CORE_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
            new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.INTERNET
            } :
            new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.INTERNET
            };

    private static final String[] OPTIONAL_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
            new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION} :
            new String[]{};

    // 逆地理编码类型标记
    private static final int TYPE_NONE = 0;
    private static final int TYPE_START = 1;
    private static final int TYPE_END = 2;
    private int currentGeocodeType = TYPE_NONE;

    // 页面创建初始化
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigate);

        // 初始化高德地图隐私政策
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        // 检查核心权限
        checkCorePermissions();
        // 初始化控件
        initViews();
        // 初始化标记图标
        initMarkerIcons();
        // 初始化地图
        initMap(savedInstanceState);
        // 初始化搜索建议弹窗
        initSuggestionPopup();
        // 初始化地图选点功能
        initMapSelect();
        // 初始化导航按钮
        initNaviButton();

        try {
            // 初始化地理编码搜索
            geocodeSearch = new GeocodeSearch(this);
            geocodeSearch.setOnGeocodeSearchListener(this);
        } catch (AMapException e) {
            throw new RuntimeException(e);
        }
    }

    // 初始化自定义标记图标
    private void initMarkerIcons() {
        try {
            // 加载起点图标
            Drawable startDrawable = ContextCompat.getDrawable(this, R.drawable.ic_marker_start);
            if (startDrawable != null) {
                startDrawable.setTintList(null);
                Bitmap startBmp = drawableToBitmap(startDrawable);
                Bitmap scaledStartBmp = Bitmap.createScaledBitmap(startBmp, 80, 80, true);
                startIcon = BitmapDescriptorFactory.fromBitmap(scaledStartBmp);
                startBmp.recycle();
            } else {
                startIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
            }

            // 加载终点图标
            Drawable endDrawable = ContextCompat.getDrawable(this, R.drawable.ic_marker_end);
            if (endDrawable != null) {
                endDrawable.setTintList(null);
                Bitmap endBmp = drawableToBitmap(endDrawable);
                Bitmap scaledEndBmp = Bitmap.createScaledBitmap(endBmp, 80, 80, true);
                endIcon = BitmapDescriptorFactory.fromBitmap(scaledEndBmp);
                endBmp.recycle();
            } else {
                endIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化自定义图标失败：" + e.getMessage());
            // 兜底默认图标
            startIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
            endIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
        }
    }

    // Drawable转Bitmap
    private Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    // 检查核心权限
    private void checkCorePermissions() {
        boolean needRequest = false;
        for (String permission : CORE_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) {
            ActivityCompat.requestPermissions(this, CORE_PERMISSIONS, REQUEST_CODE_CORE_PERMISSIONS);
        } else {
            isCorePermissionGranted = true;
            // 请求可选权限
            requestOptionalPermissions();
        }
    }

    // 请求可选权限
    private void requestOptionalPermissions() {
        if (OPTIONAL_PERMISSIONS.length == 0) return;

        boolean needRequest = false;
        for (String permission : OPTIONAL_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) {
            ActivityCompat.requestPermissions(this, OPTIONAL_PERMISSIONS, REQUEST_CODE_OPTIONAL_PERMISSIONS);
        }
    }

    // 初始化页面控件
    private void initViews() {
        // 绑定地图控件
        mapView = findViewById(R.id.map);
        if (mapView == null) {
            Toast.makeText(this, "布局加载错误：地图控件缺失", Toast.LENGTH_SHORT).show();
            return;
        }

        // 绑定导航模式选择控件
        rgNaviMode = findViewById(R.id.rg_navi_mode);
        // 绑定开始导航按钮
        btnStartNavi = findViewById(R.id.btn_start_navi);
        // 绑定起终点输入框
        etStart = findViewById(R.id.et_start);
        etEnd = findViewById(R.id.et_end);
        // 绑定搜索按钮
        btnSearchStart = findViewById(R.id.btn_search_start);
        btnSearchEnd = findViewById(R.id.btn_search_end);
        // 绑定地图选点按钮
        btnSelectStart = findViewById(R.id.btn_select_start);
        btnSelectEnd = findViewById(R.id.btn_select_end);


        // 自定义定位按钮点击事件
        if (btnMyLocation != null) {
            btnMyLocation.setOnClickListener(v -> {
                if (aMap != null && realTimeGpsLatLng != null) {
                    // 移动地图到当前定位位置
                    aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(realTimeGpsLatLng, 17));
                } else {
                    Toast.makeText(this, "正在定位中或未获取到位置", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 起点搜索按钮点击事件
        btnSearchStart.setOnClickListener(v -> {
            String keyword = etStart.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入起点关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            isSearchingStart = true;
            // 搜索POI
            searchPoi(keyword);
            // 显示搜索建议弹窗
            suggestionPopup.showAsDropDown(etStart, 0, 0, Gravity.START);
        });

        // 终点搜索按钮点击事件
        btnSearchEnd.setOnClickListener(v -> {
            String keyword = etEnd.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入终点关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            isSearchingStart = false;
            // 搜索POI
            searchPoi(keyword);
            // 显示搜索建议弹窗
            suggestionPopup.showAsDropDown(etEnd, 0, 0, Gravity.START);
        });
    }

    // 初始化搜索建议弹窗
    private void initSuggestionPopup() {
        // 创建ListView展示建议列表
        suggestionListView = new ListView(this);
        suggestionListView.setBackgroundColor(Color.WHITE);
        suggestionListView.setDividerHeight(1);
        suggestionListView.setDivider(getResources().getDrawable(android.R.drawable.divider_horizontal_textfield));

        // 设置适配器
        suggestionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, suggestionList);
        suggestionListView.setAdapter(suggestionAdapter);
        // 设置列表项点击事件
        suggestionListView.setOnItemClickListener(this);

        // 创建PopupWindow
        suggestionPopup = new PopupWindow(suggestionListView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        suggestionPopup.setFocusable(true);
        suggestionPopup.setOutsideTouchable(true);
        suggestionPopup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
    }

    // 初始化地图选点功能
    private void initMapSelect() {
        // 起点选点按钮点击事件
        btnSelectStart.setOnClickListener(v -> {
            isSelectingStart = true;
            isSelectingEnd = false;
            // 暂停定位监听
            aMap.setOnMyLocationChangeListener(null);
            Toast.makeText(this, "请在地图上点击选择起点", Toast.LENGTH_SHORT).show();
            // 设置地图点击监听
            aMap.setOnMapClickListener(mapClickListener);
        });

        // 终点选点按钮点击事件
        btnSelectEnd.setOnClickListener(v -> {
            isSelectingEnd = true;
            isSelectingStart = false;
            // 暂停定位监听
            aMap.setOnMyLocationChangeListener(null);
            Toast.makeText(this, "请在地图上点击选择终点", Toast.LENGTH_SHORT).show();
            // 设置地图点击监听
            aMap.setOnMapClickListener(mapClickListener);
        });
    }

    // 地图点击监听器（处理选点）
    private AMap.OnMapClickListener mapClickListener = new AMap.OnMapClickListener() {
        @Override
        public void onMapClick(LatLng latLng) {
            if (isSelectingStart) {
                // 保存起点坐标
                startLatLon = new LatLonPoint(latLng.latitude, latLng.longitude);
                // 更新起点标记
                updateStartMarker(latLng);
                // 逆地理编码获取地址
                reverseGeocode(latLng, true);
            } else if (isSelectingEnd) {
                // 保存终点坐标
                endLatLon = new LatLonPoint(latLng.latitude, latLng.longitude);
                // 更新终点标记
                updateEndMarker(latLng);
                // 逆地理编码获取地址
                reverseGeocode(latLng, false);
            }
            // 移除地图点击监听
            aMap.setOnMapClickListener(null);
            // 重置选点标记
            isSelectingStart = false;
            isSelectingEnd = false;
            // 恢复定位监听
            if (isCorePermissionGranted) {
                enableMyLocationWithoutMove();
            }
        }
    };

    // 逆地理编码（坐标转地址）
    private void reverseGeocode(LatLng latLng, boolean isStart) {
        LatLonPoint latLonPoint = new LatLonPoint(latLng.latitude, latLng.longitude);
        RegeocodeQuery query = new RegeocodeQuery(latLonPoint, 100, GeocodeSearch.AMAP);
        // 异步查询地址
        geocodeSearch.getFromLocationAsyn(query);
        // 标记编码类型
        currentGeocodeType = isStart ? TYPE_START : TYPE_END;
    }

    // 更新起点标记
    private void updateStartMarker(LatLng latLng) {
        // 移除旧标记
        if (startMarker != null) {
            startMarker.remove();
        }
        // 创建新标记
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title("起点")
                .snippet(String.format("纬度：%.6f，经度：%.6f", latLng.latitude, latLng.longitude))
                .icon(startIcon)
                .anchor(0.5f, 1.0f);
        startMarker = aMap.addMarker(markerOptions);
        // 移动地图到标记位置
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17), 500, null);
    }

    // 更新终点标记
    private void updateEndMarker(LatLng latLng) {
        // 移除旧标记
        if (endMarker != null) {
            endMarker.remove();
        }
        // 创建新标记
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title("终点")
                .snippet(String.format("纬度：%.6f，经度：%.6f", latLng.latitude, latLng.longitude))
                .icon(endIcon)
                .anchor(0.5f, 1.0f);
        endMarker = aMap.addMarker(markerOptions);
        // 移动地图到标记位置
        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17), 500, null);
    }

    // 初始化高德地图
    private void initMap(Bundle savedInstanceState) {
        mapView.onCreate(savedInstanceState);
        if (aMap == null) {
            aMap = mapView.getMap();
            if (aMap == null) {
                Log.e(TAG, "地图初始化失败");
                Toast.makeText(this, "地图初始化失败", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取地图UI设置
            com.amap.api.maps.UiSettings uiSettings = aMap.getUiSettings();

            // 1. 关闭原生定位按钮（使用自定义按钮）
            uiSettings.setMyLocationButtonEnabled(false);

            // 3. 开启基础手势
            uiSettings.setZoomControlsEnabled(true);
            uiSettings.setZoomGesturesEnabled(true);
            uiSettings.setScrollGesturesEnabled(true);
            uiSettings.setRotateGesturesEnabled(true);
            uiSettings.setTiltGesturesEnabled(true);
            uiSettings.setScaleControlsEnabled(true);

            // 4. 抬高 Logo，防止被底部卡片遮挡
            uiSettings.setLogoBottomMargin(300);
            aMap.getUiSettings().setZoomPosition(com.amap.api.maps.AMapOptions.ZOOM_POSITION_RIGHT_CENTER);

            // 加载自定义定位图标
            Bitmap rawBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_my_location);
            if (rawBitmap != null) {
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(rawBitmap, 80, 80, true);
                customLocationIcon = BitmapDescriptorFactory.fromBitmap(scaledBitmap);
                rawBitmap.recycle();
            } else {
                customLocationIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
            }

            // 设置定位样式
            MyLocationStyle myLocationStyle = new MyLocationStyle();
            myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER);
            myLocationStyle.strokeColor(Color.argb(0, 0, 0, 0));
            myLocationStyle.radiusFillColor(Color.argb(0, 0, 0, 0));
            myLocationStyle.strokeWidth(5.0f);
            myLocationStyle.myLocationIcon(customLocationIcon);
            aMap.setMyLocationStyle(myLocationStyle);

            // 启用定位功能
            if (isCorePermissionGranted) {
                enableMyLocation();
            }
        }
    }

    // 启用定位功能（首次定位移动地图）
    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 开启定位
            aMap.setMyLocationEnabled(true);
            // 设置定位监听
            aMap.setOnMyLocationChangeListener(location -> {
                if (location != null) {
                    // 保存实时GPS坐标
                    realTimeGpsLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    Log.d(TAG, "GPS定位成功：" + realTimeGpsLatLng.latitude + "," + realTimeGpsLatLng.longitude);
                    // 首次定位移动地图
                    if (isFirstLocation) {
                        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(realTimeGpsLatLng, 17));
                        isFirstLocation = false;
                    }
                }
            });
        }
    }

    // 启用定位功能（不移动地图）
    private void enableMyLocationWithoutMove() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 开启定位
            aMap.setMyLocationEnabled(true);
            // 设置定位监听
            aMap.setOnMyLocationChangeListener(location -> {
                if (location != null) {
                    // 保存实时GPS坐标
                    realTimeGpsLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    Log.d(TAG, "GPS定位更新：" + realTimeGpsLatLng.latitude + "," + realTimeGpsLatLng.longitude);
                }
            });
        }
    }

    // POI搜索
    private void searchPoi(String keyword) {
        try {
            // 创建搜索查询
            PoiSearch.Query query = new PoiSearch.Query(keyword, "", "");
            query.setPageSize(10);
            query.setPageNum(0);
            PoiSearch poiSearch = new PoiSearch(this, query);
            poiSearch.setOnPoiSearchListener(this);
            // 异步搜索
            poiSearch.searchPOIAsyn();
        } catch (Exception e) {
            Log.e(TAG, "POI搜索异常：" + e.getMessage());
            Toast.makeText(this, "POI搜索失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // POI搜索结果回调
    @Override
    public void onPoiSearched(PoiResult result, int code) {
        Log.d(TAG, "POI搜索结果：code=" + code);
        if (code == AMapException.CODE_AMAP_SUCCESS) {
            if (result != null && !result.getPois().isEmpty()) {
                // 清空旧数据
                suggestionList.clear();
                addressLatLonMap.clear();

                // 处理搜索结果
                for (PoiItem item : result.getPois()) {
                    String addrName = item.getTitle() + " - " + item.getSnippet();
                    LatLonPoint point = item.getLatLonPoint();
                    if (point != null) {
                        addressLatLonMap.put(addrName, point);
                        suggestionList.add(addrName);
                    }
                }

                // 更新建议列表
                suggestionAdapter.notifyDataSetChanged();

                // 显示弹窗
                if (!suggestionPopup.isShowing()) {
                    if (isSearchingStart) {
                        suggestionPopup.showAsDropDown(etStart, 0, 0, Gravity.START);
                    } else {
                        suggestionPopup.showAsDropDown(etEnd, 0, 0, Gravity.START);
                    }
                }
            } else {
                // 无搜索结果
                suggestionList.clear();
                suggestionAdapter.notifyDataSetChanged();
                suggestionPopup.dismiss();
                Toast.makeText(this, "未找到匹配的POI", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 搜索失败
            suggestionList.clear();
            suggestionAdapter.notifyDataSetChanged();
            suggestionPopup.dismiss();
            Toast.makeText(this, "POI搜索失败：错误码" + code, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "POI搜索错误：code=" + code);
        }
    }

    // POI项搜索结果回调（未使用）
    @Override
    public void onPoiItemSearched(PoiItem poiItem, int i) {}

    // 搜索建议列表项点击事件
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position >= suggestionList.size()) return;

        // 获取选中的地址
        String selectedAddr = suggestionList.get(position);
        LatLonPoint selectedPoint = addressLatLonMap.get(selectedAddr);
        if (selectedPoint == null) return;

        if (isSearchingStart) {
            // 保存起点坐标
            startLatLon = selectedPoint;
            // 更新输入框
            etStart.setText(selectedAddr);
            // 更新起点标记
            updateStartMarker(new LatLng(selectedPoint.getLatitude(), selectedPoint.getLongitude()));
            Toast.makeText(this, "起点已选：" + selectedAddr, Toast.LENGTH_SHORT).show();
        } else {
            // 保存终点坐标
            endLatLon = selectedPoint;
            // 更新输入框
            etEnd.setText(selectedAddr);
            // 更新终点标记
            updateEndMarker(new LatLng(selectedPoint.getLatitude(), selectedPoint.getLongitude()));
            Toast.makeText(this, "终点已选：" + selectedAddr, Toast.LENGTH_SHORT).show();
        }

        // 关闭建议弹窗
        suggestionPopup.dismiss();
    }

    // 逆地理编码结果回调
    @Override
    public void onRegeocodeSearched(RegeocodeResult result, int rCode) {
        if (rCode == AMapException.CODE_AMAP_SUCCESS && result != null) {
            // 获取格式化地址
            String address = result.getRegeocodeAddress().getFormatAddress();
            if (currentGeocodeType == TYPE_START) {
                // 更新起点输入框
                etStart.setText(address);
                Toast.makeText(this, "已选择起点：" + address, Toast.LENGTH_SHORT).show();
            } else if (currentGeocodeType == TYPE_END) {
                // 更新终点输入框
                etEnd.setText(address);
                Toast.makeText(this, "已选择终点：" + address, Toast.LENGTH_SHORT).show();
            }
        } else {
            // 地址解析失败，使用经纬度填充
            Toast.makeText(this, "地址解析失败，使用经纬度填充", Toast.LENGTH_SHORT).show();
            LatLng latLng = null;
            if (currentGeocodeType == TYPE_START && startMarker != null) {
                latLng = startMarker.getPosition();
                etStart.setText(String.format("%.6f, %.6f", latLng.latitude, latLng.longitude));
            } else if (currentGeocodeType == TYPE_END && endMarker != null) {
                latLng = endMarker.getPosition();
                etEnd.setText(String.format("%.6f, %.6f", latLng.latitude, latLng.longitude));
            }
        }
        // 重置编码类型
        currentGeocodeType = TYPE_NONE;
    }

    // 地理编码结果回调（未使用）
    @Override
    public void onGeocodeSearched(GeocodeResult result, int rCode) {}

    // 初始化开始导航按钮点击事件
    private void initNaviButton() {
        if (btnStartNavi != null) {
            btnStartNavi.setOnClickListener(v -> {
                // 检查输入框是否为空
                String start = etStart.getText().toString().trim();
                String end = etEnd.getText().toString().trim();
                if (start.isEmpty() || end.isEmpty()) {
                    Toast.makeText(this, "请输入起终点", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 检查坐标是否有效
                if (startLatLon == null || endLatLon == null) {
                    Toast.makeText(this, "请选择有效的起终点地址", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 获取选中的导航模式
                int naviMode = rgNaviMode.getCheckedRadioButtonId() == R.id.rb_gps ? NaviType.GPS : NaviType.EMULATOR;
                // 跳转到导航页面
                Intent intent = new Intent(NavigateActivity.this, EmulatorActivity.class);
                // 传递坐标参数
                intent.putExtra("start_lat", startLatLon.getLatitude());
                intent.putExtra("start_lng", startLatLon.getLongitude());
                intent.putExtra("end_lat", endLatLon.getLatitude());
                intent.putExtra("end_lng", endLatLon.getLongitude());
                intent.putExtra("navi_mode", naviMode);
                startActivity(intent);
            });
        }
    }

    // 权限请求结果回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "权限请求结果：requestCode=" + requestCode + ", permissions=" + Arrays.toString(permissions) + ", grantResults=" + Arrays.toString(grantResults));

        if (requestCode == REQUEST_CODE_CORE_PERMISSIONS) {
            // 检查核心权限是否全部授予
            boolean allCoreGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allCoreGranted = false;
                    break;
                }
            }
            if (allCoreGranted) {
                isCorePermissionGranted = true;
                // 启用定位
                enableMyLocation();
                Toast.makeText(this, "导航核心权限已授予，可正常使用功能", Toast.LENGTH_SHORT).show();
                // 请求可选权限
                requestOptionalPermissions();
            } else {
                isCorePermissionGranted = false;
                Toast.makeText(this, "导航核心权限（定位、网络）未授予，无法使用导航功能", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_OPTIONAL_PERMISSIONS) {
            // 检查可选权限是否全部授予
            boolean allOptionalGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allOptionalGranted = false;
                    break;
                }
            }
            if (!allOptionalGranted) {
                Toast.makeText(this, "可选权限未授予，部分功能（如后台导航）可能受限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 页面恢复
    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    // 页面暂停
    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        // 关闭建议弹窗
        if (suggestionPopup != null && suggestionPopup.isShowing()) {
            suggestionPopup.dismiss();
        }
    }

    // 保存页面状态
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    // 页面销毁
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) {
            mapView.onDestroy();
        }
        // 销毁弹窗
        if (suggestionPopup != null) {
            suggestionPopup.dismiss();
            suggestionPopup = null;
        }
        // 移除标记
        if (startMarker != null) startMarker.remove();
        if (endMarker != null) endMarker.remove();
    }
}
