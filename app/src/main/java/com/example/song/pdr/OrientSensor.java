package com.example.song.pdr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * 方向传感器
 */

public class OrientSensor implements SensorEventListener {
    private static final String TAG = "OrientSensor";

    private final SensorManager mSensorManager;
    private final OrientCallBack mOrientCallBack;

    private float[] mAccelerometerValues = new float[3];
    private float[] mMagneticValues = new float[3];
    private float[] mGyroscopeValues = new float[3];

    private int mLastDegree = 0;
    private static final int MIN_DIFF = 1;

    OrientSensor(Context context, OrientCallBack orientCallBack) {
        this.mOrientCallBack = orientCallBack;
        this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public interface OrientCallBack {
        /**
         * 方向回调
         */
        void Orient(int orient);
    }

    /**
     * 注册加速度传感器和地磁场传感器
     *
     * @return 是否支持方向功能
     */
    public Boolean registerOrient() {
        if (mSensorManager == null)
            return false;

        // 注册加速度传感器
        final Boolean isAccelerometerAvailable = mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
        // 注册地磁场传感器
        final Boolean isMagneticAvailable = mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_GAME);
        final Boolean isGyroscope = mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_GAME);

        return isAccelerometerAvailable && isMagneticAvailable && isGyroscope;
    }

    /**
     * 注销方向监听器
     */
    public void unregisterOrient() {
        if (mSensorManager == null)
            return;

        mSensorManager.unregisterListener(this);
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mAccelerometerValues = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mMagneticValues = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            mGyroscopeValues = event.values.clone();
        }

        float[] R = new float[9];
        float[] values = new float[3];
        SensorManager.getRotationMatrix(R, null, mAccelerometerValues, mMagneticValues);
        SensorManager.getOrientation(R, values);

        final int currentDegree = -(int) Math.toDegrees(values[0]);//旋转角度
        if (Math.abs(currentDegree - mLastDegree) > MIN_DIFF)
            mOrientCallBack.Orient(currentDegree);

        mLastDegree = currentDegree;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
