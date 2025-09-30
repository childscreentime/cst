package io.github.childscreentime.utils;

import android.content.Context;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import io.github.childscreentime.R;

/**
 * Utility class for handling admin password dialogs across the app
 */
public class AdminPasswordDialog {
    private static final String TAG = "AdminPasswordDialog";
    private static final String ADMIN_PASSWORD = "3443";
    
    /**
     * Interface for handling successful password authentication
     */
    public interface OnPasswordSuccessListener {
        void onPasswordSuccess();
    }
    
    /**
     * Show password dialog and navigate to SecondFragment on success
     * @param fragment The fragment from which to show the dialog and navigate
     * @param navigationAction The navigation action ID to use (e.g., R.id.action_FirstFragment_to_SecondFragment)
     */
    public static void showPasswordDialog(Fragment fragment, int navigationAction) {
        showPasswordDialog(fragment.getContext(), () -> navigateToSecondFragment(fragment, navigationAction));
    }
    
    /**
     * Show password dialog with custom success callback
     * @param context The context to show the dialog in
     * @param onSuccess Callback to execute on successful password entry
     */
    public static void showPasswordDialog(Context context, OnPasswordSuccessListener onSuccess) {
        if (context == null) {
            Log.w(TAG, "Cannot show dialog - context is null");
            return;
        }
        
        // Create password input
        android.widget.EditText passwordInput = new android.widget.EditText(context);
        passwordInput.setHint("Enter admin password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        
        // Create AlertDialog
        new AlertDialog.Builder(context)
            .setTitle("Admin Access Required")
            .setMessage("Enter password to access admin settings:")
            .setView(passwordInput)
            .setPositiveButton("OK", (dialog, which) -> {
                String enteredPassword = passwordInput.getText().toString();
                if (ADMIN_PASSWORD.equals(enteredPassword)) {
                    Log.d(TAG, "Correct password entered - executing success callback");
                    if (onSuccess != null) {
                        onSuccess.onPasswordSuccess();
                    }
                } else {
                    Log.d(TAG, "Incorrect password entered");
                    showPasswordError(context);
                }
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                Log.d(TAG, "Password dialog cancelled");
                dialog.dismiss();
            })
            .setCancelable(true)
            .show();
    }
    
    /**
     * Check if the provided password is correct
     * @param password The password to check
     * @return true if password is correct
     */
    public static boolean isPasswordCorrect(String password) {
        return ADMIN_PASSWORD.equals(password);
    }
    
    /**
     * Navigate to SecondFragment after successful authentication
     */
    private static void navigateToSecondFragment(Fragment fragment, int navigationAction) {
        try {
            NavHostFragment.findNavController(fragment)
                .navigate(navigationAction);
            Log.d(TAG, "Navigation to SecondFragment successful");
        } catch (Exception e) {
            Log.e(TAG, "Failed to navigate to SecondFragment", e);
        }
    }
    
    /**
     * Show password error message
     */
    private static void showPasswordError(Context context) {
        new AlertDialog.Builder(context)
            .setTitle("Access Denied")
            .setMessage("Incorrect password!\nAccess denied.")
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .setCancelable(true)
            .show();
    }
}
