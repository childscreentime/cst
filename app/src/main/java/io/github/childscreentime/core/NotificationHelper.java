package io.github.childscreentime.core;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import io.github.childscreentime.R;
import io.github.childscreentime.model.Credit;
import io.github.childscreentime.ui.activities.MainActivity;

/**
 * Handles all notification-related functionality
 */
public class NotificationHelper {
    
    private static final String TAG = "NotificationHelper";
    private static final String WARNING_CHANNEL_ID = "expiration_warnings";
    private static final int WARNING_NOTIFICATION_ID = 1002;
    
    // Track last warning to prevent spam
    private static long lastWarningTime = 0;
    private static long lastWarningMinutes = -1;
    private static final long MIN_WARNING_INTERVAL = 60000; // 1 minute between warnings
    
    /**
     * Initialize notification channels
     */
    public static void initializeNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (notificationManager != null) {
                // Warning notifications channel
                NotificationChannel warningChannel = new NotificationChannel(
                    WARNING_CHANNEL_ID,
                    "Time Expiration Warnings",
                    NotificationManager.IMPORTANCE_HIGH
                );
                warningChannel.setDescription("Warnings when screen time is about to expire");
                warningChannel.enableVibration(true);
                warningChannel.enableLights(true);
                warningChannel.setShowBadge(true);
                
                notificationManager.createNotificationChannel(warningChannel);
                Log.d(TAG, "Notification channels initialized");
            }
        }
    }
    
    /**
     * Show warning notification when time is about to expire
     */
    public static void showExpirationWarning(Context context, Credit credit, long durationMinutes) {
        long remainingMinutes = credit.minutes - durationMinutes;
        
        // Only show notification when exactly 1 minute remaining
        if (remainingMinutes != 1) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Check if we already showed the 1-minute warning recently
        if (lastWarningMinutes == 1 && currentTime - lastWarningTime < MIN_WARNING_INTERVAL) {
            Log.d(TAG, "1-minute warning already shown recently - skipping");
            return;
        }
        
        // Update tracking variables
        lastWarningTime = currentTime;
        lastWarningMinutes = remainingMinutes;
        
        Log.d(TAG, "Showing 1-minute warning notification");
        
        // Initialize channels if needed
        initializeNotificationChannels(context);
        
        // Create intent for notification tap
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, WARNING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Screen Time Warning")
            .setContentText("1 minute remaining!")
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("Your screen time will expire in 1 minute. The screen will be blocked when time runs out."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            
        NotificationManager notificationManager = 
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            
        if (notificationManager != null) {
            notificationManager.notify(WARNING_NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Showed 1-minute warning notification");
        } else {
            Log.e(TAG, "NotificationManager is null - cannot show expiration warning");
        }
    }
    
    /**
     * Reset warning tracking - call this after time extensions
     */
    public static void resetWarningTracking() {
        lastWarningTime = 0;
        lastWarningMinutes = -1;
        Log.d(TAG, "Warning tracking reset - notifications can be shown again");
    }
}
