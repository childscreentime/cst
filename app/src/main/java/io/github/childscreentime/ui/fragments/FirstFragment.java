package io.github.childscreentime.ui.fragments;

import android.os.Bundle;
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
import io.github.childscreentime.databinding.FragmentFirstBinding;
import io.github.childscreentime.model.Credit;
import io.github.childscreentime.utils.AdminPasswordDialog;
import io.github.childscreentime.utils.Utils;

/**
 * First fragment showing screen time status and extension buttons
 */
public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private ScreenTimeApplication app;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        app = ScreenTimeApplication.getFromContext(getActivity());
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Set up button click listeners
        binding.buttonFirst.setOnClickListener(v -> {
            TimeManager.extendTimeWithCredits(getContext(), 1);
        });
        
        binding.buttonExtfive.setOnClickListener(v -> {
            TimeManager.extendTimeWithCredits(getContext(), 5);
        });
        
        // Register callback for credit updates
        if (app != null && binding != null && isAdded()) {
            app.registerCreditCallback(this::setButtonVisibility);
            
            // Immediately update UI with current credit if available
            Credit currentCredit = app.getTodayCredit();
            if (currentCredit != null) {
                setButtonVisibility(currentCredit);
            }
        }
        
        MenuHost menuHost = getActivity();
        if (menuHost != null) {
            menuHost.addMenuProvider(new MenuProvider() {
                @Override
                public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                    menuInflater.inflate(R.menu.menu_main, menu);
                }

                @Override
                public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                    if (menuItem.getItemId() == R.id.action_settings) {
                        AdminPasswordDialog.showPasswordDialog(FirstFragment.this, R.id.action_FirstFragment_to_SecondFragment);
                    }
                    return true;
                }
            }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        }
    }

    public void setButtonVisibility(Credit credit) {
        // Check if fragment view is still valid
        if (binding == null) {
            android.util.Log.w("FirstFragment", "setButtonVisibility called but binding is null - fragment view destroyed");
            return;
        }
        
        // Additional safety check for activity
        if (getActivity() == null || getContext() == null) {
            android.util.Log.w("FirstFragment", "setButtonVisibility called but activity/context is null");
            return;
        }
        
        // Check if fragment is still attached and view lifecycle is valid
        if (!isAdded() || getViewLifecycleOwner().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.DESTROYED)) {
            android.util.Log.w("FirstFragment", "setButtonVisibility called but fragment is not in valid state");
            return;
        }
        
        try {
            binding.buttonFirst.setEnabled(credit.oneExtends > 0);
            binding.buttonExtfive.setEnabled(credit.fiveExtends > 0);
            
            // Enhanced display with debug info
            boolean hasUsageAccess = io.github.childscreentime.utils.Utils.isUsageAccessAllowed(getContext());
            String debugInfo = String.format(java.util.Locale.ROOT, "Time: %d min | Credit: %s\\nUsage Access: %s | Blocked: %s", 
                app.getDuration(), credit.asString(), hasUsageAccess ? "✅" : "❌", app.isBlocked() ? "YES" : "NO");
                
            binding.duration.setText(debugInfo);
            
            // Log detailed info for debugging
            android.util.Log.d("FirstFragment", "=== UI Update ===");
            android.util.Log.d("FirstFragment", "Duration: " + app.getDuration() + " minutes");
            android.util.Log.d("FirstFragment", "Credit: " + credit.asString());
            android.util.Log.d("FirstFragment", "Blocked: " + app.isBlocked());
            android.util.Log.d("FirstFragment", "Usage Access: " + hasUsageAccess);
            
            Utils.EventTracker tracker = app.getInteractiveEventTracker();
            if (tracker != null) {
                android.util.Log.d("FirstFragment", "EventTracker - Count: " + tracker.count + 
                    ", Duration: " + tracker.duration + "ms" +
                    ", CurStart: " + tracker.curStartTime);
            }
        } catch (Exception e) {
            android.util.Log.e("FirstFragment", "Error updating UI in setButtonVisibility", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Unregister callback to prevent crashes when fragment is destroyed
        if (app != null) {
            app.registerCreditCallback(null);
            android.util.Log.d("FirstFragment", "Unregistered credit callback on fragment destroy");
        }
        
        binding = null;
    }
}
