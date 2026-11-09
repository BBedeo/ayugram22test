$ErrorActionPreference = "Stop"

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $Here
$ChaquopyRoot = Join-Path $ProjectDir "chaquopy"

$Workspace = if ($env:WORKSPACE) { $env:WORKSPACE } else { Join-Path $ProjectDir "build\chaquopy\work" }
$Src = if ($env:SRC) { $env:SRC } else { Join-Path $ChaquopyRoot "product\runtime" }
$Abi = if ($env:ABI) { $env:ABI } else { "arm64-v8a" }
$PythonVersion = if ($env:PYTHON_VERSION) { $env:PYTHON_VERSION } else { "3.11" }
$Ndk = $env:NDK
$TargetZip = $env:PYTHON_TARGET_ZIP
$Out = if ($env:OUT) { $env:OUT } else { Join-Path $Workspace "out\$Abi" }

if (-not $Ndk -or -not (Test-Path $Ndk)) {
    throw "Android NDK was not found. Install NDK 27.2.12479018 in Android Studio SDK Manager."
}
if (-not $TargetZip -or -not (Test-Path $TargetZip)) {
    throw "Chaquopy target ZIP was not found: $TargetZip"
}
if (-not (Test-Path $Src)) {
    throw "Chaquopy runtime sources are missing. Run: git submodule update --init TMessagesProj/chaquopy"
}

New-Item -ItemType Directory -Force -Path $Workspace | Out-Null
$Workspace = (Resolve-Path $Workspace).Path

$Venv = Join-Path $Workspace "venv"
$Cy = Join-Path $Workspace "build\cython-$Abi"
$Work = Join-Path $Workspace "build\cmake-$Abi"
$Prefix = Join-Path $Workspace "prefix\$Abi"

# Resolve Python 3.11. Prefer an explicitly configured interpreter, otherwise
# use the standard Windows py launcher, which is also what Chaquopy expects.
$PythonExe = $null
$PythonPrefixArgs = @()
if ($env:PYTHON) {
    $PythonExe = $env:PYTHON
} else {
    $Py = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($Py) {
        $PythonExe = $Py.Source
        $PythonPrefixArgs = @("-$PythonVersion")
    } else {
        $Python = Get-Command python.exe -ErrorAction SilentlyContinue
        if ($Python) {
            $PythonExe = $Python.Source
        }
    }
}
if (-not $PythonExe) {
    throw "Python $PythonVersion was not found. Install Python 3.11 and ensure the py launcher is available."
}

$DetectedVersion = (& $PythonExe @PythonPrefixArgs -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')").Trim()
if ($LASTEXITCODE -ne 0 -or $DetectedVersion -ne $PythonVersion) {
    throw "Expected Python $PythonVersion, but found '$DetectedVersion'."
}

$VenvPython = Join-Path $Venv "Scripts\python.exe"
$VenvCython = Join-Path $Venv "Scripts\cython.exe"
if (-not (Test-Path $VenvCython)) {
    & $PythonExe @PythonPrefixArgs -m venv $Venv
    if ($LASTEXITCODE -ne 0) { throw "Failed to create Python virtual environment." }
    & $VenvPython -m pip install -q "Cython==3.0.11"
    if ($LASTEXITCODE -ne 0) { throw "Failed to install Cython." }
}

$SourceMarker = Join-Path $Prefix "source"
$ExistingSource = if (Test-Path $SourceMarker) { (Get-Content -Raw $SourceMarker).Trim() } else { "" }
if ($ExistingSource -ne $TargetZip) {
    Remove-Item -Recurse -Force $Prefix -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path (Join-Path $Prefix "lib") | Out-Null

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $Zip = [System.IO.Compression.ZipFile]::OpenRead($TargetZip)
    try {
        foreach ($Entry in $Zip.Entries) {
            if ($Entry.FullName.StartsWith("include/") -and -not [string]::IsNullOrEmpty($Entry.Name)) {
                $Relative = $Entry.FullName.Replace('/', [IO.Path]::DirectorySeparatorChar)
                $Destination = Join-Path $Prefix $Relative
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($Entry, $Destination, $true)
            } elseif ($Entry.FullName -eq "jniLibs/$Abi/libpython$PythonVersion.so") {
                $Destination = Join-Path $Prefix "lib\libpython$PythonVersion.so"
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($Entry, $Destination, $true)
            }
        }
    } finally {
        $Zip.Dispose()
    }
    Set-Content -NoNewline -Encoding UTF8 -Path $SourceMarker -Value $TargetZip
}

Remove-Item -Recurse -Force $Cy, $Work -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Cy, $Work, $Out | Out-Null

function Invoke-Cython([string]$Dir, [string]$Pyx) {
    $Stem = [IO.Path]::GetFileNameWithoutExtension($Pyx)
    $OutC = Join-Path $Cy "$Stem.c"
    Push-Location $Dir
    try {
        & $VenvCython -Wextra -Werror $Pyx -I $Cy -o $OutC
        if ($LASTEXITCODE -ne 0) { throw "Cython failed for $Pyx" }
    } finally {
        Pop-Location
    }

    $Content = [IO.File]::ReadAllText($OutC)
    $Content = [regex]::Replace(
        $Content,
        '(?m)^__PYX_EXTERN_C (\w+)',
        '/* cythonTask altered */ __PYX_EXTERN_C JNIEXPORT $1 JNICALL'
    )
    $Content = [regex]::Replace(
        $Content,
        '(__pyx_insert_code_object\(c_line)',
        '// cythonTask disabled: $1'
    )
    [IO.File]::WriteAllText($OutC, $Content, [Text.UTF8Encoding]::new($false))
}

Invoke-Cython (Join-Path $Src "src\main\python\java") "chaquopy.pyx"
Invoke-Cython (Join-Path $Src "src\main\python") "chaquopy_java.pyx"

# Resolve SDK CMake and Ninja. Prefer the version requested by build.gradle.
$Cmake = $env:CMAKE
$Ninja = $env:NINJA
$Sdk = $env:ANDROID_SDK_ROOT
if (-not $Cmake) {
    if (-not $Sdk) { throw "ANDROID_SDK_ROOT is not set." }
    $PreferredCmake = Join-Path $Sdk "cmake\3.31.6\bin\cmake.exe"
    if (Test-Path $PreferredCmake) {
        $Cmake = $PreferredCmake
    } else {
        $Candidates = Get-ChildItem (Join-Path $Sdk "cmake") -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName "bin\cmake.exe" } |
            Where-Object { Test-Path $_ }
        $Cmake = $Candidates | Select-Object -First 1
    }
}
if (-not $Cmake -or -not (Test-Path $Cmake)) {
    throw "CMake was not found in the Android SDK. Install CMake 3.31.6 in SDK Manager."
}
if (-not $Ninja) {
    $Ninja = Join-Path (Split-Path -Parent $Cmake) "ninja.exe"
}
if (-not (Test-Path $Ninja)) {
    $NinjaCommand = Get-Command ninja.exe -ErrorAction SilentlyContinue
    if ($NinjaCommand) { $Ninja = $NinjaCommand.Source }
}
if (-not $Ninja -or -not (Test-Path $Ninja)) {
    throw "Ninja was not found. Install CMake 3.31.6 from Android Studio SDK Manager."
}

