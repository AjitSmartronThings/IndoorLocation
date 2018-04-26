package com.example.song.pdr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

import com.example.song.pdr.bean.AccBean;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StepSensor extends StepSensorBase {

    private static final String TAG = "StepSensor";

    // 队列容量
    private static final int CAPACITY = 60;

    // 存放加速度数据的队列
    private final BlockingQueue<AccBean> mAccQueue = new LinkedBlockingDeque<>(CAPACITY);

    private Lock mLock = new ReentrantLock();
    private Condition mNotWork;

    public StepSensor(Context context, StepCallBack stepCallBack) {
        super(context, stepCallBack);
        mNotWork = mLock.newCondition();
    }

    @Override
    protected void registerStepListener() {
        isAvailable = sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
        if (isAvailable) {
            Log.i(TAG, "加速度传感器可用！");
        } else {
            Log.i(TAG, "加速度传感器不可用！");
        }
    }

    @Override
    public void unregisterStep() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final AccBean accBean = new AccBean();
        accBean.setAcc((float) Math.sqrt(Math.pow(event.values[0], 2) + Math.pow(event.values[1], 2) + Math.pow(event.values[2], 2)));
        accBean.setTime(event.timestamp);
        try {
            mAccQueue.put(accBean);
            if (mAccQueue.remainingCapacity() != 0) {
                return;
            }
            synchronized (StepSensor.class) {
                step1();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 第一步
     * 如果队列已满，那么将队列元素赋给另一个数组，同时出队前二十个元素。
     * 并以每二十个元素一组计算峰值
     */
    private void step1() {
        final Object[] accObjectArray = mAccQueue.toArray();
        final AccBean[] accArray = new AccBean[CAPACITY];
        final AccBean[] accPeakArray = new AccBean[CAPACITY / 20];
        int midPeakIndex = 0;

        AccBean currentMax = new AccBean();
        for (int i = 0; i < CAPACITY; i++) {
            // 将加速度队列前二十个数据弹出
            if (i < 20) {
                try {
                    mAccQueue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // 每二十个元素找出其中的最大值并将其存放在accPeakArray中
            final AccBean currentAcc = (AccBean) accObjectArray[i];
            accArray[i] = currentAcc;

            if (i % 20 == 0) {
                currentMax = currentAcc;
                if (i >= 20 && i < 40) {
                    midPeakIndex = i;
                }
                continue;
            }

            if (currentMax.getAcc() < currentAcc.getAcc()) {
                currentMax = currentAcc;
                if (i >= 20 && i < 40) {
                    midPeakIndex = i;
                }
            }

            if (i % 20 == 19) {

                accPeakArray[i / 20] = currentMax;
            }
        }

        step2(accArray, accPeakArray, midPeakIndex);
    }

    /**
     * 第二步
     * 进行连续性,周期性和相似性检查
     */
    private void step2(AccBean[] accArray, AccBean[] accPeakArray, int midPeakIndex) {
        final boolean isStep3Ok = step3(beanArray2AccArray(accArray), midPeakIndex);
        final boolean isStep4Ok = step4(beanArray2TimeArray(accPeakArray));
        final boolean isStep5Ok = step5(beanArray2AccArray(accPeakArray));

        Log.e(TAG, "step3 is ok : " + isStep3Ok + " , step4 is ok : " + isStep4Ok + " , step5 is ok : " + isStep5Ok);

        if (isStep3Ok && isStep4Ok && isStep5Ok) {
            StepSensorBase.CURRENT_SETP++;
            stepCallBack.Step(StepSensorBase.CURRENT_SETP);
        }
    }

    /**
     * 第三步
     * 连续性检查
     */
    @SuppressLint("DefaultLocale")
    private boolean step3(float[] accArray, int midPeakIndex) {
        String log = "step3数据：";
        for (float a : accArray) {
            log += String.format("%.2f", a) + " ";
        }
        Log.d(TAG, log);

        final float minVar = 0.82f;

        final float firstVar = variance(Arrays.copyOfRange(accArray, midPeakIndex - 20, midPeakIndex - 10));
        final float secondVar = variance(Arrays.copyOfRange(accArray, midPeakIndex - 10, midPeakIndex));
        final float thirdVar = variance(Arrays.copyOfRange(accArray, midPeakIndex + 1, midPeakIndex + 11));
        final float forthVar = variance(Arrays.copyOfRange(accArray, midPeakIndex + 11, midPeakIndex + 21));

        Log.d(TAG, "step3: 方差值：" + String.format("%.2f", firstVar) + " , " + String.format("%.2f", secondVar) + " , " + String.format("%.2f", thirdVar) + " , " + String.format("%.2f", forthVar));

        int qualifiedNum = 0;
        if (firstVar >= minVar)
            qualifiedNum++;
        if (secondVar >= minVar)
            qualifiedNum++;
        if (thirdVar >= minVar)
            qualifiedNum++;
        if (forthVar >= minVar)
            qualifiedNum++;

        return qualifiedNum >= 2;
    }

    /**
     * 第四步
     * 周期性检查
     */
    private boolean step4(long[] timeArray) {
        String time = "time0: " + timeArray[0] + " , time1: " + timeArray[1] + " , time2: " + timeArray[2] + ",差值：" + Math.abs(timeArray[0] - timeArray[1]) + " , " + Math.abs(timeArray[0] - timeArray[1]);
        Log.e(TAG, "step4 data :  " + time);

        return (Math.abs(timeArray[1] - timeArray[2]) > 300000000 && Math.abs(timeArray[1] - timeArray[2]) < 1000000000);
    }

    /**
     * 第五步
     * 相似性检查
     */
    private boolean step5(float[] accArray) {
        float result = -Math.abs(accArray[2] - accArray[0]);
        return result > -5 && result < 0;
    }

    private static float variance(float[] x) {
        final int m = x.length;
        float sum = 0;
        for (float aX1 : x) {//求和
            sum += aX1;
        }

        final float dAve = sum / m;//求平均值
        float dVar = 0;
        for (float aX : x) {//求方差
            dVar += (aX - dAve) * (aX - dAve);
        }
        return dVar / m;
    }

    private float[] beanArray2AccArray(AccBean[] accBeanArray) {
        final float[] accArray = new float[accBeanArray.length];
        for (int i = 0; i < accBeanArray.length; i++) {
            AccBean a = accBeanArray[i];
            accArray[i] = a.getAcc();
        }

        return accArray;
    }

    private long[] beanArray2TimeArray(AccBean[] accBeanArray) {
        final long[] timeArray = new long[accBeanArray.length];
        for (int i = 0; i < accBeanArray.length; i++) {
            AccBean a = accBeanArray[i];
            timeArray[i] = a.getTime();
        }

        return timeArray;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
