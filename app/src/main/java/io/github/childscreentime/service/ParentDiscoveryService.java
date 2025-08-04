package io.github.childscreentime.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import io.github.childscreentime.R;
import io.github.childscreentime.core.DeviceSecurityManager;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Foreground service that enables parent device discovery and remote control.
 * 
 * This service listens for UDP broadcast messages from parent devices on port 8888
 * and responds with device information. It also handles encrypted commands from
 * authenticated parent devices using AES encryption with device ID-based keys.
 * 
 * Key features:
 * - UDP broadcast discovery protocol
 * - AES-CBC encrypted command/response communication
 * - SHA-256 device ID-based key derivation
 * - Foreground service for reliable operation
 * - User-configurable enable/disable toggle
 */
public class ParentDiscoveryService extends Service {
    
    private static final String TAG = "ParentDiscoveryService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "parent_discovery_channel";
    private static final int DISCOVERY_PORT = 8888;
    private static final int SOCKET_TIMEOUT_MS = 60000; // 1 minute
    private static final int BUFFER_SIZE = 1024;
    
    // Protocol messages
    private static final String DISCOVERY_REQUEST = "CST_PARENT_DISCOVERY";
    private static final String DISCOVERY_RESPONSE = "CST_CHILD_RESPONSE";
    private static final String COMMAND_PREFIX = "CST_CMD:";
    private static final String RESPONSE_PREFIX = "CST_RESP:";
    
    // Preferences
    private static final String PREFS_NAME = "parent_discovery_prefs";
    private static final String KEY_ENABLED = "discovery_enabled";
    
    private DatagramSocket socket;
    private ExecutorService executorService;
    private DeviceSecurityManager securityManager;
    private volatile boolean isRunning = false;
    
    public static boolean isDiscoveryEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ENABLED, false);
    }
    
    public static void setDiscoveryEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        
        Intent serviceIntent = new Intent(context, ParentDiscoveryService.class);
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } else {
            context.stopService(serviceIntent);
        }
    }
    
    /**
     * Check if the service is currently running and listening
     */
    public static boolean isServiceRunning(Context context) {
        return isDiscoveryEnabled(context);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            securityManager = new DeviceSecurityManager(this);
        } catch (RuntimeException e) {
            Log.e(TAG, "Security initialization failed", e);
            setDiscoveryEnabled(this, false);
            stopSelf();
            return;
        }
        executorService = Executors.newCachedThreadPool();
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isDiscoveryEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        
        startForeground(NOTIFICATION_ID, createNotification());
        startDiscoveryListener();
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopDiscoveryListener();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.parent_discovery_service),
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Service for parent device discovery and communication");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.parent_discovery_service))
            .setContentText("Listening for parent device commands")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }
    
    private void startDiscoveryListener() {
        if (isRunning) return;
        
        executorService.execute(() -> {
            try {
                socket = new DatagramSocket(DISCOVERY_PORT);
                socket.setBroadcast(true);
                isRunning = true;
                
                byte[] buffer = new byte[BUFFER_SIZE];
                
                while (isRunning && !socket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                        socket.receive(packet);
                        
                        String message = new String(packet.getData(), 0, packet.getLength());
                        InetAddress senderAddress = packet.getAddress();
                        int senderPort = packet.getPort();
                        
                        handleIncomingMessage(message, senderAddress, senderPort);
                        
                    } catch (SocketTimeoutException e) {
                        // Normal timeout - continue listening
                    } catch (Exception e) {
                        if (isRunning) {
                            Log.w(TAG, "Error receiving packet", e);
                        }
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to start discovery listener", e);
                isRunning = false;
            }
        });
    }
    
    private void stopDiscoveryListener() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
    
    private void handleIncomingMessage(String message, InetAddress senderAddress, int senderPort) {
        try {
            if (DISCOVERY_REQUEST.equals(message)) {
                sendResponse(DISCOVERY_RESPONSE, senderAddress, senderPort);
                
            } else if (message.startsWith(COMMAND_PREFIX)) {
                String encryptedCommand = message.substring(COMMAND_PREFIX.length());
                
                try {
                    String decryptedCommand = securityManager.decryptMessage(encryptedCommand);
                    String response = processCommand(decryptedCommand);
                    String encryptedResponse = securityManager.encryptMessage(response);
                    
                    sendResponse(RESPONSE_PREFIX + encryptedResponse, senderAddress, senderPort);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Encryption/decryption failed", e);
                    setDiscoveryEnabled(this, false);
                    stopSelf();
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error handling message", e);
        }
    }
    
    private String processCommand(String command) {
        try {
            switch (command) {
                case "GET_STATUS":
                    return "STATUS_OK|Device ID: " + securityManager.getDeviceId();
                    
                case "GET_DEVICE_INFO":
                    return String.format("DEVICE_INFO|%s|%s", 
                        android.os.Build.MODEL, android.os.Build.VERSION.RELEASE);
                    
                case "PING":
                    return "PONG";
                    
                case "GET_TIME_LEFT":
                    // TODO: Integrate with TimeManager to get actual time left
                    return "TIME_LEFT|30";
                    
                case "LOCK_DEVICE":
                    // TODO: Integrate with screen locking functionality
                    return "DEVICE_LOCKED|OK";
                    
                case "UNLOCK_DEVICE":
                    // TODO: Integrate with screen unlocking functionality
                    return "DEVICE_UNLOCKED|OK";
                    
                default:
                    return "ERROR|Unknown command";
            }
        } catch (Exception e) {
            Log.w(TAG, "Error processing command: " + command, e);
            return "ERROR|Command processing failed";
        }
    }
    
    private void sendResponse(String response, InetAddress address, int port) {
        executorService.execute(() -> {
            try {
                byte[] responseBytes = response.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(
                    responseBytes, responseBytes.length, address, port);
                
                DatagramSocket responseSocket = new DatagramSocket();
                responseSocket.send(responsePacket);
                responseSocket.close();
                
            } catch (Exception e) {
                Log.w(TAG, "Failed to send response", e);
            }
        });
    }
}
