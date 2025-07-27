package io.github.childscreentime.receiver;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import io.github.childscreentime.R;

/**
 * Device admin receiver for managing device admin capabilities
 */
public class AdminReceiver extends DeviceAdminReceiver {
    private boolean deleted = false;
    
    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, context.getString(R.string.device_admin_enabled), Toast.LENGTH_SHORT).show();
        deleted = false;
    }
    
    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, context.getString(R.string.device_admin_disabled), Toast.LENGTH_SHORT).show();
        deleted = true;
    }
}
