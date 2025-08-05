package io.github.childscreentime.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import io.github.childscreentime.service.ParentDiscoveryService;

/**
 * Watchdog receiver to restart ParentDiscoveryService if it's killed by aggressive apps.
 * Listens for system events and ensures the service remains running when discovery is enabled.
 */
public class ServiceWatchdogReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceWatchdog";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);
        
        if (action == null) {
            return;
        }
        
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case Intent.ACTION_PACKAGE_REPLACED:
            case "android.intent.action.QUICKBOOT_POWERON":
            case "com.htc.intent.action.QUICKBOOT_POWERON":
                Log.i(TAG, "System event detected: " + action + " - checking service status");
                restartServiceIfNeeded(context);
                break;
                
            case Intent.ACTION_SCREEN_ON:
                // When screen turns on, verify service is still running
                Log.d(TAG, "Screen turned on - verifying service status");
                restartServiceIfNeeded(context);
                break;
                
            default:
                Log.d(TAG, "Ignoring broadcast: " + action);
                break;
        }
    }
    
    private void restartServiceIfNeeded(Context context) {
        try {
            // Check if discovery is enabled
            if (!ParentDiscoveryService.isDiscoveryEnabled(context)) {
                Log.d(TAG, "Discovery disabled - not starting service");
                return;
            }
            
            // Check if service is actually running
            if (!ParentDiscoveryService.isServiceActuallyRunning()) {
                Log.w(TAG, "Service not running but discovery enabled - restarting");
                
                Intent serviceIntent = new Intent(context, ParentDiscoveryService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                Log.i(TAG, "Restarted ParentDiscoveryService via watchdog");
            } else {
                Log.d(TAG, "Service is running correctly");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart service via watchdog", e);
        }
    }
}
