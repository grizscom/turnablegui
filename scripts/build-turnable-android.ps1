$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$ThirdParty = Join-Path $Root "third_party"
$TurnableDir = Join-Path $ThirdParty "Turnable"

$Arm64OutDir = Join-Path $Root "app\src\main\jniLibs\arm64-v8a"
$ArmOutDir = Join-Path $Root "app\src\main\jniLibs\armeabi-v7a"

New-Item -ItemType Directory -Force -Path $ThirdParty | Out-Null
New-Item -ItemType Directory -Force -Path $Arm64OutDir | Out-Null
New-Item -ItemType Directory -Force -Path $ArmOutDir | Out-Null

if (!(Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git не найден. Установи Git for Windows."
}

if (!(Get-Command go -ErrorAction SilentlyContinue)) {
    throw "Go не найден. Установи Go for Windows."
}

$SdkRoot = $env:ANDROID_SDK_ROOT
if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
    $SdkRoot = $env:ANDROID_HOME
}

if ([string]::IsNullOrWhiteSpace($SdkRoot)) {
    $SdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

$NdkRoot = $env:ANDROID_NDK_HOME
if ([string]::IsNullOrWhiteSpace($NdkRoot)) {
    $NdkDir = Join-Path $SdkRoot "ndk"

    if (!(Test-Path $NdkDir)) {
        throw "NDK не найден. Установи NDK через Android Studio > SDK Manager > SDK Tools > NDK (Side by side)."
    }

    $NdkRoot = Get-ChildItem $NdkDir -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1 |
        ForEach-Object { $_.FullName }
}

$ToolchainBin = Join-Path $NdkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin"

$Api = "26"

$Arm64CC = Join-Path $ToolchainBin "aarch64-linux-android$Api-clang.cmd"
$ArmCC = Join-Path $ToolchainBin "armv7a-linux-androideabi$Api-clang.cmd"

if (!(Test-Path $Arm64CC)) {
    throw "Не найден компилятор arm64: $Arm64CC"
}

if (!(Test-Path $ArmCC)) {
    throw "Не найден компилятор armv7: $ArmCC"
}

if (!(Test-Path $TurnableDir)) {
    git clone https://github.com/TheAirBlow/Turnable.git $TurnableDir
} else {
    Push-Location $TurnableDir
    git pull
    Pop-Location
}

# Удаляем старые бинарники, чтобы случайно не упаковать старую CGO_ENABLED=0 сборку.
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $Arm64OutDir "libturnable.so")
Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $ArmOutDir "libturnable.so")

Push-Location $TurnableDir

Write-Host "Using SDK: $SdkRoot"
Write-Host "Using NDK: $NdkRoot"
Write-Host "Using API: $Api"
Write-Host "arm64 CC: $Arm64CC"
Write-Host "armv7 CC:  $ArmCC"

Write-Host ""
Write-Host "Cleaning Go build cache..."
go clean -cache

Write-Host ""
Write-Host "Building Turnable for Android arm64-v8a with CGO..."

$env:GOOS = "android"
$env:GOARCH = "arm64"
$env:CGO_ENABLED = "1"
$env:CC = $Arm64CC
$env:CXX = $Arm64CC

go env GOOS GOARCH CGO_ENABLED CC

go build -a -trimpath -ldflags="-s -w" -o (Join-Path $Arm64OutDir "libturnable.so") ./cmd

Write-Host ""
Write-Host "Checking arm64 build info:"
go version -m (Join-Path $Arm64OutDir "libturnable.so")

Write-Host ""
Write-Host "Building Turnable for Android armeabi-v7a with CGO..."

$env:GOOS = "android"
$env:GOARCH = "arm"
$env:GOARM = "7"
$env:CGO_ENABLED = "1"
$env:CC = $ArmCC
$env:CXX = $ArmCC

go env GOOS GOARCH GOARM CGO_ENABLED CC

go build -a -trimpath -ldflags="-s -w" -o (Join-Path $ArmOutDir "libturnable.so") ./cmd

Write-Host ""
Write-Host "Checking armv7 build info:"
go version -m (Join-Path $ArmOutDir "libturnable.so")

Pop-Location

Write-Host ""
Write-Host "Done."
Write-Host "Created:"
Write-Host "  app\src\main\jniLibs\arm64-v8a\libturnable.so"
Write-Host "  app\src\main\jniLibs\armeabi-v7a\libturnable.so"

Write-Host ""
Write-Host "IMPORTANT:"
Write-Host "В build info должно быть: CGO_ENABLED=1"