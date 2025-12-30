# Home Assistant Plugin Development Guide

This document explains how the Home Assistant functionality was converted from a built-in feature to a standalone AndrOBD plugin.

## Why Convert to Plugin?

The Home Assistant integration was initially added as a built-in feature in AndrOBD. After review, it was decided to convert it to a plugin for several reasons:

1. **Modularity**: Plugins keep the core app lean and allow users to install only features they need
2. **Maintainability**: Separate codebase makes updates and bug fixes easier
3. **Community Sharing**: Plugins can be shared independently and updated on their own schedule
4. **Consistency**: Aligns with existing integrations like MQTT, GPS, and Sensor providers which are all plugins

## Plugin Architecture

AndrOBD plugins follow a specific architecture defined by the AndrOBD plugin framework:

### Key Components

1. **Plugin Service**: Main service that extends `com.fr3ts0n.androbd.plugin.Plugin`
   - Implements `ConfigurationHandler` for settings
   - Implements `ActionHandler` for manual actions
   - Implements `DataReceiver` to receive OBD data from AndrOBD

2. **PluginReceiver**: Broadcast receiver that responds to plugin discovery
   - Extends `PluginInfoBroadcastReceiver`
   - Provides plugin metadata to AndrOBD

3. **SettingsActivity**: Configuration UI for the plugin
   - Uses Android PreferenceFragment
   - Allows users to configure plugin behavior

4. **AndroidManifest**: Declares plugin components and permissions
   - Service and receiver must be properly declared
   - Required permissions (Internet, Network State, WiFi State)

## Conversion Process

### What Was Removed from AndrOBD Core

The following components were removed from the main AndrOBD app:

1. **HomeAssistantService.java**: Core service class for Home Assistant integration
2. **MainActivity integration**: 
   - Service initialization in onCreate()
   - Service cleanup in onDestroy()
   - Data forwarding in onDataUpdate()
   - Settings change handling
3. **Settings UI**: 
   - Home Assistant PreferenceScreen from settings.xml
   - All Home Assistant-related strings and arrays
4. **Documentation**: HOME_ASSISTANT.md moved to plugin README

### What Was Added to the Plugin

The plugin includes these new/adapted components:

1. **HomeAssistantPlugin.java**: Adapted from HomeAssistantService
   - Now extends Plugin base class
   - Implements plugin interfaces
   - Manages data buffering and transmission
   - Handles network connectivity checks

2. **PluginReceiver.java**: New component for plugin discovery
   - Responds to AndrOBD's plugin identification broadcast

3. **SettingsActivity.java**: New configuration UI
   - Preference-based settings interface
   - Dynamic data item selection
   - Real-time settings updates

4. **Resources**: Complete resource definitions
   - Strings for all UI elements
   - Preference XML layouts
   - Styles and themes

### Key Adaptations

#### Data Reception
- **Before**: MainActivity directly called `homeAssistantService.updateData()`
- **After**: Plugin implements `DataReceiver.onDataUpdate()` callback

#### Configuration
- **Before**: Settings integrated in main app's PreferenceScreen
- **After**: Separate SettingsActivity with its own PreferenceScreen

#### Lifecycle Management
- **Before**: Tied to MainActivity lifecycle
- **After**: Independent service with its own lifecycle

#### Data Item Selection
- **Before**: All OBD data automatically sent
- **After**: Users can select specific data items to publish

## Building and Testing

### Prerequisites

1. AndrOBD plugin framework (as a Git submodule or local project)
2. Android SDK with build tools
3. Gradle build system

### Build Steps

1. Ensure the plugin framework is available:
   ```bash
   # If part of AndrOBD-Plugin repository
   git submodule init
   git submodule update
   ```

2. Build the plugin:
   ```bash
   cd HomeAssistantPlugin
   ../gradlew assembleDebug  # or assembleRelease
   ```

3. Install on device:
   ```bash
   adb install -r build/outputs/apk/debug/HomeAssistantPlugin-debug.apk
   ```

### Testing Approach

1. **Manual Testing**:
   - Install both AndrOBD and the Home Assistant plugin
   - Enable the plugin in AndrOBD settings
   - Configure Home Assistant URL and credentials
   - Connect to vehicle and verify data transmission
   - Test both real-time and SSID-triggered modes

2. **Home Assistant Verification**:
   - Set up a webhook in Home Assistant
   - Monitor webhook triggers
   - Verify JSON payload structure
   - Create sensors from received data

3. **Network Scenarios**:
   - Test with WiFi connectivity
   - Test with mobile data
   - Test SSID-triggered mode with correct/incorrect WiFi
   - Test connection failures and retries

## Deployment

### Release Process

1. **Version Numbering**: Follow semantic versioning (MAJOR.MINOR.PATCH)
2. **Build Release APK**: Use `assembleRelease` task
3. **Sign APK**: Use Android signing configuration
4. **Create GitHub Release**: Tag and publish release with APK
5. **Documentation**: Update README with release notes

### Distribution Options

1. **GitHub Releases**: Direct APK downloads
2. **F-Droid**: Submit to F-Droid repository (like MQTT plugin)
3. **Google Play**: Optional, for wider distribution
4. **AndrOBD Plugin List**: Add to README.md in main AndrOBD repo

## Plugin Framework Reference

### Plugin Base Class

```java
public abstract class Plugin extends Service {
    public abstract PluginInfo getPluginInfo();
}
```

### Key Interfaces

```java
// For plugins that have configuration UI
public interface ConfigurationHandler {
    void performConfigure();
}

// For plugins that perform actions
public interface ActionHandler {
    void performAction();
}

// For plugins that receive data from AndrOBD
public interface DataReceiver {
    void onDataListUpdate(String[] dataItems);
    void onDataUpdate(String key, String value);
}
```

### PluginInfo Structure

```java
public class PluginInfo {
    public PluginInfo(String name, 
                      Class<?> serviceClass,
                      String description,
                      String copyright,
                      String license,
                      String url)
}
```

## Future Enhancements

Possible improvements for the Home Assistant plugin:

1. **Auto-discovery**: Automatically discover Home Assistant on local network
2. **SSL Certificate Validation**: Add option for custom CA certificates
3. **Batch Optimization**: Group multiple updates into single request
4. **Retry Logic**: Implement exponential backoff for failed transmissions
5. **Status Display**: Show connection status in plugin UI
6. **Data Filtering**: More advanced filtering options (by value, rate of change)
7. **Custom Topics**: Allow custom Home Assistant entity naming

## Resources

- [AndrOBD Plugin Framework](https://github.com/fr3ts0n/AndrOBD-libplugin)
- [AndrOBD Plugin Development](https://github.com/fr3ts0n/AndrOBD-Plugin)
- [MQTT Plugin Example](https://github.com/fr3ts0n/AndrOBD-Plugin/tree/master/MqttPublisher)
- [Home Assistant Webhook Documentation](https://www.home-assistant.io/docs/automation/trigger/#webhook-trigger)
- [AndrOBD Wiki](https://github.com/fr3ts0n/AndrOBD/wiki)

## Support

For questions or issues:
- AndrOBD Telegram: https://t.me/joinchat/G60ltQv5CCEQ94BZ5yWQbg
- AndrOBD Matrix: https://matrix.to/#/#AndrOBD:matrix.org
- GitHub Issues: https://github.com/ian-morgan99/AndrOBD-HomeAssistantPlugin/issues
