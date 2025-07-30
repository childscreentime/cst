package io.github.childscreentime.ui.activities;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import io.github.childscreentime.R;
import io.github.childscreentime.core.ScreenTimeApplication;

/**
 * Activity that shows the current status of screen time monitoring using fragments
 */
public class StatusActivity extends AppCompatActivity {
    private static final String TAG = "StatusActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);
        
        Log.d(TAG, "StatusActivity started with Navigation Component");
        
        // The rest is handled by StatusFragment through the Navigation Component
    }
}
