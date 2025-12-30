# AndrOBD Home Assistant Plugin

This extension plugin allows AndrOBD to publish OBD-II vehicle data to Home Assistant via webhooks or REST API.

## Features

- **Real-time Data Transmission**: Continuously send OBD data to Home Assistant as it's collected
- **SSID-Triggered Mode**: Buffer data and transmit only when connected to a specific WiFi network
- **Secure Communication**: Support for HTTPS and Bearer token authentication
- **Selective Data Publishing**: Choose which OBD data items to publish
- **Comprehensive Data**: Sends key vehicle metrics including:
  - Engine RPM
  - Vehicle speed
  - Battery voltage
  - Coolant temperature
  - Fuel level and pressure
  - Lambda sensor readings
  - Efficiency metrics
  - And many more OBD parameters

## Installation

### From Source

1. Clone this repository
2. Ensure you have the AndrOBD plugin library submodule initialized:
   ```bash
   git submodule init
   git submodule update
   ```
3. Build the project using Android Studio or Gradle:
   ```bash
   ./gradlew assembleRelease
   ```
4. Install the APK on your Android device

### Requirements

- AndrOBD v2.0.0 or higher installed on your device
- Android 4.0.3 (API 15) or higher
- Network connectivity (WiFi or mobile data)

## Configuration

### 1. Setting up Home Assistant Webhook

First, you need to create a webhook automation in Home Assistant:

1. In Home Assistant, go to **Settings** → **Automations & Scenes**
2. Create a new automation
3. Add a **Webhook** trigger and note the webhook ID
4. Your webhook URL will be: `https://your-homeassistant-url:8123/api/webhook/YOUR_WEBHOOK_ID`

Alternatively, you can use the REST API with a long-lived access token:
- URL: `https://your-homeassistant-url:8123/api/states/sensor.your_sensor`
- Generate a long-lived access token in your Home Assistant profile

### 2. Configuring the Plugin

1. Open the **AndrOBD Home Assistant** plugin app
2. Configure the following settings:

#### Required Settings:
- **Enable Home Assistant**: Check to enable the integration
- **Home Assistant URL**: Enter your webhook or API URL
  - Webhook example: `https://homeassistant.local:8123/api/webhook/abc123xyz`
  - API example: `https://homeassistant.local:8123/api/states/sensor.vehicle_data`

#### Optional Settings:
- **Bearer Token**: Long-lived access token (required for API endpoints, optional for webhooks)
- **Transmission Mode**: Choose between:
  - **Real-time**: Send data continuously while connected to OBD
  - **SSID Triggered**: Only send data when connected to specific WiFi network
- **Target WiFi SSID**: The network name to trigger transmission (for SSID-triggered mode)
- **Update Interval**: How often to send data in milliseconds (default: 5000ms = 5 seconds)
- **Data Items**: Select specific OBD parameters to publish (leave empty to publish all)

### 3. Connecting in AndrOBD

1. Open **AndrOBD**
2. Go to **Settings** → **Plugin extensions**
3. Enable the **Home Assistant Publisher** plugin

## Transmission Modes

### Real-time Mode
In real-time mode, the plugin sends OBD data to Home Assistant continuously at the specified update interval. This is ideal for:
- Home setups with VPN or local network access
- Monitoring vehicle data while parked at home
- Real-time dashboards and automations

### SSID-Triggered Mode
In SSID-triggered mode, the plugin buffers data and only transmits when the Android device connects to your specified WiFi network. This is ideal for:
- Vehicles with Android head units
- Mobile phones that travel with the vehicle
- Reducing mobile data usage
- Automatic synchronization when arriving home

## Data Format

The plugin sends data to Home Assistant in JSON format. The first transmission includes configuration and status information:

```json
{
  "config": {
    "ENGINE_RPM": { "class": "frequency", "unit": "rpm" },
    "SPEED": { "class": "speed", "unit": "km/h" },
    "COOLANT_TMP": { "class": "temperature", "unit": "°C" },
    "FUEL": { "class": "none", "unit": "%" }
  },
  "status": {
    "device_id": "androbd_device_12345",
    "app_version": "V1.0.0",
    "device_model": "Android Device",
    "android_version": "13",
    "transmission_mode": "realtime"
  },
  "obd_data": {
    "ENGINE_RPM": "2500",
    "SPEED": "65",
    "COOLANT_TMP": "90",
    "FUEL": "75.5"
  },
  "timestamp": "2024-12-29T12:34:56.789Z"
}
```

Subsequent transmissions contain only the `obd_data` and `timestamp` fields.

## Using Data in Home Assistant

### Creating Sensors from Webhook Data

Create template sensors in your `configuration.yaml`:

