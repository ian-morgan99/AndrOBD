/*
 * (C) Copyright 2024 by AndrOBD contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 */

package com.fr3ts0n.ecu.gui.androbd;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for integrating AndrOBD with Home Assistant
 * Sends OBD data to Home Assistant via webhook/API
 */
public class HomeAssistantService {
    private static final String TAG = "HomeAssistantService";
    
    // Preference keys
    public static final String PREF_HA_ENABLED = "ha_enabled";
    public static final String PREF_HA_URL = "ha_url";
    public static final String PREF_HA_TOKEN = "ha_token";
    public static final String PREF_HA_TRANSMISSION_MODE = "ha_transmission_mode";
    public static final String PREF_HA_SSID = "ha_ssid";
    public static final String PREF_HA_UPDATE_INTERVAL = "ha_update_interval";
    public static final String PREF_HA_DEVICE_ID = "ha_device_id";
    
    // Transmission modes
    public static final String MODE_REALTIME = "realtime";
    public static final String MODE_SSID_TRIGGERED = "ssid_triggered";
    
    // Default values
    private static final int DEFAULT_UPDATE_INTERVAL = 5000; // 5 seconds
    
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService executor;
    private final Handler handler;
    
    private boolean enabled = false;
    private String webhookUrl = "";
    private String bearerToken = "";
    private String transmissionMode = MODE_REALTIME;
    private String targetSsid = "";
    private int updateInterval = DEFAULT_UPDATE_INTERVAL;
    private String deviceId = "";
    
    private final Map<String, Object> dataBuffer = new HashMap<>();
    private boolean configSent = false;
    private long lastUpdateTime = 0;
    
    private Runnable updateTask;
    
    public HomeAssistantService(Context context) {
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.executor = Executors.newSingleThreadExecutor();
        this.handler = new Handler(Looper.getMainLooper());
        
        loadPreferences();
    }
    
    /**
     * Load preferences from shared preferences
     */
    private void loadPreferences() {
        enabled = prefs.getBoolean(PREF_HA_ENABLED, false);
        webhookUrl = prefs.getString(PREF_HA_URL, "");
        bearerToken = prefs.getString(PREF_HA_TOKEN, "");
        transmissionMode = prefs.getString(PREF_HA_TRANSMISSION_MODE, MODE_REALTIME);
        targetSsid = prefs.getString(PREF_HA_SSID, "");
        
        // Parse update interval from string preference
        String intervalStr = prefs.getString(PREF_HA_UPDATE_INTERVAL, String.valueOf(DEFAULT_UPDATE_INTERVAL));
        try {
            updateInterval = Integer.parseInt(intervalStr);
        } catch (NumberFormatException e) {
            updateInterval = DEFAULT_UPDATE_INTERVAL;
        }
        
        // Load or generate device ID
        deviceId = prefs.getString(PREF_HA_DEVICE_ID, "");
        if (deviceId.isEmpty()) {
            deviceId = generateDeviceId();
            // Persist the generated device ID
            prefs.edit().putString(PREF_HA_DEVICE_ID, deviceId).apply();
        }
        
        Log.d(TAG, "Preferences loaded - Enabled: " + enabled + ", Mode: " + transmissionMode);
    }
    
    /**
     * Generate a unique device ID
     */
    private String generateDeviceId() {
        return "androbd_" + android.os.Build.MODEL.replaceAll("\\s+", "_").toLowerCase(Locale.ROOT) + "_" + 
               System.currentTimeMillis();
    }
    
    /**
     * Start the Home Assistant service
     */
    public void start() {
        loadPreferences();
        
        if (!enabled) {
            Log.d(TAG, "Home Assistant service is disabled");
            return;
        }
        
        if (webhookUrl.isEmpty()) {
            Log.w(TAG, "Home Assistant webhook URL is not configured");
            return;
        }
        
        Log.i(TAG, "Starting Home Assistant service");
        
        // Schedule periodic updates for realtime mode
        if (MODE_REALTIME.equals(transmissionMode)) {
            scheduleUpdates();
        }
        
        // Reset config sent flag
        configSent = false;
    }
    
    /**
     * Stop the Home Assistant service
     */
    public void stop() {
        Log.i(TAG, "Stopping Home Assistant service");
        
        if (updateTask != null) {
            handler.removeCallbacks(updateTask);
            updateTask = null;
        }
        
        dataBuffer.clear();
        configSent = false;
    }
    
    /**
     * Schedule periodic updates
     */
    private void scheduleUpdates() {
        if (updateTask != null) {
            handler.removeCallbacks(updateTask);
        }
        
        updateTask = new Runnable() {
            @Override
            public void run() {
                if (shouldTransmit()) {
                    transmitData();
                }
                handler.postDelayed(this, updateInterval);
            }
        };
        
        handler.postDelayed(updateTask, updateInterval);
    }
    
    /**
     * Check if data should be transmitted based on current conditions
     */
    private boolean shouldTransmit() {
        if (!enabled || webhookUrl.isEmpty()) {
            return false;
        }
        
        // Check if enough time has passed since last update
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < updateInterval) {
            return false;
        }
        
        // For SSID-triggered mode, check if connected to target SSID
        if (MODE_SSID_TRIGGERED.equals(transmissionMode)) {
            return isConnectedToTargetSsid();
        }
        