$PrefixMap = Join-Path $Work "prefix-map.cmake"

# CMake treats backslashes inside values used by CMakeLists.txt as escape characters
# (for example, C:\Users becomes an invalid \U escape). Normalize every path we
# pass into CMake to forward slashes.
function Convert-ToCMakePath([string]$Path) {
    return $Path.Replace("\", "/")
}

$SrcForCmake = Convert-ToCMakePath $Src
$WorkForCmake = Convert-ToCMakePath $Work
$NinjaForCmake = Convert-ToCMakePath $Ninja
$PrefixMapForCmake = Convert-ToCMakePath $PrefixMap
$CyForCmake = Convert-ToCMakePath $Cy
$ToolchainForCmake = Convert-ToCMakePath (Join-Path $Ndk 'build\cmake\android.toolchain.cmake')
$IncludePythonForCmake = Convert-ToCMakePath (Join-Path $Prefix 'include')
$LibDirForCmake = Convert-ToCMakePath (Join-Path $Prefix 'lib')

$PrefixMapContent = 'add_compile_options("-ffile-prefix-map=' + $CyForCmake + '/=")'
[IO.File]::WriteAllText($PrefixMap, $PrefixMapContent, [Text.UTF8Encoding]::new($false))

$CmakeArgs = @(
    "-S", $SrcForCmake,
    "-B", $WorkForCmake,
    "-G", "Ninja",
    "-DCMAKE_MAKE_PROGRAM=$NinjaForCmake",
    "-DCMAKE_PROJECT_INCLUDE=$PrefixMapForCmake",
    "-DCMAKE_BUILD_TYPE=Release",
    "-DCHAQUOPY_SRC_DIR=$CyForCmake",
    "-DCMAKE_TOOLCHAIN_FILE=$ToolchainForCmake",
    "-DANDROID_ABI=$Abi",
    "-DANDROID_NATIVE_API_LEVEL=24",
    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
    "-DCHAQUOPY_PYTHON_VERSION=$PythonVersion",
    "-DCHAQUOPY_INCLUDE_PYTHON=$IncludePythonForCmake",
    "-DCHAQUOPY_LIB_DIRS=$LibDirForCmake",
    "-DCHAQUOPY_PY_SUFFIX=.so"
)
& $Cmake @CmakeArgs | Out-Null
if ($LASTEXITCODE -ne 0) { throw "CMake configure failed for $Abi." }

& $Cmake --build $Work | Out-Null
if ($LASTEXITCODE -ne 0) { throw "CMake build failed for $Abi." }

Copy-Item -Force (Join-Path $Work "chaquopy.so") (Join-Path $Out "chaquopy.so")
Copy-Item -Force (Join-Path $Work "libchaquopy_java.so") (Join-Path $Out "libchaquopy_java.so")
Get-ChildItem $Out
