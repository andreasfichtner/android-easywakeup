package android_easywakeup.retterdesapok.de.easywakeup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

public class BackgroundService extends Service implements SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

    public static String SCREEN_ON_FLAG = "SCREEN_ON";

    // This flag indicates that the screen is on and there is no need to keep the sensors busy.
    // Default to false, in case we start with the screen off.
    boolean screenOn = false;

    // Wake lock
    PowerManager.WakeLock cpuLock = null;

    // Sensor handling
    private SensorManager mSensorManager;
    private Sensor proximitySensor;
    private Sensor accelerometerSensor;
    private Sensor lightSensor;

    // Some helpers to get the events right
    float maxValueProximity = 0;
    int historyLightPosition = 0;
    private double[] historyLightValues = new double[10];
    int historyAccelerometerPosition = 0;
    private double[] historyAccelerometerValues = new double[50];

    @Override
    public IBinder onBind(Intent intent) {
        screenOn = intent.getBooleanExtra(SCREEN_ON_FLAG, false);
        handleScreenStatus();

        IBinder binder = new LocalBinder();
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        screenOn = intent.getBooleanExtra(SCREEN_ON_FLAG, false);
        handleScreenStatus();

        return START_STICKY;
    }

    private void handleScreenStatus() {
        if(screenOn) {
            stopSensors();
        } else  {
            initSensors();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSensors();

        Intent restartService = new Intent(getApplicationContext(),
                this.getClass());
        restartService.setPackage(getPackageName());
        PendingIntent restartServicePI = PendingIntent.getService(
                getApplicationContext(), 1, restartService,
                PendingIntent.FLAG_ONE_SHOT);

        //Restart the service once it has been killed android
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 100, restartServicePI);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        initSensors();

        // Register for preference changes
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).registerOnSharedPreferenceChangeListener(this);

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        BroadcastHandler mReceiver = new BroadcastHandler();
        registerReceiver(mReceiver, filter);
    }

    private void initSensors() {

        stopSensors();

        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean checkProximity = defaultSharedPreferences.getBoolean("switch_proximity", false);
        boolean checkLight = defaultSharedPreferences.getBoolean("switch_ambient", false);
        boolean checkAccelerometer = defaultSharedPreferences.getBoolean("switch_accelerometer", false);
        boolean holdWakelock = defaultSharedPreferences.getBoolean("switch_wakelock", false);

        if(checkProximity) {
            proximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            registerForSensorIfExists(proximitySensor);
        }

        if(checkLight) {
            lightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            registerForSensorIfExists(lightSensor);
        }

        if(checkAccelerometer) {
            accelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            registerForSensorIfExists(accelerometerSensor);
        }

        if(holdWakelock) {
            PowerManager powerManager = ((PowerManager) getSystemService(Context.POWER_SERVICE));
            cpuLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CPU");
            cpuLock.acquire();
        }
    }

    private void stopSensors() {
        if(cpuLock != null) {
            cpuLock.release();
        }

        mSensorManager.unregisterListener(this);
    }

    private void registerForSensorIfExists(Sensor sensor) {
        if(sensor != null) {
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
       if(event.sensor == accelerometerSensor) {
           // Get length of acceleration vector
           historyAccelerometerValues[historyAccelerometerPosition] = Math.pow(Math.abs(event.values[0]) * Math.abs(event.values[1]) * Math.abs(event.values[2]), 1.0 / 3);
           detectWakeupEventForAcceleration(historyAccelerometerValues);
           historyAccelerometerPosition = (historyAccelerometerPosition + 1) % historyAccelerometerValues.length;
       } else if (event.sensor == proximitySensor) {
           // Find out maximum distance so our threshold can never be lower than the maximum value
           if(event.values[0] > maxValueProximity) {
               maxValueProximity = event.values[0];
           }
           detectWakeupEventForProximity(event.values);
       } else if (event.sensor == lightSensor) {
           // Save the last couple of values to detect sudden changes
           historyLightValues[historyLightPosition] = event.values[0];
           detectWakeupEventForLight(historyLightValues);
           historyLightPosition = (historyLightPosition + 1) % historyLightValues.length;
       }
    }

    private void detectWakeupEventForProximity(float[] values) {
        if(values[0] < maxValueProximity && values[0] < 5) {
            wakeupDetected();
        }
    }

    private void detectWakeupEventForAcceleration(double[] values) {
        // First check for an absolute minumum
        if(values[historyAccelerometerPosition] > 0.15) {
            double mean = new Statistics(values).getMean();
            Log.d("Accel", String.format("mean = %f, current = %f, factor = %f", mean, values[historyAccelerometerPosition], values[historyAccelerometerPosition] / mean));
            if (values[historyAccelerometerPosition] / mean > 5) {
                wakeupDetected();
            }
        }
    }

    private void detectWakeupEventForLight(double[] values) {
        double mean = new Statistics(values).getMean();
        if(historyLightValues[historyLightPosition] < mean / 10) {
            wakeupDetected();
        }
    }

    private void wakeupDetected() {
        PowerManager powerManager = ((PowerManager) getSystemService(Context.POWER_SERVICE));
        PowerManager.WakeLock wake = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "TAG");
        wake.acquire();
        wake.release();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Ignore
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        initSensors();
    }

    public class LocalBinder extends Binder {
        BackgroundService getService() {
            return BackgroundService.this;
        }
    }
}