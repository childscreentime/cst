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
import io.github.childscreentime.core.ScreenTimeApplication;
import io.github.childscreentime.core.TimeManager;
import io.github.childscreentime.model.Credit;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private Future<?> listenerTask;
    private DeviceSecurityManager securityManager;
    private volatile boolean isRunning = false;
    
    public static boolean isDiscoveryEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ENABLED, false);
    }
    
    public static void setDiscoveryEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        
        Log.i(TAG, "Discovery enabled changed to: " + enabled);
        
        Intent serviceIntent = new Intent(context, ParentDiscoveryService.class);
        
        // Always stop the service first to prevent port conflicts
        Log.i(TAG, "Stopping any existing ParentDiscoveryService instance...");
        context.stopService(serviceIntent);
        
        if (enabled) {
            // Add a small delay to ensure the service is fully stopped
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            Log.i(TAG, "Starting ParentDiscoveryService...");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
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
        Log.i(TAG, "ParentDiscoveryService onCreate() called");
        
        // Initialize executor service for thread management
        executorService = Executors.newSingleThreadExecutor(); // Single thread for UDP listener
        
        try {
            securityManager = new DeviceSecurityManager(this);
            Log.d(TAG, "DeviceSecurityManager initialized successfully");
        } catch (RuntimeException e) {
            Log.e(TAG, "Security initialization failed", e);
            setDiscoveryEnabled(this, false);
            stopSelf();
            return;
        }
        createNotificationChannel();
        Log.i(TAG, "ParentDiscoveryService onCreate() completed successfully");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "ParentDiscoveryService onStartCommand() called");
        boolean isEnabled = isDiscoveryEnabled(this);
        Log.i(TAG, "Discovery enabled: " + isEnabled);
        
        if (!isEnabled) {
            Log.w(TAG, "Discovery is disabled, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        Log.i(TAG, "Starting foreground service with notification");
        startForeground(NOTIFICATION_ID, createNotification());
        startDiscoveryListener();
        
        Log.i(TAG, "ParentDiscoveryService started successfully");
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopDiscoveryListener();
        
        // Shutdown executor service properly
        if (executorService != null) {
            Log.d(TAG, "Shutting down executor service");
            executorService.shutdown();
            try {
                // Wait up to 5 seconds for tasks to finish
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Executor service did not terminate gracefully, forcing shutdown");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for executor shutdown");
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        Log.i(TAG, "ParentDiscoveryService destroyed");
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
            .setContentText("Ready for parent device discovery")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }
    
    private void startDiscoveryListener() {
        if (isRunning) {
            Log.w(TAG, "Discovery listener already running, stopping existing listener first");
            stopDiscoveryListener();
            // Wait a bit for cleanup
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        Log.i(TAG, "Starting UDP discovery listener on port " + DISCOVERY_PORT);
        
        listenerTask = executorService.submit(() -> {
            int retryCount = 0;
            final int maxRetries = 3;
            
            while (retryCount < maxRetries && !Thread.currentThread().isInterrupted()) {
                try {
                    Log.d(TAG, "Creating UDP socket on port " + DISCOVERY_PORT + " (attempt " + (retryCount + 1) + ")");
                    
                    // Ensure any existing socket is closed first
                    if (socket != null && !socket.isClosed()) {
                        Log.d(TAG, "Closing existing socket before creating new one");
                        socket.close();
                        socket = null;
                    }
                    
                    socket = new DatagramSocket(DISCOVERY_PORT);
                    socket.setBroadcast(true);
                    socket.setSoTimeout(30000); // 30 second timeout for better battery life
                    socket.setReuseAddress(true); // Allow reuse of address
                    isRunning = true;
                    
                    Log.i(TAG, "UDP socket created successfully, listening for packets...");
                    Log.d(TAG, "Socket broadcast enabled: " + socket.getBroadcast());
                    Log.d(TAG, "Socket timeout: " + socket.getSoTimeout() + "ms");
                    
                    break; // Success, exit retry loop
                    
                } catch (java.net.BindException e) {
                    retryCount++;
                    Log.w(TAG, "Port " + DISCOVERY_PORT + " in use, attempt " + retryCount + "/" + maxRetries, e);
                    
                    if (retryCount < maxRetries) {
                        try {
                            // Wait longer between retries
                            Thread.sleep(2000 * retryCount);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    } else {
                        Log.e(TAG, "Failed to bind to port after " + maxRetries + " attempts");
                        isRunning = false;
                        return;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error creating socket", e);
                    isRunning = false;
                    return;
                }
            }
            
            if (!isRunning) {
                Log.e(TAG, "Failed to start discovery listener after retries");
                return;
            }
            
            try {
                while (isRunning && !socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                    try {
                        // Create fresh buffer for each packet to avoid data corruption
                        byte[] buffer = new byte[BUFFER_SIZE];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        
                        Log.v(TAG, "Waiting for UDP packet...");
                        socket.receive(packet); // This will timeout after 30 seconds
                        
                        String message = new String(packet.getData(), 0, packet.getLength());
                        InetAddress senderAddress = packet.getAddress();
                        int senderPort = packet.getPort();
                        
                        Log.i(TAG, "Received UDP packet from " + senderAddress + ":" + senderPort + " - Message: '" + message + "'");
                        
                        handleIncomingMessage(message, senderAddress, senderPort);
                        
                    } catch (java.net.SocketTimeoutException e) {
                        // This is expected - just continue the loop
                        Log.v(TAG, "Socket timeout - no packets received, continuing...");
                        continue;
                    } catch (Exception e) {
                        if (isRunning && !Thread.currentThread().isInterrupted()) {
                            Log.w(TAG, "Error receiving packet", e);
                            // Add small delay before retrying to prevent tight error loop
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    Log.e(TAG, "Failed to start discovery listener", e);
                }
                isRunning = false;
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                Log.i(TAG, "UDP listener task terminated");
            }
        });
        
        Log.i(TAG, "Discovery listener task submitted to executor");
    }
    
    private void stopDiscoveryListener() {
        Log.d(TAG, "Stopping discovery listener...");
        isRunning = false;
        
        // Close socket first to interrupt any blocking receive() calls
        if (socket != null && !socket.isClosed()) {
            Log.d(TAG, "Closing UDP socket");
            socket.close();
        }
        
        // Cancel the listener task if it's running
        if (listenerTask != null && !listenerTask.isDone()) {
            Log.d(TAG, "Cancelling UDP listener task");
            listenerTask.cancel(true); // Interrupt if running
            
            // Wait a bit for the task to finish
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        Log.d(TAG, "Discovery listener stopped");
    }
    
    private void handleIncomingMessage(String message, InetAddress senderAddress, int senderPort) {
        try {
            Log.d(TAG, "Processing message: '" + message + "' from " + senderAddress + ":" + senderPort);
            
            if (DISCOVERY_REQUEST.equals(message)) {
                Log.i(TAG, "Received discovery request, sending response");
                sendResponse(DISCOVERY_RESPONSE, senderAddress, senderPort);
                
            } else if (message.startsWith(COMMAND_PREFIX)) {
                String encryptedCommand = message.substring(COMMAND_PREFIX.length());
                Log.d(TAG, "Received encrypted command, decrypting...");
                
                try {
                    String decryptedCommand = securityManager.decryptMessage(encryptedCommand);
                    Log.d(TAG, "Command decrypted: " + decryptedCommand);
                    String response = processCommand(decryptedCommand);
                    String encryptedResponse = securityManager.encryptMessage(response);
                    
                    Log.d(TAG, "Sending encrypted response");
                    sendResponse(RESPONSE_PREFIX + encryptedResponse, senderAddress, senderPort);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Encryption/decryption failed", e);
                    setDiscoveryEnabled(this, false);
                    stopSelf();
                }
            } else {
                Log.w(TAG, "Unknown message format: " + message);
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error handling message", e);
        }
    }
    
    private String processCommand(String command) {
        try {
            // Parse command and parameters
            String[] parts = command.split(":");
            String baseCommand = parts[0];
            
            switch (baseCommand) {
                case "GET_TIME_LEFT":
                    return getTimeLeftResponse();
                    
                case "LOCK_DEVICE":
                    return lockDevice();
                    
                case "EXTEND_TIME":
                    if (parts.length != 2) {
                        return "ERROR|Invalid EXTEND_TIME format. Use EXTEND_TIME:minutes";
                    }
                    
                    try {
                        int minutes = Integer.parseInt(parts[1]);
                        if (minutes <= 0 || minutes > 1440) { // Max 24 hours
                            return "ERROR|Invalid minutes. Must be 1-1440";
                        }
                        
                        TimeManager.directTimeExtension(this, minutes);
                        return "TIME_EXTENDED|" + minutes + " minutes added";
                        
                    } catch (NumberFormatException e) {
                        return "ERROR|Invalid minutes value";
                    }
                    
                default:
                    return "ERROR|Unknown command";
            }
        } catch (Exception e) {
            Log.w(TAG, "Error processing command: " + command, e);
            return "ERROR|Command processing failed";
        }
    }
    
    private String getTimeLeftResponse() {
        try {
            ScreenTimeApplication app = ScreenTimeApplication.getFromContext(this);
            Credit credit = app.getTodayCredit();
            
            if (credit == null) {
                return "TIME_LEFT|ERROR|No credit data available";
            }
            
            long usedMinutes = app.getDuration();
            long remainingMinutes = Math.max(0, credit.minutes - usedMinutes);
            boolean isBlocked = app.isBlocked();
            
            return String.format("TIME_LEFT|%d|%s|%d", 
                remainingMinutes, 
                isBlocked ? "BLOCKED" : "ACTIVE",
                credit.minutes);
                
        } catch (Exception e) {
            Log.w(TAG, "Error getting time left", e);
            return "TIME_LEFT|ERROR|Failed to get time data";
        }
    }
    
    private String lockDevice() {
        try {
            ScreenTimeApplication app = ScreenTimeApplication.getFromContext(this);
            boolean lockSuccessful = app.lockDevice();
            
            if (lockSuccessful) {
                return "DEVICE_LOCKED|Device is now blocked";
            } else {
                return "DEVICE_LOCKED|ERROR|Failed to lock device";
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error locking device", e);
            return "DEVICE_LOCKED|ERROR|Failed to lock device";
        }
    }
    
    private void sendResponse(String response, InetAddress address, int port) {
        try {
            Log.d(TAG, "Sending response to " + address + ":" + port + " - " + response);
            byte[] responseBytes = response.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(
                responseBytes, responseBytes.length, address, port);
            
            DatagramSocket responseSocket = new DatagramSocket();
            responseSocket.send(responsePacket);
            responseSocket.close();
            
            Log.d(TAG, "Response sent successfully");
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to send response", e);
        }
    }
}
