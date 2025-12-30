# Issue: Set up AndrOBD Home Assistant Plugin Repository

## Overview
This issue tracks the setup and migration of the Home Assistant plugin from the main AndrOBD repository to this dedicated plugin repository.

## Background
The Home Assistant integration was initially added as a built-in feature to AndrOBD. After review, it was decided to convert it to a standalone plugin following the established plugin architecture (similar to the MQTT plugin). All plugin code has been developed and is ready to be migrated from the main repository PR.

## Prerequisites
- Git installed
- Android SDK and build tools
- Access to this repository: https://github.com/ian-morgan99/AndrOBD-Plugin-Home-Assistant
- Source files from: https://github.com/ian-morgan99/AndrOBD/tree/copilot/revert-changes-to-plugin/HomeAssistantPlugin

## Step-by-Step Migration Instructions

### 1. Clone the New Repository
```bash
git clone https://github.com/ian-morgan99/AndrOBD-Plugin-Home-Assistant.git
cd AndrOBD-Plugin-Home-Assistant
```

### 2. Add Plugin Framework Submodule
```bash
git submodule add https://github.com/fr3ts0n/AndrOBD-libplugin.git plugin
git submodule init
git submodule update
```

### 3. Copy Plugin Files from Source
Copy the entire `HomeAssistantPlugin/` directory from the source PR to the root of this repository:

**Files to copy:**
```
HomeAssistantPlugin/.gitignore                                    → .gitignore (merge with existing)
HomeAssistantPlugin/PLUGIN_GUIDE.md                               → PLUGIN_GUIDE.md
HomeAssistantPlugin/README.md                                     → README.md
HomeAssistantPlugin/build.gradle                                  → build.gradle
HomeAssistantPlugin/proguard-rules.txt                            → proguard-rules.txt
HomeAssistantPlugin/src/main/AndroidManifest.xml                  → src/main/AndroidManifest.xml
HomeAssistantPlugin/src/main/java/.../**                          → src/main/java/.../
HomeAssistantPlugin/src/main/res/**                               → src/main/res/
```

**Directory structure after copying:**
```
AndrOBD-Plugin-Home-Assistant/
├── .gitignore
├── README.md
├── PLUGIN_GUIDE.md
├── build.gradle
├── proguard-rules.txt
├── plugin/                          (submodule)
├── src/
│   └── main/
│       ├── AndroidManifest.xml
│       ├── java/com/fr3ts0n/androbd/plugin/homeassistant/
│       │   ├── HomeAssistantPlugin.java
│       │   ├── PluginReceiver.java
│       │   └── SettingsActivity.java
│       └── res/
│           ├── values/
│           │   ├── arrays.xml
│           │   ├── strings.xml
│           │   └── styles.xml
│           └── xml/
│               └── preferences.xml
└── settings.gradle                  (create new)
```

### 4. Create Root Configuration Files

**Create `settings.gradle`:**
```gradle
include ':plugin'
```

**Create `gradle.properties` (if not exists):**
```properties
android.useAndroidX=false
android.enableJetifier=false
```

**Create `gradlew` wrapper files (optional but recommended):**
```bash
# If you have gradle installed locally:
gradle wrapper --gradle-version 8.4
```

### 5. Update build.gradle
Ensure the `build.gradle` file has the correct dependencies:

```gradle
apply plugin: 'com.android.application'

android {
    compileSdk 34
    defaultConfig {
        applicationId 'com.fr3ts0n.androbd.plugin.homeassistant'
        minSdkVersion 15
        targetSdkVersion 25
        vectorDrawables.useSupportLibrary = true
        versionCode 10000
        versionName 'V1.0.0'
    }

    applicationVariants.configureEach { variant ->
        variant.resValue "string", "app_version", variant.versionName
    }

    buildTypes {
        release {
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'
        }
    }

    productFlavors {
    }
    namespace 'com.fr3ts0n.androbd.plugin.homeassistant'
    lint {
        abortOnError false
    }
}

dependencies {
    implementation project(':plugin')
}
```

### 6. Add Android Project Files

