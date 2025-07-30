package io.github.childscreentime.ui.activities;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import io.github.childscreentime.core.ScreenTimeApplication;
import io.github.childscreentime.service.ScreenLockService;
import io.github.childscreentime.utils.Utils;

/**
 * Minimal launcher activity - immediately starts service and finishes
 * All UI functionality is handled by ScreenLockService overlay
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static MainActivity blockingInstance = null;
    
    // Modern Activity Result API for usage access permission
    private final ActivityResultLauncher<Intent> usageAccessLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            Log.d(TAG, "Returned from usage access settings");
            
            if (Utils.isUsageAccessAllowed(this)) {
                Log.i(TAG, "✅ Usage access permission granted - starting app");
                initializeApp();
            } else {
                Log.e(TAG, "❌ Usage access permission denied - app cannot function");
                exitWithError("Usage Access permission is required. Child Screen Time cannot function without accurate time tracking.");
            }
        }
    );

        @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(io.github.childscreentime.R.layout.activity_launcher);
        
        Log.d(TAG, "MainActivity started - checking critical permissions");
        
        // CRITICAL: Check usage access permission first - app cannot function without it
        if (!Utils.isUsageAccessAllowed(this)) {
            Log.w(TAG, "Usage access permission not granted - requesting permission");
            requestUsageAccessPermission();
            return; // Stop here - don't start any services or monitoring
        }
        
        Log.i(TAG, "Usage access permission confirmed - starting app");
        initializeApp();
    }
    
    private void initializeApp() {
        Log.d(TAG, "Initializing app with required permissions");
        
        // Request exact alarm permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }
        
        // Initialize app
        ScreenTimeApplication app = ScreenTimeApplication.getFromContext(this);
        app.running = true;
        app.initMainActivity(this);
        
        // Check overlay permission
        checkOverlayPermission();
        
        // Check notification permission for Android 13+
        checkNotificationPermission();
        
        // Start the service (this handles all UI via overlay)
        ScreenLockService.startService(this);
        
        // Check if we were started for blocking purposes (from service)
        Intent intent = getIntent();
        boolean isForBlocking = intent != null && intent.getBooleanExtra("FOR_BLOCKING", false);
        
        if (isForBlocking) {
            Log.d(TAG, "MainActivity started for blocking - staying in foreground to pause other apps");
            // Stay in foreground to interrupt other apps and pause their media
            // The overlay will be shown by the service, MainActivity just needs to remain active
            
            // Store this instance so ScreenLockService can finish it when blocking ends
            blockingInstance = this;
        } else {
            // Normal startup - finish immediately
            Log.d(TAG, "Normal startup - service started, finishing MainActivity");
            finish();
        }
    }
    
    /**
     * Request usage access permission
     */
    private void requestUsageAccessPermission() {
        Toast.makeText(this, "Usage Access permission is required for Child Screen Time to function properly", Toast.LENGTH_LONG).show();
        
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        try {
            usageAccessLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open usage access settings", e);
            exitWithError("Cannot open usage access settings. Please grant Usage Access permission manually in Settings → Special access → Usage access");
        }
    }
    
    /**
     * Exit the app with an error message
     */
    private void exitWithError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Exiting app: " + message);
        
        // Give user time to read the message
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            finishAndRemoveTask();
            System.exit(1);
        }, 3000);
    }
    
    /**
     * Check and request overlay permission if needed
     */
    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Overlay permission needed - requesting");
                
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open overlay permission settings", e);
                }
            }
        }
    }
    
    /**
     * Check and request notification permission for Android 13+
     */
    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission needed - requesting");
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }
    
    /**
     * Static method to restart service (used by other components)
     */
    public static void startMainActivity(Context context) {
        Log.d(TAG, "Restarting service via MainActivity");
        ScreenLockService.startService(context);
    }
    
    /**
     * Finish the MainActivity instance that was kept for blocking purposes
     */
    public static void finishBlockingInstance() {
        if (blockingInstance != null) {
            Log.d(TAG, "Finishing MainActivity that was kept for blocking");
            blockingInstance.finish();
            blockingInstance = null;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clear the blocking instance reference if this was the blocking instance
        if (blockingInstance == this) {
            blockingInstance = null;
        }
    }
}
