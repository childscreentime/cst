package io.github.childscreentime.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import io.github.childscreentime.R;
import io.github.childscreentime.core.ScreenTimeApplication;
import io.github.childscreentime.core.TimeManager;
import io.github.childscreentime.databinding.FragmentStatusBinding;
import io.github.childscreentime.model.Credit;
import io.github.childscreentime.utils.Utils;

/**
 * Fragment that shows the status information with admin access
 */
public class StatusFragment extends Fragment {
    private static final String TAG = "StatusFragment";
    
    private FragmentStatusBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStatusBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Add menu for admin access
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_settings) {
                    Log.d(TAG, "Admin settings menu clicked from StatusFragment");
                    new NoticeDialogFragment().show(getParentFragmentManager(), "NoticeDialogFragment");
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        // Load initial status
        refreshStatus();
        
        // Set up refresh button if it exists in parent activity
        if (getActivity() != null) {
            getActivity().findViewById(R.id.refreshButton).setOnClickListener(v -> refreshStatus());
        }
    }

    private void refreshStatus() {
        if (getContext() == null || binding == null) return;
        
        ScreenTimeApplication app = ScreenTimeApplication.getFromContext(getContext());
        
        // Log current values before refresh
        long beforeRefresh = app.getDuration();
        Log.d(TAG, "Before refresh - duration: " + beforeRefresh + " minutes");
        
        // Refresh the current usage data before displaying
        TimeManager.updateBlockedState(getContext());
        
        // Log values after refresh
        long afterRefresh = app.getDuration();
        Log.d(TAG, "After refresh - duration: " + afterRefresh + " minutes");
        
        // Update status display
        Credit credit = app.getTodayCredit();
        String status = String.format(java.util.Locale.ROOT, 
            "Current Usage: %d minutes\nCredit: %s\nBlocked: %s\n\nInteractive Events: %s", 
            app.getDuration(), 
            credit != null ? credit.asString() : "No credit",
            app.isBlocked() ? "YES" : "NO",
            getEventTrackerInfo(app));
            
        binding.statusText.setText(status);
        Log.d(TAG, "Status updated: " + status);
    }
    
    private String getEventTrackerInfo(ScreenTimeApplication app) {
        try {
            Utils.EventTracker tracker = app.getInteractiveEventTracker();
            if (tracker != null) {
                return String.format(java.util.Locale.ROOT, 
                    "Count: %d, Duration: %dms", tracker.count, tracker.duration);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting event tracker info", e);
        }
        return "No tracking data";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
