package com.fr3ts0n.androbd.plugin.homeassistant;

import android.content.Context;
import android.content.Intent;
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

import com.fr3ts0n.androbd.plugin.Plugin;
import com.fr3ts0n.androbd.plugin.PluginInfo;

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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AndrOBD Home Assistant publishing plugin
 * <p>
 * Publish AndrOBD measurements to Home Assistant via webhook or API
 */
public class HomeAssistantPlugin
        extends Plugin
        implements Plugin.ConfigurationHandler,
                   Plugin.ActionHandler,
                   Plugin.DataReceiver,
                   SharedPreferences.OnSharedPreferenceChangeListener
{
    private static final String TAG = "HomeAssistantPlugin";
    
    static final PluginInfo myInfo = new PluginInfo("HomeAssistantPublisher",
            HomeAssistantPlugin.class,
            "Home Assistant publish AndrOBD measurements",
            "Copyright (C) 2024 by AndrOBD contributors",
            "GPLV3+",
            "https://github.com/ian-morgan99/AndrOBD-HomeAssistantPlugin"
    );
    
    // Preference keys
    static final String PREF_HA_ENABLED = "ha_enabled";
    static final String PREF_HA_URL = "ha_url";
    static final String PREF_HA_TOKEN = "ha_token";
    static final String PREF_HA_TRANSMISSION_MODE = "ha_transmission_mode";
    static final String PREF_HA_SSID = "ha_ssid";
    static final String PREF_HA_UPDATE_INTERVAL = "ha_update_interval";
    static final String PREF_HA_DEVICE_ID = "ha_device_id";
    static final String ITEMS_SELECTED = "data_items";
    static final String ITEMS_KNOWN = "known_items";
    
    // Transmission modes
    public static final String MODE_REALTIME = "realtime";
    public static final String MODE_SSID_TRIGGERED = "ssid_triggered";
    
    // Default values
    private static final int DEFAULT_UPDATE_INTERVAL = 5000; // 5 seconds
    
    private SharedPreferences prefs;
    private final ExecutorService executor;
    private final Handler handler;
    
    private boolean enabled = false;
    private String webhookUrl = "";
    private String bearerToken = "";
    private String transmissionMode = MODE_REALTIME;
    private String targetSsid = "";
    private int updateInterval = DEFAULT_UPDATE_INTERVAL;
    private String deviceId = "";
    
    private final Map<String, String> dataBuffer = new HashMap<>();
    private boolean configSent = false;
    private long lastUpdateTime = 0;
    
    private Runnable updateTask;
    
    /**
     * Set of items which are selected to be published
     */
    protected static HashSet<String> mSelectedItems = new HashSet<>();
    
    /**
     * Set of items which are known to the plugin
     */
    protected static HashSet<String> mKnownItems = new HashSet<>();
    
    public HomeAssistantPlugin() {
        this.executor = Executors.newSingleThreadExecutor();
        this.handler = new Handler(Looper.getMainLooper());
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        loadPreferences();
    }
    
    @Override
    public void onDestroy() {
        stop();
        executor.shutdown();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }
    
    @Override
    public PluginInfo getPluginInfo() {
        return myInfo;
    }
    
    @Override
    public void performConfigure() {
        Intent cfgIntent = new Intent(this, SettingsActivity.class);
        cfgIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(cfgIntent);
    }
    
    @Override
    public void performAction() {
        // Manual trigger to send data immediately
        if (enabled && !dataBuffer.isEmpty()) {
            transmitData();
        }
    }
    
    @Override
    public void onDataListUpdate(String[] strings) {
        // Update list of known data items
        for (String item : strings) {
            mKnownItems.add(item);
        }
        
        // Store known items in preferences
        prefs.edit().putStringSet(ITEMS_KNOWN, mKnownItems).apply();
    }
    
    @Override
    public void onDataUpdate(String key, String value) {
        if (!enabled) {
            return;
        }
        
        // Only publish selected items (if any are selected)
        if (!mSelectedItems.isEmpty() && !mSelectedItems.contains(key)) {
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
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null || 
            PREF_HA_ENABLED.equals(key) ||
            PREF_HA_URL.equals(key) ||
            PREF_HA_TOKEN.equals(key) ||
            PREF_HA_TRANSMISSION_MODE.equals(key) ||
            PREF_HA_SSID.equals(key) ||
            PREF_HA_UPDATE_INTERVAL.equals(key)) {
            
            stop();
            loadPreferences();
            start();
        }
        
        if (ITEMS_SELECTED.equals(key)) {
            Set<String> selectedSet = prefs.getStringSet(ITEMS_SELECTED, new HashSet<>());
            mSelectedItems = new HashSet<>(selectedSet);
        }
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
            prefs.edit().putString(PREF_HA_DEVICE_ID, deviceId).apply();
        }
        
        // Load selected items
        Set<String> selectedSet = prefs.getStringSet(ITEMS_SELECTED, new HashSet<>());
        mSelectedItems = new HashSet<>(selectedSet);
        
        // Load known items
        Set<String> knownSet = prefs.getStringSet(ITEMS_KNOWN, new HashSet<>());
        mKnownItems = new HashSet<>(knownSet);
        
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
        
        WifiManager wifiManager = (WifiManager) getApplicationContext()
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
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        
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
     * Transmit data to Home Assistant
     */
    private void transmitData() {
        if (dataBuffer.isEmpty()) {
            return;
        }
        
        Map<String, String> dataToSend;
        synchronized (dataBuffer) {
            dataToSend = new HashMap<>(dataBuffer);
        }
        
        executor.execute(() -> sendToHomeAssistant(dataToSend));
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Send data to Home Assistant via HTTP POST
     */
    private void sendToHomeAssistant(Map<String, String> data) {
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
    private JSONObject buildPayload(Map<String, String> data) throws JSONException {
        JSONObject payload = new JSONObject();
        
        // Include config and status on first transmission
        if (!configSent) {
            payload.put("config", buildConfigObject(data));
            payload.put("status", buildStatusObject());
            configSent = true;
        }
        
        // Add OBD data
        JSONObject obdData = new JSONObject();
        for (Map.Entry<String, String> entry : data.entrySet()) {
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
    private JSONObject buildConfigObject(Map<String, String> data) throws JSONException {
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
        status.put("app_version", "1.0.0"); // Plugin version
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
}
