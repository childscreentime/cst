package io.github.childscreentime.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import io.github.childscreentime.core.ScreenTimeApplication;
import io.github.childscreentime.core.TimeManager;
import io.github.childscreentime.databinding.FragmentSecondBinding;

/**
 * Second fragment with admin settings and custom extension functionality
 */
public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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
        
        binding.buttonEtendCust.setOnClickListener(v -> {
            try {
                int numExtend = Integer.parseInt(binding.numExtend.getText().toString());
                TimeManager.directTimeExtension(getContext(), numExtend);
            } catch (NumberFormatException e) {
                android.util.Log.e("SecondFragment", "Invalid extend time entered", e);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
