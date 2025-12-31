# AndrOBD Build Instructions

## Prerequisites

### Required Software
- Java Development Kit (JDK) 17 or later
- Android SDK with the following components:
  - Build Tools: 34.0.0, 35.0.0, 35.0.1, 36.0.0, or 36.1.0
  - Platform Tools
  - Android SDK Platform (various versions as needed by the project)
- Git (for cloning and managing submodules)

### Environment Variables
Ensure the following environment variables are set:
- `ANDROID_HOME` or `ANDROID_SDK_ROOT`: Path to your Android SDK installation
- `JAVA_HOME`: Path to your JDK installation

## Build Process

### 1. Clone the Repository
```bash
git clone https://github.com/fr3ts0n/AndrOBD.git
cd AndrOBD
```

### 2. Initialize Git Submodules
The project uses git submodules for the plugin framework:
```bash
git submodule update --init --recursive
```

This will clone the AndrOBD-libplugin repository into the `plugin` directory.

### 3. Build the Project
The project uses Gradle as its build system. To build all modules:

```bash
./gradlew build
```

This command will:
- Download required dependencies from Maven Central, Google Maven, and JitPack
- Compile all Java/Kotlin source code
- Run lint checks
- Execute unit tests
- Generate APK files

### 4. Build Variants

#### Debug Build
To build only the debug APK:
```bash
./gradlew assembleDebug
```

#### Release Build
To build the release APK:
```bash
./gradlew assembleRelease
```

## Build Outputs

After a successful build, you can find the generated APK files at:
- Debug APK: `androbd/build/outputs/apk/debug/androbd-debug.apk`
- Release APK: `androbd/build/outputs/apk/release/androbd-release.apk`

## Project Structure

The project consists of three main modules:
- **androbd**: The main Android application
- **library**: Shared library code
- **plugin**: Plugin framework (git submodule)

## Network Requirements

The build process requires internet connectivity to download dependencies from:
- `dl.google.com` - Android Gradle Plugin and Android SDK components
- `repo.maven.apache.org` - Maven Central repository
- `jitpack.io` - Third-party dependencies

## Troubleshooting

### Gradle Daemon
If you encounter issues, try stopping the Gradle daemon and rebuilding:
```bash
./gradlew --stop
./gradlew clean build
```

### Dependency Issues
If dependencies fail to download:
1. Check your internet connection
2. Verify that you can access the required repository URLs
3. Try clearing the Gradle cache: `rm -rf ~/.gradle/caches`

### Build Failures
For detailed error information:
```bash
./gradlew build --stacktrace --info
```

## Continuous Integration

The project includes GitHub Actions workflows for automated building:
- `.github/workflows/android.yml` - Standard CI build on push/PR
- `.github/workflows/release-build.yml` - Release APK generation
- `.github/workflows/android_beta_apk.yml` - Beta APK generation

## Additional Commands

### Clean Build
```bash
./gradlew clean
```

### Run Tests Only
```bash
./gradlew test
```

### Generate Code Coverage
```bash
./gradlew jacocoTestReport
```

### List All Tasks
```bash
./gradlew tasks --all
```
