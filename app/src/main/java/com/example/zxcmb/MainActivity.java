package com.example.zxcmb;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.navi.enums.NaviType;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;

// 导航设置主页面（选择起点终点、导航模式）
public class MainActivity extends AppCompatActivity implements PoiSearch.OnPoiSearchListener {
    // 地图控件
    private MapView mapView;
    private AMap aMap;
    // 导航模式选择控件
    private RadioGroup rgNaviMode;
    // 开始导航按钮
    private Button btnStartNavi;
    // 起点终点输入框
    private EditText etStart, etEnd;

    // 起终点坐标存储
    private LatLonPoint startLatLon, endLatLon;
    // 标记当前搜索的是起点还是终点
    private boolean isSearchingStart;
    // 设备实时GPS坐标
    private LatLng realTimeGpsLatLng;

    // 权限请求常量
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String[] NEEDED_PERMISSIONS;

    // 根据系统版本初始化所需权限
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NEEDED_PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE
            };
        } else {
            NEEDED_PERMISSIONS = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE
            };
        }
    }

    // 页面创建初始化
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置布局文件
        setContentView(R.layout.activity_main);
        // 初始化高德地图隐私政策
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);

        try {
            // 初始化控件
            initViews();
            // 初始化地图
            initMap(savedInstanceState);
            // 初始化输入框监听
            initInputListener();
            // 初始化导航按钮点击事件
            initNaviButton();
            // 检查并请求权限
            checkPermissions();
        } catch (RuntimeException e) {
            Toast.makeText(this, "初始化失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // 初始化页面控件
    private void initViews() {
        mapView = findViewById(R.id.map);
        if (mapView == null) {
            throw new RuntimeException("布局文件中缺少 map 控件");
        }
        rgNaviMode = findViewById(R.id.rg_navi_mode);
        if (rgNaviMode == null) {
            throw new RuntimeException("布局文件中缺少 rg_navi_mode 控件");
        }
        btnStartNavi = findViewById(R.id.btn_start_navi);
        if (btnStartNavi == null) {
            throw new RuntimeException("布局文件中缺少 btn_start_navi 控件");
        }
        // 绑定起点输入框
        etStart = findViewById(R.id.et_start);
        if (etStart == null) {
            throw new RuntimeException("布局文件中缺少 et_start 控件，请检查ID是否匹配");
        }
        // 绑定终点输入框
        etEnd = findViewById(R.id.et_end);
        if (etEnd == null) {
            throw new RuntimeException("布局文件中缺少 et_end 控件，请检查ID是否匹配");
        }
    }

    // 初始化高德地图
    private void initMap(Bundle savedInstanceState) {
        mapView.onCreate(savedInstanceState);
        if (aMap == null) {
            aMap = mapView.getMap();
            // 设置定位样式
            MyLocationStyle locationStyle = new MyLocationStyle();
            locationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE);
            locationStyle.strokeColor(Color.argb(180, 3, 145, 255));
            locationStyle.radiusFillColor(Color.argb(10, 0, 0, 180));
            aMap.setMyLocationStyle(locationStyle);
            aMap.getUiSettings().setMyLocationButtonEnabled(true);

            // GPS定位监听（获取实时位置）
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                aMap.setOnMyLocationChangeListener(location -> {
                    if (location != null) {
                        realTimeGpsLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        // 移动地图到当前位置
                        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(realTimeGpsLatLng, 17));
                    }
                });
            }
        }
    }

    // 初始化输入框文本变化监听
    private void initInputListener() {
        // 起点输入框监听
        etStart.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 输入字符数≥2时搜索POI
                if (s.length() >= 2) {
                    isSearchingStart = true;
                    try {
                        searchPoi(s.toString());
                    } catch (AMapException e) {
                        Toast.makeText(MainActivity.this, "POI搜索异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 终点输入框监听
        etEnd.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 输入字符数≥2时搜索POI
                if (s.length() >= 2) {
                    isSearchingStart = false;
                    try {
                        searchPoi(s.toString());
                    } catch (AMapException e) {
                        Toast.makeText(MainActivity.this, "POI搜索异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // 搜索POI地点
    private void searchPoi(String keyword) throws AMapException {
        // 创建POI搜索查询对象
        PoiSearch.Query query = new PoiSearch.Query(keyword, "", "");
        query.setPageSize(1);
        query.setPageNum(0);
        PoiSearch poiSearch = new PoiSearch(this, query);
        poiSearch.setOnPoiSearchListener(this);
        // 异步搜索POI
        poiSearch.searchPOIAsyn();
    }

    // POI搜索结果回调
    @Override
    public void onPoiSearched(PoiResult result, int code) {
        // 检查搜索结果是否成功
        if (code != AMapException.CODE_AMAP_SUCCESS || result == null || result.getPois().isEmpty()) {
            Toast.makeText(this, "未找到地点，请重新输入", Toast.LENGTH_SHORT).show();
            return;
        }
        // 获取第一个搜索结果
        PoiItem poiItem = result.getPois().get(0);
        LatLonPoint point = poiItem.getLatLonPoint();
        String name = poiItem.getTitle();
        // 区分起点/终点存储坐标
        if (isSearchingStart) {
            startLatLon = point;
            Toast.makeText(this, "POI起点已选：" + name, Toast.LENGTH_SHORT).show();
        } else {
            endLatLon = point;
            Toast.makeText(this, "终点已选：" + name, Toast.LENGTH_SHORT).show();
        }
    }

    // POI项搜索结果回调（未使用）
    @Override
    public void onPoiItemSearched(PoiItem poiItem, int i) {}

    // 初始化开始导航按钮点击事件
    private void initNaviButton() {
        btnStartNavi.setOnClickListener(v -> {
            // 获取选中的导航模式
            int naviMode = rgNaviMode.getCheckedRadioButtonId() == R.id.rb_gps
                    ? NaviType.GPS : NaviType.EMULATOR;

            // 按导航模式确定起点
            LatLonPoint finalStartLatLon = null;
            if (naviMode == NaviType.GPS) {
                // GPS导航：优先使用实时GPS位置
                if (realTimeGpsLatLng != null) {
                    finalStartLatLon = new LatLonPoint(realTimeGpsLatLng.latitude, realTimeGpsLatLng.longitude);
                    Toast.makeText(this, "使用GPS实时起点", Toast.LENGTH_SHORT).show();
                } else if (startLatLon != null) {
                    finalStartLatLon = startLatLon;
                } else {
                    Toast.makeText(this, "请先获取GPS位置或选择POI起点", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                // 模拟导航：强制使用POI输入的起点
                if (startLatLon != null) {
                    finalStartLatLon = startLatLon;
                    Toast.makeText(this, "使用POI输入起点（模拟导航不更新GPS）", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "请先在起点输入框选择POI地点", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // 检查终点是否已选择
            if (endLatLon == null) {
                Toast.makeText(this, "请先选择终点", Toast.LENGTH_SHORT).show();
                return;
            }

//            // 跳转到导航页面（注：原代码注释了跳转逻辑）
//            Intent intent = new Intent(this, EmulatorActivity.class);
//            intent.putExtra("navi_mode", naviMode);
//            intent.putExtra("start_lat", finalStartLatLon.getLatitude());
//            intent.putExtra("start_lng", finalStartLatLon.getLongitude());
//            intent.putExtra("end_lat", endLatLon.getLatitude());
//            intent.putExtra("end_lng", endLatLon.getLongitude());
//            startActivity(intent);
        });
    }

    // 检查应用所需权限
    private void checkPermissions() {
        if (lacksPermissions(NEEDED_PERMISSIONS)) {
            // 请求权限
            ActivityCompat.requestPermissions(this, NEEDED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        } else {
            // 权限已授予，启用定位
            enableLocation();
        }
    }

    // 判断是否缺少指定权限
    private boolean lacksPermissions(String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    // 权限请求结果回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 检查定位权限是否授予
            boolean hasFineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean hasCoarseLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

            if (hasFineLoc && hasCoarseLoc) {
                enableLocation();
                Toast.makeText(this, "定位权限已授予，正在获取GPS位置...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "缺少定位权限，无法使用GPS导航", Toast.LENGTH_LONG).show();
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Toast.makeText(this, "请在设置中手动开启定位权限", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    // 启用地图定位功能
    private void enableLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (aMap != null) {
                aMap.setMyLocationEnabled(true);
            }
        }
    }

    // 页面恢复时执行
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        // 检查定位权限并启用
        if (!lacksPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})) {
            enableLocation();
        }
    }

    // 页面暂停时执行
    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    // 保存页面状态
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    // 页面销毁时执行
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }
}