        // For realtime mode, check if we have network connectivity
        return hasNetworkConnectivity();
    }
    
    /**
     * Check if connected to target SSID
     */
    private boolean isConnectedToTargetSsid() {
        if (targetSsid.isEmpty()) {
            return false;
        }
        
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        
        if (wifiManager == null) {
            return false;
        }
        
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            return false;
        }
        
        String currentSsid = wifiInfo.getSSID();
        // Remove quotes from SSID
        currentSsid = currentSsid.replace("\"", "");
        
        Log.d(TAG, "Current SSID: " + currentSsid + ", Target SSID: " + targetSsid);
        return currentSsid.equals(targetSsid);
    }
    
    /**
     * Check if device has network connectivity
     */
    private boolean hasNetworkConnectivity() {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (cm == null) {
            return false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && 
                   (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }
    
    /**
     * Update OBD data value
     */
    public void updateData(String key, Object value) {
        if (!enabled) {
            return;
        }
        
        synchronized (dataBuffer) {
            dataBuffer.put(key, value);
        }
        
        // For realtime mode with frequent updates, transmit is handled by scheduled task
        // For SSID mode, check if we should transmit now
        if (MODE_SSID_TRIGGERED.equals(transmissionMode) && shouldTransmit()) {
            transmitData();
        }
    }
    
    /**
     * Transmit data to Home Assistant
     */
    private void transmitData() {
        if (dataBuffer.isEmpty()) {
            return;
        }
        
        Map<String, Object> dataToSend;
        synchronized (dataBuffer) {
            dataToSend = new HashMap<>(dataBuffer);
        }
        
        executor.execute(() -> sendToHomeAssistant(dataToSend));
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Send data to Home Assistant via HTTP POST
     */
    private void sendToHomeAssistant(Map<String, Object> data) {
        HttpURLConnection connection = null;
        
        try {
            URL url = new URL(webhookUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "AndrOBD-HomeAssistant/1.0");
            
            if (!bearerToken.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }
            
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            // Build JSON payload
            JSONObject payload = buildPayload(data);
            
            // Send request
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK || 
                responseCode == HttpURLConnection.HTTP_CREATED) {
                
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                Log.d(TAG, "Data sent successfully to Home Assistant");
            } else {
                Log.w(TAG, "Failed to send data to Home Assistant: " + responseCode);
            }
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error sending data to Home Assistant", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Build JSON payload for Home Assistant
     */
    private JSONObject buildPayload(Map<String, Object> data) throws JSONException {
        JSONObject payload = new JSONObject();
        
        // Include config and status on first transmission
        if (!configSent) {
            payload.put("config", buildConfigObject(data));
            payload.put("status", buildStatusObject());
            configSent = true;
        }
        
        // Add OBD data
        JSONObject obdData = new JSONObject();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            obdData.put(entry.getKey(), entry.getValue());
        }
        payload.put("obd_data", obdData);
        
        // Add timestamp
        payload.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", 
                Locale.US).format(new Date()));
        
        return payload;
    }
    
    /**
     * Build config object for initial payload
     */
    private JSONObject buildConfigObject(Map<String, Object> data) throws JSONException {
        JSONObject config = new JSONObject();
        
        // Add metadata for each data field
        for (String key : data.keySet()) {
            JSONObject fieldConfig = new JSONObject();
            fieldConfig.put("class", getDeviceClass(key));
            fieldConfig.put("unit", getUnit(key));
            config.put(key, fieldConfig);
        }
        
        return config;
    }
    
    /**
     * Build status object
     */
    private JSONObject buildStatusObject() throws JSONException {
        JSONObject status = new JSONObject();
        
        status.put("device_id", deviceId);
        status.put("app_version", context.getString(R.string.app_version));
        status.put("device_model", Build.MODEL);
        status.put("android_version", Build.VERSION.RELEASE);
        status.put("transmission_mode", transmissionMode);
        
        return status;
    }
    
    /**
     * Get Home Assistant device class for a given OBD parameter
     */
    private String getDeviceClass(String key) {
        String keyLower = key.toLowerCase(Locale.ROOT);
        
        if (keyLower.contains("rpm") || keyLower.contains("engine")) {
            return "frequency";
        } else if (keyLower.contains("speed")) {
            return "speed";
        } else if (keyLower.contains("temp") || keyLower.contains("temperature")) {
            return "temperature";
        } else if (keyLower.contains("pressure")) {
            return "pressure";
        } else if (keyLower.contains("voltage") || keyLower.contains("battery")) {
            return "voltage";
        } else if (keyLower.contains("fuel") || keyLower.contains("level")) {
            return "none";
        }
        
        return "none";
    }
    
    /**
     * Get unit for a given OBD parameter
     */
    private String getUnit(String key) {
        String keyLower = key.toLowerCase(Locale.ROOT);
        
        if (keyLower.contains("rpm")) {
            return "rpm";
        } else if (keyLower.contains("speed")) {
            return "km/h";
        } else if (keyLower.contains("temp") || keyLower.contains("temperature")) {
            return "°C";
        } else if (keyLower.contains("pressure")) {
            return "kPa";
        } else if (keyLower.contains("voltage")) {
            return "V";
        } else if (keyLower.contains("fuel") || keyLower.contains("level")) {
            return "%";
        } else if (keyLower.contains("lambda")) {
            return "λ";
        } else if (keyLower.contains("mpg")) {
            return "mpg";
        }
        
        return "";
    }
    
    /**
     * Check if Home Assistant service is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Set whether Home Assistant service is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        prefs.edit().putBoolean(PREF_HA_ENABLED, enabled).apply();
        
        if (enabled) {
            start();
        } else {
            stop();
        }
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        stop();
        executor.shutdown();
    }
}
