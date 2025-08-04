package io.github.childscreentime.ui.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import io.github.childscreentime.R;
import io.github.childscreentime.ui.fragments.SecondFragment;

/**
 * Transparent overlay activity to host SecondFragment when launched from blocking overlay
 */
public class OverlaySettingsActivity extends AppCompatActivity {
    private static final String TAG = "OverlaySettingsActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Make this activity appear over the blocking overlay
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        
        // Create a simple layout with fragment container
        FrameLayout container = new FrameLayout(this);
        container.setId(R.id.fragment_container);
        container.setBackgroundColor(0xFF2C2C2C); // Dark background
        setContentView(container);
        
        Log.d(TAG, "OverlaySettingsActivity created");
        
        // Add SecondFragment to the container
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new SecondFragment())
                .commit();
            
            Log.d(TAG, "SecondFragment added to container");
        }
    }
    
    @Override
    public void onBackPressed() {
        // Close this activity and return to overlay
        super.onBackPressed();
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "OverlaySettingsActivity destroyed");
    }
}
