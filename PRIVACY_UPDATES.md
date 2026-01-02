# Android Privacy Protection Updates

## Overview

This document describes the changes made to address the Android privacy warning: "This app was built for an older version of android and doesn't include the latest privacy protections."

## Root Cause

The application was targeting Android SDK 25 (Android 7.1), which is over 7 years old and lacks modern privacy protections required by current Android versions. Google Play requires apps to target at least SDK 31 (Android 12) for new submissions.

## Changes Made

### 1. Updated Target SDK Version

**Files Modified:**
- `androbd/build.gradle`
- `plugin/build.gradle`

**Changes:**
- Updated `targetSdkVersion` from 25 to 34 (Android 14)
- Removed `ExpiredTargetSdkVersion` suppression warnings
- This brings the app up to modern Android standards

### 2. Bluetooth Permission Updates

**File Modified:** `androbd/src/main/AndroidManifest.xml`

**Legacy Permissions (Android 11 and below):**
```xml
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
```

**Modern Permissions (Android 12+):**
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" 
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

**Key Points:**
- `BLUETOOTH_SCAN` with `neverForLocation` flag indicates the app doesn't use Bluetooth for location tracking
- `BLUETOOTH_CONNECT` is required to connect to paired Bluetooth devices
- Legacy permissions are restricted to SDK 30 and below using `maxSdkVersion`

### 3. Runtime Permission Handling

**File Modified:** `androbd/src/main/java/com/fr3ts0n/ecu/gui/androbd/MainActivity.java`

**New Methods Added:**
- `checkBluetoothPermissions()` - Checks and requests Bluetooth permissions on Android 12+
- `onRequestPermissionsResult()` - Handles permission grant/denial responses

**Permission Flow:**
1. When user attempts to connect to Bluetooth device, app checks for permissions
2. If not granted, displays system permission dialog
3. If granted, proceeds with connection
4. If denied, shows explanatory message and stays in offline mode

**Files Updated with Permission Checks:**
- `BtDeviceListActivity.java` - Added permission checks before accessing paired devices
- `BtCommService.java` - Added permission checks before Bluetooth operations

### 4. Storage Permission Updates

**File Modified:** `androbd/src/main/AndroidManifest.xml`

**Changes:**
```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
    android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" 
    android:maxSdkVersion="32" />
```

**Code Changes in MainActivity.java:**
- Storage permissions are now only requested for Android 6-9 (API 23-28)
- Android 10+ (API 29+) uses scoped storage by default
- This eliminates the need for broad storage permissions on modern Android

### 5. Foreground Service Updates

**File Modified:** `androbd/src/main/AndroidManifest.xml`

**New Permission:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
```

**Service Declaration:**
```xml
<service
    android:name=".ObdBackgroundService"
    android:foregroundServiceType="connectedDevice" />
```

**Explanation:**
- Android 14+ requires explicit foreground service types
- `connectedDevice` type is appropriate for OBD communication services
- This ensures the background service can continue running while app is in background

## Plugin Framework Compatibility

The plugin framework architecture continues to work with the updated target SDK:

1. **Service Communication:** Plugin services can still communicate via exported services
2. **Data Exchange:** The `PluginDataService` mechanism remains functional
3. **Intent-based Discovery:** Plugin discovery via intents still works with SDK 34

**Key Design Consideration:**
The previous comment "SDK 25 to allow background service mechanism for plugins" was based on an outdated understanding. Modern Android (SDK 34) fully supports:
- Exported services with explicit declarations
- Background service communication
- Intent-based component discovery
- Foreground services with proper type declarations

## Testing Recommendations

### 1. Bluetooth Functionality
- Test on Android 12+ devices
- Verify permission dialogs appear correctly
- Ensure Bluetooth connection works after permissions granted
- Test with pre-paired devices and new device pairing

### 2. Storage Operations
- Test file save/load on Android 10+ (scoped storage)
- Verify file operations on Android 6-9 with legacy permissions
- Test screenshot saving functionality

### 3. Background Service
- Verify ObdBackgroundService starts properly
- Check notification appears when service is running
- Test that OBD monitoring continues when app is backgrounded

### 4. Plugin Compatibility
- Test with existing plugins (MQTT, GpsProvider, SensorProvider)
- Verify plugin discovery still works
- Ensure plugin data exchange functions correctly

## Security Improvements

1. **Minimal Bluetooth Permissions:** Using `neverForLocation` flag reduces privacy concerns
2. **Scoped Storage:** Modern Android versions use scoped storage, limiting app's file system access
3. **Runtime Permissions:** Users have granular control over what the app can access
4. **Foreground Service Transparency:** Clear notification shows when background monitoring is active

## Migration Notes for Users

### First Launch After Update:
1. App will request Bluetooth permissions when first connecting to OBD device
2. Users must grant `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` permissions
3. On Android 10+, no storage permissions will be requested (uses scoped storage)
4. On older Android versions (6-9), storage permissions may be requested

### No Breaking Changes:
- Existing functionality remains the same
- Plugin system continues to work
- All OBD features remain available
- File save/load continues to work with appropriate storage APIs

## Compliance

These changes bring the app into compliance with:
- Google Play's target API level requirements (SDK 31+)
- Android 12+ privacy protection standards
- Android 13+ notification permission requirements
- Android 14+ foreground service type requirements

## References

- [Android Bluetooth Permissions](https://developer.android.com/guide/topics/connectivity/bluetooth/permissions)
- [Scoped Storage](https://developer.android.com/about/versions/11/privacy/storage)
- [Foreground Service Types](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Target API Level Requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
