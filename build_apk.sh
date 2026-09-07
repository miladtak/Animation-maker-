#!/bin/bash
# Puppet Studio 2D - APK Build and Root Deployment Script
set -e

echo "=== شروع فرآیند ساخت و بیلد APK برنامه Puppet Studio 2D ==="
gradle copyApkToRoot

if [ -f "./PuppetStudio2D.apk" ]; then
    echo "=== فایل APK با موفقیت در ریشه پروژه قرار گرفت: $(pwd)/PuppetStudio2D.apk ==="
    ls -lh ./PuppetStudio2D.apk
else
    echo "خطا در یافتن فایل APK در ریشه!"
    exit 1
fi
