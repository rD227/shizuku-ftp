# deploy.ps1
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "--- Starting Auto-Deployment ---" -ForegroundColor Cyan

# 1. Check ADB Connection
$devices = adb devices
if ($devices -match "device$") {
    Write-Host "Device connected." -ForegroundColor Green
} else {
    Write-Host "No device found. Restarting ADB server..." -ForegroundColor Yellow
    adb kill-server
    adb start-server
    Start-Sleep -Seconds 2
}

# 2. Build Project
Write-Host "Building APK..." -ForegroundColor Cyan
if (Test-Path ".\gradlew.bat") {
    cmd /c ".\gradlew.bat assembleDebug"
} else {
    Write-Host "Error: gradlew.bat not found." -ForegroundColor Red
    exit
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit
}

# 3. Install APK
$apkDir = "D:\Users\xvsu\AndroidStudioProjects\shizuku-ftp\primitiveFTPd\debug"
$apkFile = Get-ChildItem -Path "$apkDir\*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if ($apkFile) {
    Write-Host "Installing: $($apkFile.Name)" -ForegroundColor Cyan
    adb install -r -d -t -g "$($apkFile.FullName)"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Installation Successful!" -ForegroundColor Green
        
        # 4. Launch App
        Write-Host "Launching app..." -ForegroundColor Cyan
        # 包名和 Activity 路径
        $packageName = "org.primftpd.shizuku" 
        $activityName = "org.primftpd.ui.MainTabsActivity"
        
        adb shell am start -n "$packageName/$activityName"
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "App launched successfully!" -ForegroundColor Green
        }
    } else {
        Write-Host "Installation failed." -ForegroundColor Red
    }
} else {
    Write-Host "Error: No APK found." -ForegroundColor Red
}