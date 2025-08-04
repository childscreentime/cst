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
 * Fullscreen activity to host SecondFragment when launched from blocking overlay
 */
public class OverlaySettingsActivity extends AppCompatActivity {
    private static final String TAG = "OverlaySettingsActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "OverlaySettingsActivity onCreate started");
        
        try {
            // Make this activity fullscreen and appear over everything
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
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
            
            Log.d(TAG, "OverlaySettingsActivity layout set");
            
            // Add SecondFragment to the container
            if (savedInstanceState == null) {
                try {
                    getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new SecondFragment())
                        .commitNow(); // Use commitNow() to ensure immediate execution
                    
                    Log.d(TAG, "SecondFragment added to container successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to add SecondFragment", e);
                }
            }
            
            Log.d(TAG, "OverlaySettingsActivity onCreate completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in OverlaySettingsActivity onCreate", e);
        }
    }
    
    @Override
    public void onBackPressed() {
        Log.d(TAG, "Back pressed - finishing activity");
        super.onBackPressed();
        finish();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "OverlaySettingsActivity onResume");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "OverlaySettingsActivity onPause");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "OverlaySettingsActivity destroyed");
    }
}
