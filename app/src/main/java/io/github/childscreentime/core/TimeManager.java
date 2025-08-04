package io.github.childscreentime.core;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

import io.github.childscreentime.model.Credit;
import io.github.childscreentime.service.ScreenLockService;
import io.github.childscreentime.ui.activities.MainActivity;
import io.github.childscreentime.utils.Utils;

/**
 * Manages time tracking, blocking logic, and extensions
 */
public class TimeManager {
    
    private static final String TAG = "TimeManager";
    private static final long SYNC_INTERVAL_MINUTES = 5;
    private static volatile boolean extensionInProgress = false;
    
    /**
     * Force an immediate check of blocked state - useful for testing
     */
    public static void forceStateCheck(Context context) {
        Log.d(TAG, "=== FORCED STATE CHECK ===");
        updateBlockedState(context);
    }
    
    /**
     * Update the blocked state based on current usage and credit
     * NOTE: This method assumes usage access permission is already granted
     */
    public static boolean updateBlockedState(Context context) {
        Log.d(TAG, "=== updateBlockedState called ===");
        ScreenTimeApplication app = ScreenTimeApplication.getFromContext(context);
        boolean wasBlocked = app.isBlocked();
        
        // Get current credit and usage
        Credit credit = app.getTodayCredit();
        if (credit == null) {
            Log.e(TAG, "Cannot update blocked state - credit is null");
            return false;
        }
        
        long durationMinutes = Utils.millisToMinutes(updateInteractiveEventTracker(context));
        app.setDuration(durationMinutes);
        
        Log.d(TAG, String.format("=== BLOCKING STATE CHECK === Usage: %d minutes, Credit: %d minutes", 
            durationMinutes, credit.minutes));
        
        // Determine if should block
        boolean shouldBlock = durationMinutes >= credit.minutes;
        app.setBlocked(shouldBlock);
        
        Log.d(TAG, "Should block: " + shouldBlock + " (was: " + wasBlocked + ")");
        
        // Notify service if state changed
        if (wasBlocked != shouldBlock) {
            Log.d(TAG, "Blocking state changed - notifying ScreenLockService");
            notifyScreenLockService(context);
        } else {
            Log.d(TAG, "Blocking state unchanged - no notification needed");
        }
        
        // Show warnings if time is running out
        if (!shouldBlock && credit.expiresSoon(durationMinutes)) {
            long remainingMinutes = credit.minutes - durationMinutes;
            Log.d(TAG, "Time expiring soon - " + remainingMinutes + " minutes remaining");
            
            NotificationHelper.showExpirationWarning(context, credit, durationMinutes);
            
            // Start more frequent checks only when very close to expiration
            if (remainingMinutes <= 1) {
                startFrequentChecks(context);
            }
        }
        
        // Sync to server if needed
        syncIfNeeded(context, app, wasBlocked != shouldBlock);
        
        return wasBlocked != app.isBlocked();
    }
    
    /**
     * Extend time directly without credit checks (admin function)
     */
    public static void directTimeExtension(Context context, int extendMinutes) {
        Log.d(TAG, "=== DIRECT TIME EXTENSION START ===");
        Log.d(TAG, "Direct time extension requested: " + extendMinutes + " minutes");
        
        if (extensionInProgress) {
            Log.w(TAG, "Extension already in progress, skipping request");
            return;
        }
        
        extensionInProgress = true;
        
        try {
            ScreenTimeApplication app = ScreenTimeApplication.getFromContext(context);
            Credit credit = app.getTodayCredit();
            
            if (credit == null) {
                Log.e(TAG, "Cannot extend - credit is null");
                return;
            }
            
            Log.d(TAG, "Before extension - Credit: " + credit.minutes + " minutes, Blocked: " + app.isBlocked());
            
            // Get current usage
            long durationMinutes = Utils.millisToMinutes(updateInteractiveEventTracker(context));
            app.setDuration(durationMinutes);
            
            Log.d(TAG, "Current usage: " + durationMinutes + " minutes");
            
            // Extend the credit
            long newCreditMinutes = durationMinutes + extendMinutes;
            credit.minutes = newCreditMinutes;
            app.getTodayCreditPreferences().save(credit);
            
            Log.d(TAG, String.format("Direct extension applied. Credit: %d minutes (usage %d + extension %d)", 
                newCreditMinutes, durationMinutes, extendMinutes));
            
            // Update state immediately
            app.setBlocked(false);
            
            // Reset notification tracking so warnings can be shown again after extension
            NotificationHelper.resetWarningTracking();
            
            // Force immediate overlay update after credit save
            notifyScreenLockService(context);
            
            // Finish the MainActivity that was kept for blocking (similar to overlay extension buttons)
            MainActivity.finishBlockingInstance();
            
            // Add small delay before re-checking to prevent System UI conflicts
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(() -> {
                Log.d(TAG, "Post-extension delayed state check starting");
                // Force immediate re-check after extension
                updateBlockedState(context);
                // Update overlay again after state check to ensure consistency
                notifyScreenLockService(context);
                Log.d(TAG, "Post-extension state check completed");
                extensionInProgress = false; // Reset flag after delay
            }, 500); // 500ms delay
            
            Log.d(TAG, "After extension - Blocked: " + app.isBlocked() + ", Duration: " + app.getDuration() + ", Credit: " + credit.minutes);
            Log.d(TAG, "=== DIRECT TIME EXTENSION END ===");
        } catch (Exception e) {
            Log.e(TAG, "Error during direct time extension", e);
            extensionInProgress = false; // Reset flag on error
        }
    }
    