**Create `build.gradle` in root (if needed):**
```gradle
// Top-level build file
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.3.1'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

### 7. Add App Icon Resources
Create icon resources in `src/main/res/mipmap-*/` directories or copy from the MQTT plugin example.

**Minimum required:**
```
src/main/res/mipmap-hdpi/ic_launcher.png
src/main/res/mipmap-mdpi/ic_launcher.png
src/main/res/mipmap-xhdpi/ic_launcher.png
src/main/res/mipmap-xxhdpi/ic_launcher.png
src/main/res/mipmap-xxxhdpi/ic_launcher.png
```

### 8. Build and Test

**Build debug version:**
```bash
./gradlew assembleDebug
```

**Build release version:**
```bash
./gradlew assembleRelease
```

**Expected output:**
- Debug APK: `build/outputs/apk/debug/AndrOBD-Plugin-Home-Assistant-debug.apk`
- Release APK: `build/outputs/apk/release/AndrOBD-Plugin-Home-Assistant-release.apk`

### 9. Test the Plugin

**Installation:**
1. Install AndrOBD on your Android device (v2.0.0 or higher)
2. Install the Home Assistant plugin APK
3. Open AndrOBD → Settings → Plugin extensions
4. Enable "Home Assistant Publisher"
5. Tap the plugin to open settings
6. Configure Home Assistant URL and other settings

**Test scenarios:**
- [ ] Plugin appears in AndrOBD plugin list
- [ ] Settings activity opens correctly
- [ ] Data items are discovered after connecting to vehicle
- [ ] Real-time mode sends data to Home Assistant
- [ ] SSID-triggered mode works when connected to specified WiFi
- [ ] Data appears correctly in Home Assistant

### 10. Create First Release

Once testing is complete:

1. **Tag the release:**
```bash
git tag -a v1.0.0 -m "Initial release - Home Assistant plugin for AndrOBD"
git push origin v1.0.0
```

2. **Create GitHub Release:**
   - Go to Releases → Create new release
   - Choose tag: v1.0.0
   - Title: "v1.0.0 - Initial Release"
   - Upload the signed release APK
   - Copy release notes from README.md

3. **Update main AndrOBD documentation:**
   - Verify the plugin link in the main AndrOBD README is correct

## Files Reference

### Source Location
All files are available in the source PR:
https://github.com/ian-morgan99/AndrOBD/tree/copilot/revert-changes-to-plugin/HomeAssistantPlugin

### Key Files to Review

1. **HomeAssistantPlugin.java** (~480 lines)
   - Main plugin service
   - Handles data reception from AndrOBD
   - Manages HTTP transmission to Home Assistant
   - Implements real-time and SSID-triggered modes

2. **SettingsActivity.java** (~170 lines)
   - Configuration UI
   - Dynamic data item selection
   - Preference management

3. **PluginReceiver.java** (~10 lines)
   - Plugin discovery mechanism

4. **README.md**
   - Complete user documentation
   - Installation and configuration guide
   - Home Assistant integration examples

5. **PLUGIN_GUIDE.md**
   - Developer documentation
   - Architecture details
   - Build instructions

## Verification Checklist

After migration, verify:

- [ ] Repository structure matches the expected layout
- [ ] Plugin framework submodule is properly initialized
- [ ] All Java source files are in correct package structure
- [ ] All resource files are properly organized
- [ ] build.gradle has correct dependencies
- [ ] Project builds successfully (`./gradlew assembleDebug`)
- [ ] No compilation errors
- [ ] Icon resources are present
- [ ] README.md is comprehensive and accurate
- [ ] PLUGIN_GUIDE.md provides clear developer instructions

## Additional Resources

- **AndrOBD Plugin Framework:** https://github.com/fr3ts0n/AndrOBD-libplugin
- **AndrOBD Plugin Examples:** https://github.com/fr3ts0n/AndrOBD-Plugin
- **MQTT Plugin Reference:** https://github.com/fr3ts0n/AndrOBD-Plugin/tree/master/MqttPublisher
- **Home Assistant Webhook Docs:** https://www.home-assistant.io/docs/automation/trigger/#webhook-trigger
- **Source PR with full context:** https://github.com/ian-morgan99/AndrOBD/pull/[PR_NUMBER]

## Expected Timeline

- **Setup repository structure:** 30 minutes
- **Copy and organize files:** 30 minutes
- **Build configuration:** 1 hour
- **Testing:** 2-4 hours
- **Documentation review:** 30 minutes
- **First release:** 30 minutes

**Total estimated time:** 5-7 hours

## Success Criteria

The migration is complete when:
1. ✅ Repository builds successfully
2. ✅ Plugin installs and runs on Android device
3. ✅ Plugin is discoverable by AndrOBD
4. ✅ Settings UI works correctly
5. ✅ Data transmission to Home Assistant works
6. ✅ Both transmission modes (real-time and SSID-triggered) function
7. ✅ Documentation is complete and accurate
8. ✅ First release (v1.0.0) is published

## Support

For questions or issues during migration:
- Review the PLUGIN_GUIDE.md in the source files
- Check the CONVERSION_SUMMARY.md for technical details
- Reference the MQTT plugin implementation
- Ask in AndrOBD Telegram: https://t.me/joinchat/G60ltQv5CCEQ94BZ5yWQbg

---

**Note:** This plugin was developed following the AndrOBD plugin architecture and includes all functionality from the original built-in Home Assistant integration, plus enhancements like selective data item publishing.
