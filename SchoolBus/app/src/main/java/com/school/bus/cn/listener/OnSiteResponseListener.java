package com.school.bus.cn.listener;

/**
 * Created by Administrator on 2017/4/6.
 */

public interface OnSiteResponseListener {
    public void onSuccess(String response);
    public void onFail(String message);
}
