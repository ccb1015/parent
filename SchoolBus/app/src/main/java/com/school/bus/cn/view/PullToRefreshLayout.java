package com.school.bus.cn.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AbsListView;
import android.widget.RelativeLayout;
import android.view.View;
import android.widget.TextView;

import com.school.bus.R;
import com.school.bus.cn.listener.OnRefreshListener;

import java.lang.reflect.Field;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by zhangxingpei on 2017/3/28.
 */

public class PullToRefreshLayout extends RelativeLayout implements View.OnTouchListener {
    public static final String TAG = "PullToRefreshLayout";
    public static final int PULL_TO_REFRESH = 0;   // 下拉刷新
    public static final int RELEASE_TO_REFRESH = 1; // 释放立即刷新
    public static final int REFRESHING = 2;    // 正在刷新
    public static final int DONE = 3;    // 刷新完毕
    private int state = PULL_TO_REFRESH;    // 当前状态
    private OnRefreshListener mListener;  // 刷新回调接口
    public static final int REFRESH_SUCCEED = 0;   // 刷新成功
    public static final int REFRESH_FAIL = 1;    // 刷新失败
    private View headView;   // 下拉头
    private View contentView;  // 内容
    private float downY, lastY;  // 按下Y坐标，上一个事件点Y坐标
    public float moveDeltaY = 0;  // 下拉的距离
    private float refreshDist = 200;// 释放刷新的距离
    private Timer timer;
    private MyTimerTask mTask;
    public float MOVE_SPEED = 8;  // 回滚速度
    private boolean isLayout = false;  // 第一次执行布局
    private boolean canPull = true;// 是否可以下拉
    private boolean isTouchInRefreshing = false;   // 在刷新过程中滑动操作
    private float radio = 2;  // 手指滑动距离与下拉头的滑动距离比，中间会随正切函数变化
    private Animation rotateAnimation;  // 下拉箭头的转180°动画
    private Animation refreshingAnimation;  // 刷新按钮均匀旋转动画
    private View pullView; // 下拉的箭头
    private View refreshingView;   // 正在刷新的图标
    private View stateImageView;  // 刷新结果图标
    private TextView stateTextView; // 刷新结果：成功或失败
    /**
     * 执行自动回滚的handler
     */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // 回弹速度随下拉距离moveDeltaY增大而增大
            MOVE_SPEED = (float) (8 + 5 * Math.tan(Math.PI / 2 / getMeasuredHeight() * moveDeltaY));
            if (state == REFRESHING && moveDeltaY <= refreshDist && !isTouchInRefreshing) {
                // 正在刷新，且没有往上推的话则悬停，显示"正在刷新..."
                moveDeltaY = refreshDist;
                mTask.cancel();
            }
            if (canPull) moveDeltaY -= MOVE_SPEED;
            if (moveDeltaY <= 0) { // 已完成回弹
                moveDeltaY = 0;
                pullView.clearAnimation();
                // 隐藏下拉头时有可能还在刷新，只有当前状态不是正在刷新时才改变状态
                if (state != REFRESHING)
                    changeState(PULL_TO_REFRESH);
                mTask.cancel();
            }
            // 刷新布局,会自动调用
            requestLayout();
        }
    };

    public PullToRefreshLayout(Context context) {
        super(context);
        initView(context);
    }

    public PullToRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public PullToRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    public void initView(Context context) {
        timer = new Timer();
        mTask = new MyTimerTask(mHandler);
        rotateAnimation = AnimationUtils.loadAnimation(context, R.anim.reverse_animation);
        refreshingAnimation = AnimationUtils.loadAnimation(context, R.anim.rotate_animation);
        //设置匀速旋转
        LinearInterpolator linearInterpolator = new LinearInterpolator();
        rotateAnimation.setInterpolator(linearInterpolator);
        refreshingAnimation.setInterpolator(linearInterpolator);
    }

    private void hideHead() {
        if (mTask != null) {
            mTask.cancel();
            mTask = null;
        }
        mTask = new MyTimerTask(mHandler);
        timer.schedule(mTask, 0, 5);
    }

    public void refreshFinish(int refreshResult) {
        refreshingView.clearAnimation();
        refreshingView.setVisibility(GONE);
        switch (refreshResult) {
            case REFRESH_SUCCEED:
                stateImageView.setVisibility(VISIBLE);
                stateImageView.setBackgroundResource(R.drawable.success);
                stateTextView.setText("刷新成功");
                break;
            case REFRESH_FAIL:
                stateImageView.setVisibility(VISIBLE);
                stateImageView.setBackgroundResource(R.drawable.fail);
                stateTextView.setText("刷新失败");
                break;
            default:
                break;
        }
        // 刷新结果停留1秒
        new Handler() {
            @Override
            public void handleMessage(Message msg) {
                state = PULL_TO_REFRESH;
                hideHead();
            }
        }.sendEmptyMessageDelayed(0, 1000);
    }

    private void changeState(int to) {
        state = to;
        switch (state) {
            case PULL_TO_REFRESH:    // 下拉刷新
                stateImageView.setVisibility(View.GONE);
                stateTextView.setText("下拉刷新");
                pullView.clearAnimation();
                pullView.setVisibility(View.VISIBLE);
                break;
            case RELEASE_TO_REFRESH:  // 释放刷新
                stateTextView.setText("释放立即刷新");
                pullView.startAnimation(rotateAnimation);
                break;
            case REFRESHING:// 正在刷新
                pullView.clearAnimation();
                refreshingView.setVisibility(View.VISIBLE);
                pullView.setVisibility(View.INVISIBLE);
                refreshingView.startAnimation(refreshingAnimation);
                stateTextView.setText("正在刷新...");
                break;
            default:
                break;
        }
    }

    public void setOnRefreshListener(OnRefreshListener onRefreshListener) {
        this.mListener = onRefreshListener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = ev.getY();
                lastY = downY;
                if (mTask != null) {
                    mTask.cancel();
                }
                if (ev.getY() < moveDeltaY) //触碰下拉的地方
                    return true;
                break;
            case MotionEvent.ACTION_MOVE:
                // canPull这个值在底下onTouch中会根据ListView是否滑到顶部来改变，意思是是否可下拉
                if (canPull) {     // 对实际滑动距离做缩小，造成用力拉的感觉
                    moveDeltaY = moveDeltaY + (ev.getY() - lastY) / radio;
                    if (moveDeltaY < 0) moveDeltaY = 0;
                    if (moveDeltaY > getMeasuredHeight()) moveDeltaY = getMeasuredHeight();
                    if (state == REFRESHING) {      // 正在刷新的时候触摸移动
                        isTouchInRefreshing = true;
                    }
                }
                lastY = ev.getY();    // 根据下拉距离改变比例
                radio = (float) (2 + 2 * Math.tan(Math.PI / 2 / getMeasuredHeight() * moveDeltaY));
                requestLayout();
                if (moveDeltaY <= refreshDist && state == RELEASE_TO_REFRESH) {
                    // 如果下拉距离没达到刷新的距离且当前状态是释放刷新，改变状态为下拉刷新
                    changeState(PULL_TO_REFRESH);
                }
                if (moveDeltaY >= refreshDist && state == PULL_TO_REFRESH) {
                    changeState(RELEASE_TO_REFRESH);
                }
                if (moveDeltaY > 8) {     // 防止下拉过程中误触发长按事件和点击事件
                    clearContentViewEvents();
                }
                if (moveDeltaY > 0) {     // 正在下拉，不让子控件捕获事件
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (moveDeltaY > refreshDist)     // 正在刷新时往下拉释放后下拉头不隐藏
                    isTouchInRefreshing = false;
                if (state == RELEASE_TO_REFRESH) {// 刷新操作
                    changeState(REFRESHING);
                    if (mListener != null) mListener.onRefresh();
                }
                hideHead();
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void clearContentViewEvents() {
        try {
            Field[] fields = AbsListView.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++)
                if (fields[i].getName().equals("mPendingCheckForLongPress")) {
                    // mPendingCheckForLongPress是AbsListView中的字段，通过反射获取并从消息列表删除，去掉长按事件
                    fields[i].setAccessible(true);
                    contentView.getHandler().removeCallbacks((Runnable) fields[i].get(contentView));
                } else if (fields[i].getName().equals("mTouchMode")) {
                    // TOUCH_MODE_REST = -1， 这个可以去除点击事件
                    fields[i].setAccessible(true);
                    fields[i].set(contentView, -1);
                }
            // 去掉焦点
            ((AbsListView) contentView).getSelector().setState(new int[]{0});
        } catch (Exception e) {
            Log.d(TAG, "error : " + e.toString());
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {//绘制阴影
        super.dispatchDraw(canvas);
        if (moveDeltaY == 0) return;
        RectF rectF = new RectF(0, 0, getMeasuredWidth(), moveDeltaY);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        // 阴影的高度为26
        LinearGradient linearGradient = new LinearGradient(0, moveDeltaY, 0, moveDeltaY - 26, 0x66000000, 0x00000000, Shader.TileMode.CLAMP);
        paint.setShader(linearGradient);
        paint.setStyle(Paint.Style.FILL);
        // 在moveDeltaY处往上变淡
        canvas.drawRect(rectF, paint);
    }

    private void initView() {
        pullView = headView.findViewById(R.id.pull_icon);
        stateTextView = (TextView) headView.findViewById(R.id.state_tv);
        refreshingView = headView.findViewById(R.id.refresh_icon);
        stateImageView = headView.findViewById(R.id.state_iv);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!isLayout) {
            // 这里是第一次进来的时候做一些初始化
            headView = getChildAt(0);
            contentView = getChildAt(1);
            // 给AbsListView设置OnTouchListener
            contentView.setOnTouchListener(this);
            isLayout = true;
            initView();
            refreshDist = ((ViewGroup) headView).getChildAt(0).getMeasuredHeight();
        }
        if (canPull) {    // 改变子控件的布局
            headView.layout(0, (int) moveDeltaY - headView.getMeasuredHeight(), headView.getMeasuredWidth(), (int) moveDeltaY);
            contentView.layout(0, (int) moveDeltaY, contentView.getMeasuredWidth(), (int) moveDeltaY + contentView.getMeasuredHeight());
        } else
            super.onLayout(changed, l, t, r, b);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // 第一个item可见且滑动到顶部
        AbsListView alv = null;
        try {
            alv = (AbsListView) v;
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
            return false;
        }
        if (alv.getCount() == 0) {   // 没有item的时候也可以下拉刷新
            canPull = true;
        } else if (alv.getFirstVisiblePosition() == 0 && alv.getChildAt(0).getTop() >= 0) {
            // 滑到AbsListView的顶部了
            canPull = true;
        } else
            canPull = false;
        return false;
    }
}

class MyTimerTask extends TimerTask {
    Handler handler;

    public MyTimerTask(Handler handler) {
        this.handler = handler;
    }

    @Override
    public void run() {
        handler.sendMessage(handler.obtainMessage());
    }
}
