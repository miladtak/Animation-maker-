#!/bin/bash
set -e
gradle :app:copyApkToRoot
echo "APK successfully built and copied to root directory: $(pwd)/PuppetStudio2D.apk"
