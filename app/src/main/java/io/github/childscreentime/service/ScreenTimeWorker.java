package io.github.childscreentime.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import io.github.childscreentime.core.ScreenTimeApplication;
import io.github.childscreentime.core.TimeManager;
import io.github.childscreentime.model.Credit;
import io.github.childscreentime.service.ParentDiscoveryService;

/**
 * Background worker that periodically checks screen time and updates blocking state
 */
public class ScreenTimeWorker extends Worker {
    private static final String TAG = "ScreenTimeWorker";
    private static int executionCount = 0;

    public ScreenTimeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        executionCount++;
        Log.d(TAG, "=== ScreenTimeWorker executing (count: " + executionCount + ") ===");
        
        try {
            Context context = getApplicationContext();
            ScreenTimeApplication app = ScreenTimeApplication.getFromContext(context);
            
            // Update blocking state and get the result
            boolean wasBlocked = app.isBlocked();
            Log.d(TAG, "Before updateBlockedState - wasBlocked: " + wasBlocked);
            
            TimeManager.updateBlockedState(context);
            boolean isNowBlocked = app.isBlocked();
            
            Log.d(TAG, "After updateBlockedState - isNowBlocked: " + isNowBlocked);
            
            // Check if we're approaching the limit and need to wake up the foreground service
            if (!isNowBlocked) {
                Credit credit = app.getTodayCredit();
                if (credit != null) {
                    long remainingMinutes = credit.minutes - app.getDuration();
                    if (remainingMinutes <= 15) {
                        Log.d(TAG, "Approaching limit (" + remainingMinutes + " min remaining) - triggering foreground service active monitoring");
                        // This will cause the foreground service to switch to more frequent checking
                        ScreenLockService.updateOverlayState(context);
                    }
                }
            }
            
            // Always notify service if blocking state changed
            if (wasBlocked != isNowBlocked) {
                Log.d(TAG, "Blocking state changed: " + wasBlocked + " -> " + isNowBlocked + " - updating overlay");
                ScreenLockService.updateOverlayState(context);
            } else {
                Log.d(TAG, "Blocking state unchanged: " + isNowBlocked + " - no overlay update needed");
            }
            
            // Check and restart ParentDiscoveryService if needed (protection against termination)
            checkAndRestartParentDiscoveryService(context);
            
            Log.d(TAG, "=== ScreenTimeWorker completed successfully ===");
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in ScreenTimeWorker", e);
            return Result.retry();
        }
    }
    
    /**
     * Check if ParentDiscoveryService should be running but isn't, and restart it if needed.
     * This provides protection against aggressive termination by game mode apps.
     */
    private void checkAndRestartParentDiscoveryService(Context context) {
        try {
            // Check if parent discovery should be enabled
            if (!ParentDiscoveryService.isDiscoveryEnabled(context)) {
                Log.v(TAG, "Parent discovery disabled - no action needed");
                return;
            }
            
            // Check if service is actually running
            if (!ParentDiscoveryService.isServiceActuallyRunning()) {
                Log.w(TAG, "ParentDiscoveryService should be running but isn't - restarting from ScreenTimeWorker");
                
                // Restart the service
                ParentDiscoveryService.setDiscoveryEnabled(context, true);
                Log.i(TAG, "Restarted ParentDiscoveryService from ScreenTimeWorker");
            } else {
                Log.v(TAG, "ParentDiscoveryService running correctly");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking/restarting ParentDiscoveryService from ScreenTimeWorker", e);
        }
    }
}
