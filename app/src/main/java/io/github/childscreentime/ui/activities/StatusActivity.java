package io.github.childscreentime.ui.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import io.github.childscreentime.R;
import io.github.childscreentime.core.ScreenTimeApplication;
import io.github.childscreentime.core.TimeManager;
import io.github.childscreentime.model.Credit;

/**
 * Activity that shows the current status of screen time monitoring
 */
public class StatusActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);
        
        ScreenTimeApplication app = ScreenTimeApplication.getFromContext(this);
        
        // Log current values before refresh
        long beforeRefresh = app.getDuration();
        Log.d("StatusActivity", "Before refresh - duration: " + beforeRefresh + " minutes");
        
        // Refresh the current usage data before displaying
        TimeManager.updateBlockedState(this);
        
        // Log values after refresh
        long afterRefresh = app.getDuration();
        Log.d("StatusActivity", "After refresh - duration: " + afterRefresh + " minutes");
        
        TextView statusText = findViewById(R.id.statsView);
        if (statusText != null) {
            Credit credit = app.getTodayCredit();
            String status = String.format(java.util.Locale.ROOT, "Current Usage: %d minutes\nCredit: %s\nBlocked: %s", 
                app.getDuration(), 
                credit != null ? credit.asString() : "No credit",
                app.isBlocked() ? "YES" : "NO");
            statusText.setText(status);
            Log.d("StatusActivity", "Status text set: " + status);
        }
    }
}
