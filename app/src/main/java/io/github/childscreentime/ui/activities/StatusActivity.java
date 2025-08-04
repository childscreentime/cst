package io.github.childscreentime.ui.activities;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

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
        
        // Check if we should navigate directly to SecondFragment (from overlay)
        if (getIntent().getBooleanExtra("SHOW_SECOND_FRAGMENT", false)) {
            try {
                NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_status);
                navController.navigate(R.id.action_StatusFragment_to_SecondFragment);
                Log.d(TAG, "Navigated directly to SecondFragment from overlay");
            } catch (Exception e) {
                Log.e(TAG, "Failed to navigate to SecondFragment", e);
            }
        }
        
        // The rest is handled by StatusFragment through the Navigation Component
    }
}
