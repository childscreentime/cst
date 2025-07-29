package io.github.childscreentime.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import io.github.childscreentime.R;
import io.github.childscreentime.core.ScreenTimeApplication;
import io.github.childscreentime.core.TimeManager;
import io.github.childscreentime.model.Credit;
import io.github.childscreentime.ui.activities.MainActivity;
import io.github.childscreentime.utils.Utils;

/**
 * Foreground service that monitors screen time and shows unescapable overlay when blocked
 */
public class ScreenLockService extends Service {
    private static final String TAG = "ScreenLockService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "screen_lock_service";
    
    private WindowManager windowManager;
    private View blockingView;
    private ScreenTimeApplication app;
    private boolean isShowingBlockingView = false;
    private android.os.Handler periodicHandler;
    private Runnable periodicCheck;
    private PowerManager powerManager;
    private boolean isScreenOn = true;
    private BroadcastReceiver screenReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScreenLockService created");
        
        app = ScreenTimeApplication.getFromContext(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        
        // Setup screen state monitoring
        setupScreenStateMonitoring();
        
        // Initialize periodic state checking
        setupPeriodicChecks();
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ScreenLockService started");
        
        String action = intent != null ? intent.getAction() : null;
        if ("UPDATE_OVERLAY_ONLY".equals(action)) {
            Log.d(TAG, "Received blocking state update request");
            // Update blocking state and show/hide overlay as needed
            updateBlockingOverlay();
        } else {
            // Initial startup - ensure state is current
            TimeManager.updateBlockedState(this);
            updateBlockingOverlay();
        }
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ScreenLockService destroyed");
        stopPeriodicChecks();
        stopScreenStateMonitoring();
        hideBlockingOverlay();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    public void updateBlockingOverlay() {
        // Always get the current app state to ensure we have the latest blocking status
        ScreenTimeApplication currentApp = ScreenTimeApplication.getFromContext(this);
        boolean shouldBlock = currentApp.blocked;
        
        Log.d(TAG, "updateBlockingOverlay - shouldBlock: " + shouldBlock + ", isShowingBlockingView: " + isShowingBlockingView);
        
        // Only make changes if state actually changed to avoid unnecessary work
        if (shouldBlock && !isShowingBlockingView) {
            Log.d(TAG, "Showing blocking overlay");
            showBlockingOverlay();
        } else if (!shouldBlock && isShowingBlockingView) {
            Log.d(TAG, "Hiding blocking overlay");
            hideBlockingOverlay();
        } else {
            Log.d(TAG, "Overlay state unchanged - no action needed");
        }
        
        // Update notification
        updateNotification();
        
        // Reschedule periodic checks if needed (but don't do it immediately)
        periodicHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                reschedulePeriodicCheckAfterUpdate();
            }
        }, 100); // Small delay to avoid immediate rescheduling
    }
    
    private void reschedulePeriodicCheckAfterUpdate() {
        if (periodicHandler == null || periodicCheck == null) {
            return; // No periodic checking setup
        }
        
        if (!isScreenOn) {
            Log.d(TAG, "Screen is OFF - not rescheduling periodic check");
            return;
        }
        
        // Remove any pending checks and reschedule based on current state
        periodicHandler.removeCallbacks(periodicCheck);
        long nextInterval = getNextCheckInterval();
        
        if (nextInterval == -1) {
            Log.d(TAG, "Plenty of time remaining - no periodic check rescheduled, WorkManager will handle monitoring");
        } else {
            periodicHandler.postDelayed(periodicCheck, nextInterval);
            Log.d(TAG, "Periodic check rescheduled after overlay update (screen is ON)");
        }
    }
    
    private void showBlockingOverlay() {
        if (isShowingBlockingView || !hasOverlayPermission()) {
            return;
        }
        
        try {
            LayoutInflater inflater = LayoutInflater.from(this);
            // Passing null is appropriate here as this view will be added to an overlay window
            @SuppressWarnings("InflateParams")
            View view = inflater.inflate(R.layout.service_blocking_layout, null);
            blockingView = view;
            
            setupBlockingContent(blockingView);
            setupBlockingView(blockingView);
            
            WindowManager.LayoutParams params = createOverlayParams();
            windowManager.addView(blockingView, params);
            isShowingBlockingView = true;
            
            // Bring our app to the foreground to interrupt other apps (which pauses their media)
            // Do this in background thread to avoid blocking UI
            new Thread(new Runnable() {
                @Override
                public void run() {
                    bringAppToForeground();
                }
            }).start();
            
            Log.d(TAG, "Blocking overlay shown and app bring-to-foreground queued");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to show blocking overlay", e);
        }
    }
    
    private void hideBlockingOverlay() {
        if (!isShowingBlockingView || blockingView == null) {
            return;
        }
        
        try {
            // Remove from window manager
            if (windowManager != null && blockingView != null) {
                windowManager.removeView(blockingView);
            }
            
            // Clear references
            blockingView = null;
            isShowingBlockingView = false;
            
            Log.d(TAG, "Blocking overlay hidden");
        } catch (Exception e) {
            Log.e(TAG, "Failed to hide blocking overlay", e);
            // Force cleanup even if removal failed
            blockingView = null;
            isShowingBlockingView = false;
        }
    }
    
    /**
     * Bring our app to the foreground, which will naturally pause other apps and their media
     */
    private void bringAppToForeground() {
        try {
            // Create a proper intent to bring MainActivity to foreground for blocking
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                           Intent.FLAG_ACTIVITY_CLEAR_TASK |
                           Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            
            // Add flag to indicate this is for blocking purposes
            intent.putExtra("FOR_BLOCKING", true);
            
            // Start the activity
            startActivity(intent);
            Log.d(TAG, "Started MainActivity for blocking with NEW_TASK | CLEAR_TASK | BROUGHT_TO_FRONT");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to bring app to foreground", e);
        }
    }
    
    private void setupBlockingContent(View targetBlockingView) {
        FrameLayout contentArea = targetBlockingView.findViewById(R.id.content_area);
        if (contentArea != null) {
            LayoutInflater inflater = LayoutInflater.from(this);
            // Passing null is appropriate here as this view will be added to the contentArea FrameLayout
            @SuppressWarnings("InflateParams")
            View firstFragmentView = inflater.inflate(R.layout.fragment_first, null);
            contentArea.addView(firstFragmentView);
            
            targetBlockingView.setTag(firstFragmentView);
            setupFirstFragmentButtons(firstFragmentView);
        } else {
            Log.e(TAG, "Content area not found in service_blocking_layout");
        }
    }
    
    private void setupFirstFragmentButtons(View firstFragmentView) {
        if (firstFragmentView == null) return;
        
        Button extend1Button = firstFragmentView.findViewById(R.id.button_first);
        Button extend5Button = firstFragmentView.findViewById(R.id.button_extfive);
        
        Credit credit = app.getTodayCredit();
        
        if (extend1Button != null) {
            boolean canExtend1 = credit != null && credit.oneExtends > 0;
            extend1Button.setEnabled(canExtend1);
            
            extend1Button.setOnClickListener(v -> {
                Log.d(TAG, "1-minute extension requested from overlay");
                if (TimeManager.extendTimeWithCredits(this, 1)) {
                    updateBlockingOverlay();
                    // Finish the MainActivity that was kept for blocking
                    MainActivity.finishBlockingInstance();
                }
            });
        }
        
        if (extend5Button != null) {
            boolean canExtend5 = credit != null && credit.fiveExtends > 0;
            extend5Button.setEnabled(canExtend5);
            
            extend5Button.setOnClickListener(v -> {
                Log.d(TAG, "5-minute extension requested from overlay");
                if (TimeManager.extendTimeWithCredits(this, 5)) {
                    updateBlockingOverlay();
                    // Finish the MainActivity that was kept for blocking
                    MainActivity.finishBlockingInstance();
                }
            });
        }
    }
    
    private void setupBlockingView(View targetBlockingView) {
        if (targetBlockingView == null) return;
        
        // Make the view truly fullscreen and hide system UI
        targetBlockingView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        
        // Make the view focusable to capture all touch events
        targetBlockingView.setFocusable(true);
        targetBlockingView.setFocusableInTouchMode(true);
        targetBlockingView.requestFocus();
        
        // Override key events to prevent back button, home button, etc.
        targetBlockingView.setOnKeyListener((v, keyCode, event) -> {
            // Block all hardware keys except volume (for emergencies)
            if (keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_SEARCH ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                Log.d(TAG, "Blocked hardware key: " + keyCode);
                return true; // Consume the event
            }
            return false; // Allow other keys (like volume)
        });
        
        // Setup FAB for settings
        View fab = targetBlockingView.findViewById(R.id.fab);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                Log.d(TAG, "Settings FAB clicked from overlay - requesting password");
                showPasswordDialog();
            });
        }
        
        updateTimeDisplay(targetBlockingView);
    }
    
    private void updateTimeDisplay(View targetBlockingView) {
        if (targetBlockingView == null) {
            return;
        }
        
        View firstFragmentView = (View) targetBlockingView.getTag();
        if (firstFragmentView == null) return;
        
        TextView durationTextView = firstFragmentView.findViewById(R.id.duration);
        if (durationTextView == null) return;
        
        Credit credit = app.getTodayCredit();
        if (credit != null) {
            String displayText = String.format(java.util.Locale.ROOT, "Used: %d min | Limit: %d min\nBlocked: %s", 
                app.duration, credit.minutes, app.blocked ? "YES" : "NO");
            durationTextView.setText(displayText);
        }
    }
    
    // Keep the old method for compatibility with existing calls
    private void updateTimeDisplay() {
        updateTimeDisplay(blockingView);
    }
    
    /**
     * Show password dialog to protect admin settings access
     */
    private void showPasswordDialog() {
        try {
            // Create a simple password input dialog using the overlay
            LayoutInflater inflater = LayoutInflater.from(this);
            View passwordView = inflater.inflate(android.R.layout.select_dialog_item, null);
            
            // Create a simple password input layout
            android.widget.LinearLayout passwordLayout = new android.widget.LinearLayout(this);
            passwordLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
            passwordLayout.setPadding(50, 50, 50, 50);
            passwordLayout.setBackgroundColor(0xFF2C2C2C); // Dark background
            
            // Title
            android.widget.TextView titleText = new android.widget.TextView(this);
            titleText.setText("Admin Password Required");
            titleText.setTextColor(0xFFFFFFFF);
            titleText.setTextSize(18);
            titleText.setGravity(android.view.Gravity.CENTER);
            titleText.setPadding(0, 0, 0, 30);
            passwordLayout.addView(titleText);
            
            // Password input
            android.widget.EditText passwordInput = new android.widget.EditText(this);
            passwordInput.setHint("Enter password");
            passwordInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            passwordInput.setTextColor(0xFFFFFFFF);
            passwordInput.setHintTextColor(0xFFAAAAAA);
            passwordInput.setBackgroundColor(0xFF444444);
            passwordInput.setPadding(20, 20, 20, 20);
            passwordLayout.addView(passwordInput);
            
            // Buttons layout
            android.widget.LinearLayout buttonsLayout = new android.widget.LinearLayout(this);
            buttonsLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            buttonsLayout.setPadding(0, 30, 0, 0);
            
            // Cancel button
            android.widget.Button cancelButton = new android.widget.Button(this);
            cancelButton.setText("Cancel");
            cancelButton.setBackgroundColor(0xFF666666);
            cancelButton.setTextColor(0xFFFFFFFF);
            cancelButton.setOnClickListener(v -> {
                Log.d(TAG, "Password dialog cancelled");
                showMainBlockingContent(); // Return to main screen
            });
            buttonsLayout.addView(cancelButton);
            
            // OK button
            android.widget.Button okButton = new android.widget.Button(this);
            okButton.setText("OK");
            okButton.setBackgroundColor(0xFF4CAF50);
            okButton.setTextColor(0xFFFFFFFF);
            okButton.setOnClickListener(v -> {
                String enteredPassword = passwordInput.getText().toString();
                if ("253".equals(enteredPassword)) {
                    Log.d(TAG, "Correct password entered - showing admin settings");
                    showSettingsDialog();
                } else {
                    Log.d(TAG, "Incorrect password entered");
                    // Show error and return to main screen
                    showPasswordError();
                }
            });
            buttonsLayout.addView(okButton);
            
            passwordLayout.addView(buttonsLayout);
            
            // Replace content with password dialog
            FrameLayout contentArea = blockingView.findViewById(R.id.content_area);
            if (contentArea != null) {
                contentArea.removeAllViews();
                contentArea.addView(passwordLayout);
                
                Log.d(TAG, "Password dialog shown");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to show password dialog", e);
        }
    }
    
    /**
     * Show password error and return to main screen
     */
    private void showPasswordError() {
        try {
            // Create error message layout
            android.widget.LinearLayout errorLayout = new android.widget.LinearLayout(this);
            errorLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
            errorLayout.setPadding(50, 50, 50, 50);
            errorLayout.setBackgroundColor(0xFF2C2C2C);
            
            // Error message
            android.widget.TextView errorText = new android.widget.TextView(this);
            errorText.setText("Incorrect Password!\nAccess Denied");
            errorText.setTextColor(0xFFFF4444);
            errorText.setTextSize(20);
            errorText.setGravity(android.view.Gravity.CENTER);
            errorText.setPadding(0, 0, 0, 30);
            errorLayout.addView(errorText);
            
            // OK button to return
            android.widget.Button okButton = new android.widget.Button(this);
            okButton.setText("OK");
            okButton.setBackgroundColor(0xFF666666);
            okButton.setTextColor(0xFFFFFFFF);
            okButton.setOnClickListener(v -> {
                Log.d(TAG, "Password error acknowledged - returning to main screen");
                showMainBlockingContent();
            });
            errorLayout.addView(okButton);
            
            // Replace content with error dialog
            FrameLayout contentArea = blockingView.findViewById(R.id.content_area);
            if (contentArea != null) {
                contentArea.removeAllViews();
                contentArea.addView(errorLayout);
            }
            
            Log.d(TAG, "Password error dialog shown");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to show password error", e);
            // Fallback to main screen
            showMainBlockingContent();
        }
    }
    
    private void showSettingsDialog() {
        try {
            LayoutInflater inflater = LayoutInflater.from(this);
            View secondFragmentView = inflater.inflate(R.layout.fragment_second, null);
            
            FrameLayout contentArea = blockingView.findViewById(R.id.content_area);
            if (contentArea != null) {
                contentArea.removeAllViews();
                contentArea.addView(secondFragmentView);
                setupSecondFragmentButtons(secondFragmentView);
                
                Log.d(TAG, "Loaded SecondFragment content into overlay");
            } else {
                Log.e(TAG, "Content area not found in blocking overlay");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to show settings dialog", e);
        }
    }
    
    private void setupSecondFragmentButtons(View fragmentView) {
        // Exit button
        View exitButton = fragmentView.findViewById(R.id.button_second);
        if (exitButton != null) {
            exitButton.setOnClickListener(v -> {
                Log.d(TAG, "Exit button clicked");
                app.exit();
                stopSelf();
            });
        }
        
        // Custom extend button
        View extendButton = fragmentView.findViewById(R.id.button_etend_cust);
        View extendInput = fragmentView.findViewById(R.id.num_extend);
        if (extendButton != null && extendInput != null) {
            extendButton.setOnClickListener(v -> {
                try {
                    android.widget.EditText editText = (android.widget.EditText) extendInput;
                    String extendText = editText.getText().toString();
                    int extendMinutes = Integer.parseInt(extendText);
                    
                    Log.d(TAG, "Custom extend requested: " + extendMinutes + " minutes");
                    TimeManager.directTimeExtension(this, extendMinutes);
                    updateBlockingOverlay();
                    // Finish the MainActivity that was kept for blocking
                    MainActivity.finishBlockingInstance();
                    
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid extend time entered", e);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing extend request", e);
                }
            });
        }
        
        // Back button (FAB)
        View fab = blockingView.findViewById(R.id.fab);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                Log.d(TAG, "Back button clicked from settings");
                showMainBlockingContent();
            });
        }
    }
    
    private void showMainBlockingContent() {
        try {
            FrameLayout contentArea = blockingView.findViewById(R.id.content_area);
            if (contentArea != null) {
                contentArea.removeAllViews();
                
                LayoutInflater inflater = LayoutInflater.from(this);
                View firstFragmentView = inflater.inflate(R.layout.fragment_first, null);
                contentArea.addView(firstFragmentView);
                
                blockingView.setTag(firstFragmentView);
                setupFirstFragmentButtons(firstFragmentView);
                
                // Restore FAB functionality
                View fab = blockingView.findViewById(R.id.fab);
                if (fab != null) {
                    fab.setOnClickListener(v -> {
                        Log.d(TAG, "Settings FAB clicked from main screen - requesting password");
                        showPasswordDialog();
                    });
                }
                
                updateTimeDisplay();
                Log.d(TAG, "Restored main blocking content");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to show main blocking content", e);
        }
    }
    
    private WindowManager.LayoutParams createOverlayParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        // Flags to make overlay truly fullscreen and block all interactions
        params.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN |
                      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                      WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                      WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                      WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                      WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                      WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                      WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                      WindowManager.LayoutParams.FLAG_SECURE;
        
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.TOP | Gravity.START;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        
        // Ensure overlay covers system UI areas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        
        return params;
    }
    
    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return android.provider.Settings.canDrawOverlays(this);
        }
        return true;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Screen Lock Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitoring screen time and blocking when limit exceeded");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        
        String contentText = app.blocked ? 
            "Screen is blocked - time limit exceeded" : 
            String.format(java.util.Locale.ROOT, "Monitoring screen time - %d minutes used", app.duration);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kid Screen Lock")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build();
    }
    
    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }
    
    public static void startService(Context context) {
        Intent serviceIntent = new Intent(context, ScreenLockService.class);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
        
        Log.d(TAG, "ScreenLockService start requested");
    }
    
    public static void stopService(Context context) {
        Intent serviceIntent = new Intent(context, ScreenLockService.class);
        context.stopService(serviceIntent);
        Log.d(TAG, "ScreenLockService stop requested");
    }
    
    private void setupPeriodicChecks() {
        periodicHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        periodicCheck = new Runnable() {
            @Override
            public void run() {
                // Move heavy operations to background thread to avoid blocking UI
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Early exit conditions - don't waste resources
                            if (!isScreenOn) {
                                Log.d(TAG, "Screen is OFF - skipping periodic check to save battery");
                                return; // Don't schedule next check - will resume when screen turns on
                            }
                            
                            Credit credit = app.getTodayCredit();
                            if (credit == null) {
                                Log.w(TAG, "Credit is null - skipping check");
                                return;
                            }
                            
                            long remainingMinutes = credit.minutes - app.duration;
                            Log.d(TAG, "=== Background Periodic Check === (Remaining: " + remainingMinutes + " min, Screen: ON)");
                            
                            // Update blocking state in background
                            boolean wasBlocked = app.blocked;
                            TimeManager.updateBlockedState(ScreenLockService.this);
                            boolean isNowBlocked = app.blocked;
                            
                            // Only update UI on main thread if state changed
                            if (wasBlocked != isNowBlocked) {
                                final boolean finalIsNowBlocked = isNowBlocked;
                                periodicHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d(TAG, "State change detected: " + wasBlocked + " -> " + finalIsNowBlocked);
                                        updateBlockingOverlay();
                                    }
                                });
                            }
                            
                            // Schedule next check from background thread
                            long nextCheckInterval = getNextCheckInterval();
                            periodicHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    scheduleNextCheckIfNeeded(nextCheckInterval, remainingMinutes);
                                }
                            });
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error in background periodic check", e);
                        }
                    }
                }).start();
            }
        };
        
        // Start periodic checks if screen is on
        startPeriodicChecksIfScreenOn();
    }
    
    private void startPeriodicChecksIfScreenOn() {
        if (isScreenOn) {
            periodicHandler.post(periodicCheck);
            Log.d(TAG, "Periodic state checking initialized (screen is ON)");
        } else {
            Log.d(TAG, "Periodic state checking deferred (screen is OFF - will start when screen turns on)");
        }
    }
    
    private void scheduleNextCheckIfNeeded(long nextCheckInterval, long remainingMinutes) {
        if (nextCheckInterval == -1) {
            Log.d(TAG, "Plenty of time remaining (" + remainingMinutes + " min) - no more foreground service checks needed, WorkManager will handle monitoring");
            return; // Don't schedule - WorkManager handles it
        }
        
        if (!isScreenOn) {
            Log.d(TAG, "Screen turned OFF during check - not scheduling next check");
            return; // Don't schedule if screen went off
        }
        
        // Log what we're doing
        if (nextCheckInterval >= 300000) { // 5+ minutes
            Log.d(TAG, "Blocked state - minimal monitoring, next check in " + (nextCheckInterval / 60000) + " minutes");
        } else {
            Log.d(TAG, "Close to limit - active monitoring, next check in " + (nextCheckInterval / 1000) + " seconds");
        }
        
        // Schedule the next check
        periodicHandler.postDelayed(periodicCheck, nextCheckInterval);
    }
    
    private long getNextCheckInterval() {
        try {
            Credit credit = app.getTodayCredit();
            if (credit == null) return 30000; // 30 seconds fallback
            
            long remainingMinutes = credit.minutes - app.duration;
            
            // If already blocked, check less frequently since WorkManager handles most monitoring
            if (app.blocked) {
                return 600000; // 10 minutes when blocked (reduced frequency for performance)
            }
            
            // Reduced frequency checks to improve performance
            if (remainingMinutes <= 1) {
                return 30000; // 30 seconds when very close (was 10s)
            } else if (remainingMinutes <= 5) {
                return 60000; // 1 minute when close (was 30s)
            } else if (remainingMinutes <= 15) {
                return 300000; // 5 minutes for moderate time remaining
            } else {
                // When there's plenty of time, don't schedule any checks - let WorkManager handle it completely
                return -1; // Special value indicating no scheduling needed
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating check interval", e);
            return -1; // Let WorkManager handle it on error - conservative for battery
        }
    }
    
    private void stopPeriodicChecks() {
        if (periodicHandler != null && periodicCheck != null) {
            periodicHandler.removeCallbacks(periodicCheck);
            Log.d(TAG, "Periodic state checking stopped");
        }
    }
    
    private void setupScreenStateMonitoring() {
        // Check initial screen state
        if (powerManager != null) {
            isScreenOn = powerManager.isInteractive();
            Log.d(TAG, "Initial screen state: " + (isScreenOn ? "ON" : "OFF"));
        }
        
        // Register for screen on/off broadcasts
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    isScreenOn = true;
                    Log.d(TAG, "Screen turned ON - resuming periodic checks");
                    resumePeriodicChecks();
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    isScreenOn = false;
                    Log.d(TAG, "Screen turned OFF - pausing periodic checks");
                    pausePeriodicChecks();
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
        
        Log.d(TAG, "Screen state monitoring initialized");
    }
    
    private void stopScreenStateMonitoring() {
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
                Log.d(TAG, "Screen state monitoring stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping screen state monitoring", e);
            }
        }
    }
    
    private void resumePeriodicChecks() {
        if (periodicHandler != null && periodicCheck != null) {
            periodicHandler.removeCallbacks(periodicCheck);
            periodicHandler.post(periodicCheck);
        }
    }
    
    private void pausePeriodicChecks() {
        if (periodicHandler != null && periodicCheck != null) {
            periodicHandler.removeCallbacks(periodicCheck);
        }
    }

    /**
     * Efficiently update overlay state without redundant blocking state calculation
     */
    public static void updateOverlayState(Context context) {
        Intent serviceIntent = new Intent(context, ScreenLockService.class);
        serviceIntent.setAction("UPDATE_OVERLAY_ONLY");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
        
        Log.d(TAG, "ScreenLockService overlay update requested");
    }
}
