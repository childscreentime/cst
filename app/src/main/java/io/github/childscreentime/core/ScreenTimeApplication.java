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
        
        // Only start background monitoring in the main process
        if (isMainProcess()) {
            startBackgroundMonitoring();
        } else {
            Log.d(TAG, "Not main process - skipping background monitoring setup");
        }
    }
    
    private boolean isMainProcess() {
        String currentProcessName = getCurrentProcessName();
        String packageName = getPackageName();
        return packageName.equals(currentProcessName);
    }
    
    private String getCurrentProcessName() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                for (android.app.ActivityManager.RunningAppProcessInfo processInfo : am.getRunningAppProcesses()) {
                    if (processInfo.pid == android.os.Process.myPid()) {
                        return processInfo.processName;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get current process name", e);
        }
        return getPackageName(); // Fallback to package name
    }
    
    private void initializeServices() {
        try {
            // Only initialize WorkManager in the main process
            if (isMainProcess()) {
                this.workManager = WorkManager.getInstance(this);
                Log.d(TAG, "WorkManager initialized in main process");
            } else {
                Log.d(TAG, "Skipping WorkManager initialization in separate process");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to initialize WorkManager", e);
        }
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
        
        if (workManager == null) {
            Log.w(TAG, "WorkManager not initialized - cannot start background monitoring");
            return;
        }
        
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
    
    /**
     * Force the device into a locked/blocked state by manipulating the credit
     * This method preserves usage statistics by modifying available credit rather than duration
     * 
     * @return boolean true if device was successfully locked, false otherwise
     */
    public synchronized boolean lockDevice() {
        try {
            // Force an update of the blocked state which will trigger screen locking
            TimeManager.updateBlockedState(this);
            
            if (isBlocked()) {
                Log.d(TAG, "Device is already blocked");
                return true;
            }
            
            // Force blocking by reducing credit to be less than current usage
            Credit currentCredit = getTodayCredit();
            if (currentCredit != null) {
                long currentUsage = getDuration();
                // Set credit to current usage minus 1 to force blocking
                currentCredit.minutes = Math.max(0, currentUsage - 1);
                getTodayCreditPreferences().save(currentCredit);
                TimeManager.updateBlockedState(this);
                
                boolean lockSuccessful = isBlocked();
                Log.d(TAG, lockSuccessful ? "Device successfully locked" : "Device lock failed");
                return lockSuccessful;
            } else {
                Log.w(TAG, "Cannot lock device - no credit data available");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error locking device", e);
            return false;
        }
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
