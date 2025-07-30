package io.github.childscreentime.core;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.github.childscreentime.R;
import io.github.childscreentime.model.Credit;
import io.github.childscreentime.model.CreditPreferences;
import io.github.childscreentime.service.ScreenLockService;
import io.github.childscreentime.service.ScreenTimeWorker;
import io.github.childscreentime.ui.activities.MainActivity;
import io.github.childscreentime.utils.Utils;

/**
 * Main application class responsible for initializing services and managing app state
 * Thread-safe implementation for multi-threaded access
 */
public class ScreenTimeApplication extends Application implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = "ScreenTimeApplication";
    private static final String WORK_TAG = "screen_time_monitoring";
    
    // Core app state - thread-safe via synchronized methods
    private boolean blocked = false;
    private boolean running = false;
    private long duration = 0;
    
    // Dependencies
    private MainActivity mainActivity;
    private SharedPreferences sharedPreferences;
    private WorkManager workManager;
    
    // Credit management
    private Credit credit;
    private long lastCreditSync;
    public Consumer<Credit> creditCallback;
    
    // Message system
    public String msg;
    public long msgMillis;
    
    // Time tracking
    private Utils.EventTracker interactiveEventTracker;
    private long lastSync;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application starting");
        
        initializeServices();
        initializePreferences();
        initializeNotifications();
        checkOverlayPermission();
        startBackgroundMonitoring();
    }
    
    private void initializeServices() {
        this.workManager = WorkManager.getInstance(this);
    }
    
    private void initializeNotifications() {
        NotificationHelper.initializeNotificationChannels(this);
    }
    
    private void initializePreferences() {
        this.sharedPreferences = getSharedPreferences(
            getString(R.string.credit_preferences_file_name), 
            Context.MODE_PRIVATE
        );
        this.sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }
    
    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Overlay permission not granted - app may not block properly");
            } else {
                Log.d(TAG, "Overlay permission granted");
            }
        }
    }
    
    public void startBackgroundMonitoring() {
        Log.d(TAG, "Starting background monitoring");
        
        // Start the foreground service for persistent blocking
        ScreenLockService.startService(this);
        
        // Create constraints for the work
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(false)
            .build();
        
        // Create the periodic work request
        PeriodicWorkRequest screenTimeWork = new PeriodicWorkRequest.Builder(
            ScreenTimeWorker.class, 
            15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build();
        
        // Enqueue the work
        workManager.enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            screenTimeWork
        );
        
        Log.d(TAG, "Background monitoring started - WorkManager enqueued with 1-minute intervals");
        Log.w(TAG, "Note: Android may enforce minimum 15-minute intervals for PeriodicWorkRequest");
        Log.i(TAG, "Primary monitoring now handled by ScreenLockService with adaptive intervals");
    }
    
    public void stopBackgroundMonitoring() {
        if (workManager != null) {
            workManager.cancelUniqueWork(WORK_TAG);
            Log.d(TAG, "Stopped background monitoring");
        }
        
        ScreenLockService.stopService(this);
    }
    
    public void exit() {
        Log.d(TAG, "Exiting application");
        
        stopBackgroundMonitoring();
        
        if (mainActivity != null) {
            mainActivity.finishAndRemoveTask();
        }
        
        setRunning(false);
        System.exit(0);
    }
    
    // Credit management
    public Credit getTodayCredit() {
        if (credit == null || isNewCreditDay()) {
            Log.d(TAG, "Credit needs refresh - forcing sync");
            forceSyncCredit();
        }
        return credit;
    }
    
    private void forceSyncCredit() {
        try {
            this.credit = getTodayCreditPreferences().get();
            this.lastCreditSync = Utils.getTodayAsMillis();
            Log.d(TAG, "Credit synced: " + (credit != null ? credit.asString() : "null"));
            
            if (creditCallback != null && credit != null) {
                try {
                    creditCallback.accept(credit);
                } catch (Exception e) {
                    Log.e(TAG, "Error in credit callback", e);
                    creditCallback = null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync credit", e);
        }
    }
    
    private boolean isNewCreditDay() {
        return lastCreditSync != Utils.getTodayAsMillis();
    }
    
    public CreditPreferences getTodayCreditPreferences() {
        return CreditPreferences.getTodayCreditPreferences(sharedPreferences, this);
    }
    
    // Activity management
    public void initMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        Log.d(TAG, "MainActivity initialized");
    }
    
    public void registerCreditCallback(Consumer<Credit> consumer) {
        this.creditCallback = consumer;
        Log.d(TAG, creditCallback != null ? "Credit callback registered" : "Credit callback cleared");
    }
    
    // Thread-safe accessors
    public synchronized boolean isBlocked() {
        return blocked;
    }
    
    public synchronized void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }
    
    public synchronized boolean isRunning() {
        return running;
    }
    
    public synchronized void setRunning(boolean running) {
        this.running = running;
    }
    
    public synchronized long getDuration() {
        return duration;
    }
    
    public synchronized void setDuration(long duration) {
        this.duration = duration;
    }
    
    public synchronized Utils.EventTracker getInteractiveEventTracker() {
        return interactiveEventTracker;
    }
    
    public synchronized void setInteractiveEventTracker(Utils.EventTracker tracker) {
        this.interactiveEventTracker = tracker;
    }
    
    public synchronized long getLastSync() {
        return lastSync;
    }
    
    public synchronized void setLastSync(long lastSync) {
        this.lastSync = lastSync;
    }
    
    // Static helper methods
    public static ScreenTimeApplication getFromContext(Context context) {
        if (context instanceof ScreenTimeApplication) {
            return (ScreenTimeApplication) context;
        }
        
        Context appContext = context.getApplicationContext();
        if (appContext instanceof ScreenTimeApplication) {
            return (ScreenTimeApplication) appContext;
        }
        
        throw new IllegalArgumentException("Context is not from ScreenTimeApplication");
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        Log.d(TAG, "SharedPreferences changed for key: " + key);
        forceSyncCredit();
    }
    
    @Override
    public void onTerminate() {
        super.onTerminate();
        
        if (sharedPreferences != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        }
        
        stopBackgroundMonitoring();
        Log.d(TAG, "Application terminated");
    }
}
