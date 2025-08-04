package io.github.childscreentime.ui.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import io.github.childscreentime.R;
import io.github.childscreentime.core.DeviceSecurityManager;
import io.github.childscreentime.core.ScreenTimeApplication;
import io.github.childscreentime.core.TimeManager;
import io.github.childscreentime.databinding.FragmentSecondBinding;
import io.github.childscreentime.service.ParentDiscoveryService;

/**
 * Second fragment with admin settings and custom extension functionality
 */
public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;
    private DeviceSecurityManager securityManager;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSecondBinding.inflate(inflater, container, false);
        
        try {
            securityManager = new DeviceSecurityManager(getContext());
        } catch (RuntimeException e) {
            // Security initialization failed - parent discovery will be disabled
            securityManager = null;
        }
        
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupExitButton();
        setupExtendButton();
        setupParentDiscoveryToggle();
        setupDeviceIdDisplay();
    }
    
    private void setupExitButton() {
        binding.buttonSecond.setOnClickListener(v -> {
            // Stop monitoring and exit the app
            ScreenTimeApplication app = ScreenTimeApplication.getFromContext(getActivity());
            app.stopBackgroundMonitoring();
            
            // Finish the activity and exit
            if (getActivity() != null) {
                getActivity().finishAndRemoveTask();
            }
            System.exit(0);
        });
    }
    
    private void setupExtendButton() {
        binding.buttonEtendCust.setOnClickListener(v -> {
            try {
                int numExtend = Integer.parseInt(binding.numExtend.getText().toString());
                TimeManager.directTimeExtension(getContext(), numExtend);
            } catch (NumberFormatException e) {
                android.util.Log.e("SecondFragment", "Invalid extend time entered", e);
            }
        });
    }
    
    private void setupParentDiscoveryToggle() {
        CheckBox discoveryToggle = binding.parentDiscoveryToggle;
        TextView discoveryDescription = binding.parentDiscoveryDescription;
        
        // Check if security is properly initialized
        if (securityManager == null || !securityManager.isSecurityEnabled()) {
            discoveryToggle.setEnabled(false);
            discoveryToggle.setChecked(false);
            discoveryDescription.setText("Parent discovery unavailable - security initialization failed");
            discoveryDescription.setTextColor(0xFFFF0000); // Red color
            return;
        }
        
        // Set initial state
        boolean isEnabled = ParentDiscoveryService.isDiscoveryEnabled(getContext());
        discoveryToggle.setChecked(isEnabled);
        updateDiscoveryStatus(discoveryDescription, isEnabled);
        
        discoveryToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ParentDiscoveryService.setDiscoveryEnabled(getContext(), isChecked);
            updateDiscoveryStatus(discoveryDescription, isChecked);
            
            String message = isChecked ? 
                "Parent discovery enabled" : "Parent discovery disabled";
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        });
    }
    
    private void updateDiscoveryStatus(TextView statusView, boolean isEnabled) {
        if (isEnabled) {
            boolean isRunning = ParentDiscoveryService.isServiceRunning(getContext());
            if (isRunning) {
                statusView.setText("Ready for parent device discovery");
                statusView.setTextColor(0xFF00AA00); // Green color
            } else {
                statusView.setText("Discovery service starting...");
                statusView.setTextColor(0xFFFF8800); // Orange color
            }
        } else {
            statusView.setText("Parent discovery disabled");
            statusView.setTextColor(0xFF666666); // Gray color
        }
    }
    
    private void setupDeviceIdDisplay() {
        TextView deviceIdText = binding.deviceIdText;
        View deviceIdCopy = binding.deviceIdCopy;
        
        if (securityManager == null || !securityManager.isSecurityEnabled()) {
            deviceIdText.setText("SECURITY_DISABLED");
            deviceIdText.setTextColor(0xFFFF0000); // Red color
            deviceIdCopy.setEnabled(false);
            return;
        }
        
        String deviceId = securityManager.getDeviceId();
        deviceIdText.setText(deviceId);
        
        deviceIdCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) 
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Device ID", deviceId);
            clipboard.setPrimaryClip(clip);
            
            Toast.makeText(getContext(), R.string.device_id_copied, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
