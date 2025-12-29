# Home Assistant Integration

AndrOBD now includes built-in integration with Home Assistant, allowing you to send real-time OBD-II data directly to your Home Assistant server.

## Features

- **Real-time Data Transmission**: Continuously send OBD data to Home Assistant as it's collected
- **SSID-Triggered Mode**: Buffer data and transmit only when connected to a specific WiFi network
- **Secure Communication**: Support for HTTPS and Bearer token authentication
- **Comprehensive Data**: Sends key vehicle metrics including:
  - Engine RPM
  - Vehicle speed
  - Battery voltage
  - Coolant temperature
  - Fuel level and pressure
  - Lambda sensor readings
  - Efficiency metrics
  - And many more OBD parameters

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

### 2. Configuring AndrOBD

1. Open AndrOBD
2. Go to **Settings** (three-dot menu → Settings)
3. Scroll to **Home Assistant** section
4. Configure the following:

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

## Transmission Modes

### Real-time Mode
In real-time mode, AndrOBD sends OBD data to Home Assistant continuously at the specified update interval. This is ideal for:
- Home setups with VPN or local network access
- Monitoring vehicle data while parked at home
- Real-time dashboards and automations

### SSID-Triggered Mode
In SSID-triggered mode, AndrOBD buffers data and only transmits when the Android device connects to your specified WiFi network. This is ideal for:
- Vehicles with Android head units
- Mobile phones that travel with the vehicle
- Reducing mobile data usage
- Automatic synchronization when arriving home

## Data Format

AndrOBD sends data to Home Assistant in JSON format. The first transmission includes configuration and status information:

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
    "app_version": "V2.6.16",
    "device_model": "Android Device",
    "android_version": "13",
    "transmission_mode": "realtime"
  },
  "obd_data": {
    "ENGINE_RPM": 2500,
    "SPEED": 65,
    "COOLANT_TMP": 90,
    "FUEL": 75.5
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

### SSID-triggered mode not working
1. Ensure the SSID is entered exactly as it appears in WiFi settings
2. Check that WiFi permissions are granted to AndrOBD
3. Verify you're connected to the correct network

### High data usage
1. Increase the update interval (e.g., from 5000ms to 10000ms)
2. Use SSID-triggered mode instead of real-time mode
3. Consider using a local network or VPN instead of remote access

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

## Credits

This integration was inspired by the [WiCAN firmware](https://github.com/ian-morgan99/wican-fw) Home Assistant integration approach.