    /**
     * Extend time using available extension credits
     */
    public static boolean extendTimeWithCredits(Context context, int requestedMinutes) {
        if (extensionInProgress) {
            Log.w(TAG, "Extension already in progress, skipping credit request");
            return false;
        }
        
        extensionInProgress = true;
        
        try {
            ScreenTimeApplication app = ScreenTimeApplication.getFromContext(context);
            Credit credit = app.getTodayCredit();
            
            if (credit == null) {
                Log.e(TAG, "Cannot extend - credit is null");
                return false;
            }
            
            int grantedMinutes = 0;
            
            // Check available extensions
            if (requestedMinutes < 5 && credit.oneExtends > 0) {
                credit.oneExtends--;
                grantedMinutes = 1;
                Log.d(TAG, "Granted 1-minute extension. Remaining: " + credit.oneExtends);
            } else if (credit.fiveExtends > 0) {
                credit.fiveExtends--;
                grantedMinutes = 5;
                Log.d(TAG, "Granted 5-minute extension. Remaining: " + credit.fiveExtends);
            } else {
                Log.d(TAG, "No extensions available");
                return false;
            }
            
            // Apply extension
            long durationMinutes = Utils.millisToMinutes(updateInteractiveEventTracker(context));
            long newCreditMinutes = durationMinutes + grantedMinutes;
            credit.minutes = newCreditMinutes;
            app.getTodayCreditPreferences().save(credit);
            
            app.setBlocked(false);
            notifyScreenLockService(context);
            
            // Reset notification tracking so warnings can be shown again after extension
            NotificationHelper.resetWarningTracking();
            
            // Add small delay before re-checking to prevent System UI conflicts
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(() -> {
                Log.d(TAG, "Post-credit-extension delayed state check starting");
                // Force immediate re-check after extension
                updateBlockedState(context);
                Log.d(TAG, "Post-credit-extension state check completed");
                extensionInProgress = false; // Reset flag after delay
            }, 500); // 500ms delay
            
            Log.d(TAG, "Extension applied. New credit: " + newCreditMinutes + " minutes");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error during credit extension", e);
            extensionInProgress = false; // Reset flag on error
            return false;
        }
    }
    
    private static long updateInteractiveEventTracker(Context context) {
        ScreenTimeApplication app = ScreenTimeApplication.getFromContext(context);
        
        long beginTime = Utils.getTodayAsMillis();
        long currentMillis = Calendar.getInstance().getTimeInMillis();
        
        // Initialize tracker if needed - thread-safe access
        Utils.EventTracker tracker = app.getInteractiveEventTracker();
        if (tracker == null || beginTime > tracker.endStepTimeStamp) {
            tracker = new Utils.EventTracker();
            tracker.endStepTimeStamp = beginTime;
            app.setInteractiveEventTracker(tracker);
            Log.d(TAG, "Initialized new EventTracker for today");
        }
        
        try {
            Utils.updateInteractiveEventTracker(
                tracker, 
                context, 
                tracker.endStepTimeStamp, 
                currentMillis
            );
            
            // Fix for screen state tracking: After processing events, check if screen is currently on
            // and ensure we're tracking current interactive time properly
            Utils.ensureCurrentScreenStateTracked(tracker, context, currentMillis);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating interactive event tracker", e);
            return 0;
        }
        
        // Calculate total duration
        long totalDuration;
        if (tracker.curStartTime != 0) {
            totalDuration = tracker.duration + 
                           (currentMillis - tracker.curStartTime);
        } else {
            totalDuration = tracker.duration;
        }
        
        tracker.endStepTimeStamp = currentMillis;
        
        // Debug logging for usage tracking
        long durationMinutes = Utils.millisToMinutes(totalDuration);
        Log.d(TAG, "Usage tracking update: duration=" + durationMinutes + "min (" + totalDuration + "ms), " +
              "curStartTime=" + tracker.curStartTime + ", " +
              "storedDuration=" + tracker.duration + "ms");
        
        return totalDuration;
    }
    
    private static void notifyScreenLockService(Context context) {
        try {
            // Use the more efficient direct service call
            ScreenLockService.updateOverlayState(context);
            Log.d(TAG, "Notified ScreenLockService of blocking state change");
        } catch (Exception e) {
            Log.e(TAG, "Failed to notify ScreenLockService", e);
        }
    }
    
    private static void startFrequentChecks(Context context) {
        // Implementation for frequent checks when close to limit
        Log.d(TAG, "Starting frequent checks - close to time limit");
        // This could be moved to a separate FrequentCheckManager class
    }
    
    private static void syncIfNeeded(Context context, ScreenTimeApplication app, boolean stateChanged) {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long lastSync = app.getLastSync();
        boolean shouldSync = lastSync == 0 || 
                           Utils.millisToMinutes(currentTime - lastSync) >= SYNC_INTERVAL_MINUTES || 
                           stateChanged;
                           
        if (shouldSync) {
            app.setLastSync(currentTime);
            // Move server sync to separate class
            Log.d(TAG, "Should sync to server");
        }
    }
}
