package io.github.childscreentime.ui.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
        
        // Don't initialize security manager here - wait for onViewCreated
        Log.d("SecondFragment", "onCreateView completed, deferring security manager initialization");
        
        return binding.getRoot();
    }
    
    private void initializeSecurityManager() {
        Log.d("SecondFragment", "Attempting to initialize SecurityManager...");
        
        try {
            // Always use the activity context when available for consistency
            Context context = getActivity();
            if (context == null) {
                context = getContext();
            }
            if (context == null) {
                Log.e("SecondFragment", "No context available for SecurityManager initialization");
                scheduleSecurityManagerRetry();
                return;
            }
            
            securityManager = new DeviceSecurityManager(context);
            Log.d("SecondFragment", "SecurityManager initialized successfully");
            
            // Immediately update UI elements
            setupParentDiscoveryToggle();
            setupDeviceIdDisplay();
            
        } catch (Exception e) {
            Log.e("SecondFragment", "Security initialization failed", e);
            securityManager = null;
            scheduleSecurityManagerRetry();
        }
    }
    
    private void scheduleSecurityManagerRetry() {
        Log.d("SecondFragment", "Scheduling SecurityManager retry...");
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (securityManager == null && isAdded() && getView() != null) {
                Log.d("SecondFragment", "Retrying SecurityManager initialization...");
                try {
                    Context context = getActivity();
                    if (context != null) {
                        securityManager = new DeviceSecurityManager(context);
                        Log.d("SecondFragment", "SecurityManager retry successful");
                        
                        // Update UI elements after successful retry
                        setupParentDiscoveryToggle();
                        setupDeviceIdDisplay();
                    }
                } catch (Exception retryException) {
                    Log.e("SecondFragment", "SecurityManager retry failed", retryException);
                }
            }
        }, 2000); // 2 second delay for retry
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup static UI elements first
        setupExitButton();
        setupExtendButton();
        
        // Hide virtual keyboard by default and make sure no input fields have focus
        hideVirtualKeyboard();
        
        // Initialize security manager and setup dependent UI elements
        initializeSecurityManager();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        Log.d("SecondFragment", "onResume - refreshing UI elements");
        
        // Hide virtual keyboard first
        hideVirtualKeyboard();
        
        // Check SecurityManager and refresh UI elements
        if (securityManager == null) {
            Log.w("SecondFragment", "SecurityManager is null in onResume, forcing reinitialization");
            initializeSecurityManager();
        } else if (securityManager.isSecurityEnabled()) {
            // SecurityManager exists and is working, just refresh UI
            Log.d("SecondFragment", "SecurityManager exists, refreshing UI elements");
            setupParentDiscoveryToggle();
            setupDeviceIdDisplay();
        } else {
            // SecurityManager exists but not enabled, try to reinitialize
            Log.w("SecondFragment", "SecurityManager not enabled in onResume, attempting reinitialization");
            securityManager = null;
            initializeSecurityManager();
        }
    }
    
    private void hideVirtualKeyboard() {
        try {
            if (getActivity() != null && getView() != null) {
                // Clear focus from any input fields
                if (binding.numExtend != null) {
                    binding.numExtend.clearFocus();
                }
                
                // Hide keyboard
                android.view.inputmethod.InputMethodManager imm = 
                    (android.view.inputmethod.InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
                }
                
                // Request focus on the root view to ensure no input field is focused
                getView().requestFocus();
                
                Log.d("SecondFragment", "Virtual keyboard hidden and focus cleared");
            }
        } catch (Exception e) {
            Log.e("SecondFragment", "Error hiding virtual keyboard", e);
        }
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
                // Hide keyboard first
                hideVirtualKeyboard();
                
                int numExtend = Integer.parseInt(binding.numExtend.getText().toString());
                TimeManager.directTimeExtension(getContext(), numExtend);
                
                // Clear the input field after successful extension
                binding.numExtend.setText("");
                
            } catch (NumberFormatException e) {
                android.util.Log.e("SecondFragment", "Invalid extend time entered", e);
                // Hide keyboard even on error
                hideVirtualKeyboard();
            }
        });
        
        // Configure the input field to be less intrusive
        if (binding.numExtend != null) {
            // Don't auto-focus the input field
            binding.numExtend.clearFocus();
            
            // Hide keyboard when done is pressed
            binding.numExtend.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    hideVirtualKeyboard();
                    return true;
                }
                return false;
            });
        }
    }
    
    private void setupParentDiscoveryToggle() {
        if (binding == null) {
            Log.w("SecondFragment", "Binding is null, cannot setup parent discovery toggle");
            return;
        }
        
        CheckBox discoveryToggle = binding.parentDiscoveryToggle;
        TextView discoveryDescription = binding.parentDiscoveryDescription;
        
        if (discoveryToggle == null || discoveryDescription == null) {
            Log.w("SecondFragment", "Discovery toggle or description view not found");
            return;
        }
        
        // Clear any existing listeners to prevent duplicates
        discoveryToggle.setOnCheckedChangeListener(null);
        
        // Check if security is properly initialized
        if (securityManager == null || !securityManager.isSecurityEnabled()) {
            Log.w("SecondFragment", "SecurityManager not available, disabling parent discovery controls");
            discoveryToggle.setEnabled(false);
            discoveryToggle.setChecked(false);
            discoveryDescription.setText("Parent discovery unavailable - security initialization failed");
            discoveryDescription.setTextColor(0xFFFF0000); // Red color
            return;
        }
        
        Log.d("SecondFragment", "Setting up parent discovery toggle with SecurityManager");
        
        // Enable the toggle and set initial state
        discoveryToggle.setEnabled(true);
        boolean isEnabled = ParentDiscoveryService.isDiscoveryEnabled(getContext());
        discoveryToggle.setChecked(isEnabled);
        updateDiscoveryStatus(discoveryDescription, isEnabled);
        
        // Set up the click listener
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
        if (binding == null) {
            Log.w("SecondFragment", "Binding is null, cannot setup device ID display");
            return;
        }
        
        TextView deviceIdText = binding.deviceIdText;
        View deviceIdCopy = binding.deviceIdCopy;
        
        if (deviceIdText == null || deviceIdCopy == null) {
            Log.w("SecondFragment", "Device ID views not found");
            return;
        }
        
        // Clear any existing click listeners to prevent duplicates
        deviceIdCopy.setOnClickListener(null);
        
        if (securityManager == null || !securityManager.isSecurityEnabled()) {
            Log.w("SecondFragment", "SecurityManager not available, showing security disabled");
            deviceIdText.setText("SECURITY_DISABLED");
            deviceIdText.setTextColor(0xFFFF0000); // Red color
            deviceIdCopy.setEnabled(false);
            return;
        }
        
        String deviceId = securityManager.getDeviceId();
        deviceIdText.setText(deviceId);
        deviceIdText.setTextColor(0xFF000000); // Black color for normal text
        deviceIdCopy.setEnabled(true);
        
        Log.d("SecondFragment", "Device ID display updated: " + deviceId);
        
        // Set up the click listener
        deviceIdCopy.setOnClickListener(v -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) 
                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Device ID", deviceId);
                clipboard.setPrimaryClip(clip);
                
                Toast.makeText(getContext(), R.string.device_id_copied, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("SecondFragment", "Error copying device ID", e);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
