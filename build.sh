#!/bin/bash

# AndrOBD Build Script
# This script automates the build process for the AndrOBD project

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "AndrOBD Build Script"
echo "=========================================="
echo

# Check for Java
echo -n "Checking Java installation... "
if ! command -v java &> /dev/null; then
    echo -e "${RED}FAILED${NC}"
    echo "Java is not installed or not in PATH"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
echo -e "${GREEN}OK${NC} (Version: $JAVA_VERSION)"

# Check for Android SDK
echo -n "Checking Android SDK... "
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo -e "${YELLOW}WARNING${NC}"
    echo "ANDROID_HOME or ANDROID_SDK_ROOT not set"
    echo "Build may fail if Android SDK is not properly configured"
else
    SDK_PATH=${ANDROID_HOME:-$ANDROID_SDK_ROOT}
    echo -e "${GREEN}OK${NC} (Path: $SDK_PATH)"
fi

# Check for git
echo -n "Checking git installation... "
if ! command -v git &> /dev/null; then
    echo -e "${RED}FAILED${NC}"
    echo "Git is not installed or not in PATH"
    exit 1
fi
echo -e "${GREEN}OK${NC}"

# Initialize submodules
echo
echo "Initializing git submodules..."
if git submodule update --init --recursive; then
    echo -e "${GREEN}Submodules initialized successfully${NC}"
else
    echo -e "${RED}Failed to initialize submodules${NC}"
    exit 1
fi

# Make gradlew executable
echo
echo "Making gradlew executable..."
chmod +x ./gradlew

# Run the build
echo
echo "=========================================="
echo "Starting Gradle build..."
echo "=========================================="
echo

BUILD_TYPE=${1:-build}

case $BUILD_TYPE in
    "debug")
        echo "Building debug APK..."
        ./gradlew assembleDebug
        ;;
    "release")
        echo "Building release APK..."
        ./gradlew assembleRelease
        ;;
    "clean")
        echo "Cleaning build artifacts..."
        ./gradlew clean
        ;;
    "build")
        echo "Building all modules..."
        ./gradlew build
        ;;
    "test")
        echo "Running tests..."
        ./gradlew test
        ;;
    *)
        echo -e "${RED}Unknown build type: $BUILD_TYPE${NC}"
        echo "Usage: $0 [build|debug|release|clean|test]"
        exit 1
        ;;
esac

BUILD_EXIT_CODE=$?

echo
echo "=========================================="
if [ $BUILD_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}Build completed successfully!${NC}"
    echo "=========================================="
    
    # Show build outputs
    if [ "$BUILD_TYPE" = "debug" ] || [ "$BUILD_TYPE" = "release" ] || [ "$BUILD_TYPE" = "build" ]; then
        echo
        echo "Build outputs:"
        if [ -d "androbd/build/outputs/apk" ]; then
            find androbd/build/outputs/apk -name "*.apk" -type f
        fi
    fi
else
    echo -e "${RED}Build failed with exit code: $BUILD_EXIT_CODE${NC}"
    echo "=========================================="
    exit $BUILD_EXIT_CODE
fi
