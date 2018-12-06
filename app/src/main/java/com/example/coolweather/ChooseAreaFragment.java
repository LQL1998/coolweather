package com.example.coolweather;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.example.coolweather.db.City;
import com.example.coolweather.db.County;
import com.example.coolweather.db.Province;
import com.example.coolweather.util.HttpUtil;
import com.example.coolweather.util.Utility;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Created by 本人 on 2018/11/29.
 */

public class ChooseAreaFragment extends Fragment {

    public static final int LEVEL_PROVINCE = 0; // 省级别

    public static final int LEVEL_CITY = 1; // 市级别

    public static final int LEVEL_COUNTY = 2; // 县级别

    private ProgressDialog mProgressDialog; // 请求服务器加载数据时的进度对话框

    private TextView mTitleTextView;

    private Button mBackButton;

    private ListView mListView;

    private ArrayAdapter<String> mAdapter; // ListView适配器

    private List<String> mDataList = new ArrayList<>(); // 保存省市县的名字

    private List<Province> mProvinceList; // 省列表

    private List<City> mCityList; // 市列表

    private List<County> mCountyList; // 县列表

    private Province mSelectedProvince; // 选中的省份

    private City mSelectedCity; // 选中的城市

    private int currentLevel; // 当前选中的级别

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area, container, false);
        mTitleTextView = (TextView) view.findViewById(R.id.title_text);
        mBackButton = (Button) view.findViewById(R.id.back_button);
        mListView = (ListView) view.findViewById(R.id.list_view);
        mAdapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_list_item_1, mDataList);
        mListView.setAdapter(mAdapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE) {
                    mSelectedProvince = mProvinceList.get(position);
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    mSelectedCity = mCityList.get(position);
                    queryCounties();
                }
            }
        });
        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLevel == LEVEL_COUNTY) {
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    queryProvinces();
                }
            }
        });
        queryProvinces(); // 初始化先加载省份
    }
    /**
     * 查询全国所有的省，优先从数据库查询，如果没有查询到再去服务器查询
     */
    private void queryProvinces() {
        mTitleTextView.setText("中国");
        mBackButton.setVisibility(View.GONE);
        mProvinceList = DataSupport.findAll(Province.class);
        if (mProvinceList.size() > 0) {
            mDataList.clear();
            for (Province province : mProvinceList) {
                mDataList.add(province.getProvinceName());
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        } else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address, "province");
        }
    }

    /**
     * 查询选中省内所有的市，优先从数据库查询，如果没有查询到再去服务器查询
     */
    private void queryCities() {
        mTitleTextView.setText(mSelectedProvince.getProvinceName());
        mBackButton.setVisibility(View.VISIBLE);
        mCityList = DataSupport.where("provinceid = ?",
                String.valueOf(mSelectedProvince.getId())).find(City.class);
        if (mCityList.size() > 0) {
            mDataList.clear();
            for (City city : mCityList) {
                mDataList.add(city.getCityName());
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(0);
            currentLevel = LEVEL_CITY;
        } else {
            int provinceCode = mSelectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(address, "city");
        }
    }

    /**
     * 查询选中市内所有的县，优先从数据库查询，如果没有查询到再去服务器查询
     */
    private void queryCounties() {
        mTitleTextView.setText(mSelectedCity.getCityName());
        mBackButton.setVisibility(View.VISIBLE);
        mCountyList = DataSupport.where("cityid = ?",
                String.valueOf(mSelectedCity.getId())).find(County.class);
        if (mCountyList.size() > 0) {
            mDataList.clear();
            for (County county : mCountyList) {
                mDataList.add(county.getCountyName());
            }
            mAdapter.notifyDataSetChanged();
            mListView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        } else {
            int provinceCode = mSelectedProvince.getProvinceCode();
            int cityCode = mSelectedCity.getCityCode();
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromServer(address, "county");
        }
    }

    /**
     * 根据传入的地址和类型从服务器上查询省市县数据
     *
     * @param address 地址
     * @param type    类型（省、市、县）
     */
    private void queryFromServer(String address, final String type) {
        showProgressDialog(); // 请求服务器加载数据时显示进度框
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 通过runOnUiThread()方法回到主线程处理UI
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                boolean result = false;
                if ("province".equals(type)) {
                    result = Utility.handleProvinceResponse(responseText);
                } else if ("city".equals(type)) {
                    result = Utility.handleCityResponse(responseText, mSelectedProvince.getId());
                } else if ("county".equals(type)) {
                    result = Utility.handleCountyResponse(responseText, mSelectedCity.getId());
                }

                if (result) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog(); // 数据加载完成关闭进度框
                            if ("province".equals(type)) {
                                queryProvinces();
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * 显示进度框
     */
    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(getActivity());
            mProgressDialog.setMessage("正在加载...");
            /**
             * dialog.setCancelable(false);dialog弹出后点击屏幕或物理返回键，dialog不消失
             * dialog.setCanceledOnTouchOutside(false);dialog弹出后点击屏幕，dialog不消失；点击物理返回键dialog消失
             */
            mProgressDialog.setCanceledOnTouchOutside(false);
        }
        mProgressDialog.show();
    }

    /**
     * 关闭进度框
     */
    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }
}