```yaml
template:
  - trigger:
      - platform: webhook
        webhook_id: YOUR_WEBHOOK_ID
    sensor:
      - name: "Vehicle RPM"
        state: "{{ trigger.json.obd_data.ENGINE_RPM }}"
        unit_of_measurement: "rpm"
        
      - name: "Vehicle Speed"
        state: "{{ trigger.json.obd_data.SPEED }}"
        unit_of_measurement: "km/h"
        
      - name: "Coolant Temperature"
        state: "{{ trigger.json.obd_data.COOLANT_TMP }}"
        unit_of_measurement: "°C"
        device_class: temperature
```

### Example Automations

**Alert when coolant temperature is high:**
```yaml
automation:
  - alias: "High Coolant Temperature Alert"
    trigger:
      - platform: numeric_state
        entity_id: sensor.coolant_temperature
        above: 100
    action:
      - service: notify.mobile_app
        data:
          message: "Warning: Vehicle coolant temperature is high!"
```

**Track fuel efficiency:**
```yaml
automation:
  - alias: "Log Vehicle Data"
    trigger:
      - platform: webhook
        webhook_id: YOUR_WEBHOOK_ID
    action:
      - service: logbook.log
        data:
          name: "Vehicle Data"
          message: "Speed: {{ trigger.json.obd_data.SPEED }} km/h, RPM: {{ trigger.json.obd_data.ENGINE_RPM }}"
```

## Troubleshooting

### Data not appearing in Home Assistant
1. Check that Home Assistant URL is correct and accessible from your device
2. Verify webhook ID or Bearer token is correct
3. Check network connectivity (try accessing the URL in a browser)
4. Look at AndrOBD logs for connection errors
5. Ensure the plugin is enabled in AndrOBD settings

### SSID-triggered mode not working
1. Ensure the SSID is entered exactly as it appears in WiFi settings
2. Check that WiFi permissions are granted to the plugin
3. Verify you're connected to the correct network

### High data usage
1. Increase the update interval (e.g., from 5000ms to 10000ms)
2. Use SSID-triggered mode instead of real-time mode
3. Consider using a local network or VPN instead of remote access
4. Select only specific data items to publish instead of all

### No data items available to select
1. Connect AndrOBD to your vehicle first
2. Allow data to be collected for a few minutes
3. The plugin will automatically discover available OBD parameters
4. Return to plugin settings to select items

## Security Considerations

- Always use HTTPS URLs to ensure encrypted communication
- Use strong, unique Bearer tokens
- Consider using a VPN for remote access instead of exposing Home Assistant to the internet
- Regularly rotate access tokens
- Monitor Home Assistant logs for unexpected access

## Example Use Cases

1. **Home Garage Display**: Show vehicle stats on a dashboard when parked at home
2. **Maintenance Tracking**: Log engine hours, average RPM, temperature ranges
3. **Fuel Efficiency Monitoring**: Track MPG over time and create efficiency reports
4. **Alert System**: Get notified of engine problems or maintenance needs
5. **Trip Logging**: Automatically log trips when leaving/arriving home
6. **Battery Health Monitoring**: Track battery voltage trends over time

## Development

### Building from Source

1. Clone the repository with submodules:
   ```bash
   git clone --recursive https://github.com/ian-morgan99/AndrOBD-HomeAssistantPlugin.git
   ```

2. Open in Android Studio or build with Gradle:
   ```bash
   cd AndrOBD-HomeAssistantPlugin
   ./gradlew assembleDebug
   ```

### Project Structure

```
HomeAssistantPlugin/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/fr3ts0n/androbd/plugin/homeassistant/
│   │   ├── HomeAssistantPlugin.java    # Main plugin service
│   │   ├── PluginReceiver.java         # Plugin info receiver
│   │   └── SettingsActivity.java       # Settings UI
│   └── res/
│       ├── values/strings.xml          # String resources
│       └── xml/preferences.xml         # Settings UI definition
├── build.gradle                        # Build configuration
└── README.md                           # This file
```

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

## Credits

This plugin was inspired by the [WiCAN firmware](https://github.com/ian-morgan99/wican-fw) Home Assistant integration approach and the [AndrOBD MQTT Publisher](https://github.com/fr3ts0n/AndrOBD-Plugin/tree/master/MqttPublisher) plugin.

## License

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License version 3 or later as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

## Support

- Report issues: [GitHub Issues](https://github.com/ian-morgan99/AndrOBD-HomeAssistantPlugin/issues)
- AndrOBD chat: [Telegram](https://t.me/joinchat/G60ltQv5CCEQ94BZ5yWQbg) or [Matrix](https://matrix.to/#/#AndrOBD:matrix.org)
- Plugin development: [AndrOBD Plugin Framework](https://github.com/fr3ts0n/AndrOBD-Plugin)
