package io.github.childscreentime.utils;

import static android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE;
import static android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE;

import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.SECOND;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.os.PowerManager;
import android.util.Log;

import java.text.SimpleDateFormat;

public class Utils {
    public static long DAY_IN_MILLIS = 86400000;
    private static SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");

    public static int processTime(int hour, int minute, int second) {
        return (hour * 3600) + (minute * 60) + second;
    }

    public static long millisToMinutes(long millis) {
        return (millis / 1000) / 60;
    }

    public static int[] reverseProcessTime(int time) {
        int time2 = time % 3600;
        return new int[]{time / 3600, time2 / 60, time2 % 60};
    }

    public static void updateInteractiveEventTracker(EventTracker interactiveEventTracker, Context context, long beginTime, long endTime) {
        UsageEvents usageEvents = ((UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE)).queryEvents(beginTime, endTime);
        while (usageEvents.hasNextEvent()) {
            UsageEvents.Event currentEvent = new UsageEvents.Event();
            usageEvents.getNextEvent(currentEvent);
            switch (currentEvent.getEventType()) {
                case SCREEN_INTERACTIVE:
                    interactiveEventTracker.update(currentEvent.getTimeStamp());
                    break;
                case SCREEN_NON_INTERACTIVE:
                    interactiveEventTracker.commitTime(currentEvent.getTimeStamp());
                    break;
            }
        }
    }
    
    public static void ensureCurrentScreenStateTracked(EventTracker interactiveEventTracker, Context context, long currentTime) {
        // Check if screen is currently on/interactive
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = powerManager.isInteractive();
        
        if (isScreenOn && interactiveEventTracker.curStartTime == 0) {
            // Screen is on but we're not tracking - this happens after screen state transitions
            // Start tracking from current time
            interactiveEventTracker.update(currentTime);
            Log.d("Utils", "Fixed tracking: Screen is on but curStartTime was 0, started tracking from " + currentTime);
        } else if (!isScreenOn && interactiveEventTracker.curStartTime != 0) {
            // Screen is off but we're still tracking - commit the time
            interactiveEventTracker.commitTime(currentTime);
            Log.d("Utils", "Fixed tracking: Screen is off but was still tracking, committed time at " + currentTime);
        }
        
        // Debug logging
        Log.d("Utils", "Screen state check: isScreenOn=" + isScreenOn + 
              ", curStartTime=" + interactiveEventTracker.curStartTime + 
              ", duration=" + interactiveEventTracker.duration);
    }

    public static boolean isUsageAccessAllowed(Context context) {
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = 0;
            if (appOpsManager != null) {
                mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, applicationInfo.packageName);
            }
            if (mode == 0) {
                return true;
            }
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static String getTodayAsString() {
        return sdf.format(getTodayAsMillis());
    }

    public static long getTodayAsMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(HOUR_OF_DAY, 0);
        calendar.set(MINUTE, 0);
        calendar.set(SECOND, 0);
        calendar.set(MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static long getEndOfTodayAsMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(HOUR_OF_DAY, 20);
        calendar.set(MINUTE, 30);
        calendar.set(SECOND, 0);
        calendar.set(MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static final class EventTracker {
        public int count;
        public long curStartTime;
        public long duration;
        public long endStepTimeStamp;
        public long lastEventTime;

        public void commitTime(long timeStamp) {
            if (this.curStartTime != 0) {
                this.duration += timeStamp - this.curStartTime;
                this.curStartTime = 0;
            }
        }

        public void endStep(long timeStamp) {
            if (this.curStartTime != 0) {
                this.duration += timeStamp - this.curStartTime;
                this.curStartTime = timeStamp;
            }
            this.endStepTimeStamp = timeStamp;
        }

        public void update(long timeStamp) {
            if (this.curStartTime == 0) {
                this.count++;
            }
            commitTime(timeStamp);
            this.curStartTime = timeStamp;
            this.lastEventTime = timeStamp;
        }
    }
}
