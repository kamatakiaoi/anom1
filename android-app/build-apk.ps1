$ErrorActionPreference = "Stop"

$envDir = "C:\Users\flooxie\.android_build_env"
$jdkDir = "$envDir\jdk-17"
$sdkDir = "$envDir\android-sdk"
$gradleDir = "$envDir\gradle-8.8"

$env:JAVA_HOME = $jdkDir
$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir
$env:PATH = "$jdkDir\bin;$gradleDir\bin;$sdkDir\platform-tools;$env:PATH"

Write-Host "=========================================="
Write-Host " Building Anonymous Chat Native Android APK"
Write-Host " Version: 3.6.10 (Build 3610)"
Write-Host "=========================================="
Write-Host "JAVA_HOME   : $env:JAVA_HOME"
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"

$appDir = "c:\Users\flooxie\Downloads\Compressed\code2\android-app"
Set-Location $appDir

& "$gradleDir\bin\gradle.bat" assembleDebug --no-daemon --stacktrace
if ($LASTEXITCODE -ne 0) {
    Write-Host "Gradle build failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

$apkPath = "$appDir\app\build\outputs\apk\debug\app-debug.apk"
$destApkPath = "c:\Users\flooxie\Downloads\Compressed\code2\AnonymousChat.apk"

if (Test-Path $apkPath) {
    Copy-Item -Path $apkPath -Destination $destApkPath -Force
    $apkSize = (Get-Item $destApkPath).Length / 1MB
    Write-Host "------------------------------------------"
    Write-Host " BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host " Root APK Output: $destApkPath" -ForegroundColor Cyan
    Write-Host " APK Size: $([math]::Round($apkSize, 2)) MB" -ForegroundColor Cyan
    Write-Host "------------------------------------------"
} else {
    Write-Host "Build finished but APK not found at $apkPath" -ForegroundColor Red
    exit 1
}
