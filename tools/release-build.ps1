[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:LASTEXITCODE = 0

$ExpectedPackage = 'com.eduflow.app'
$ExpectedCertificateSha256 = '35:D6:DF:B9:D7:86:9A:88:D0:23:04:BC:DC:91:2F:57:FF:74:63:9E:69:C1:52:20:5C:64:FA:49:2C:8B:9F:7F'

function Fail([string]$Message) {
    throw $Message
}

function Invoke-NativeCapture([string]$FilePath, [string[]]$Arguments) {
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = (& $FilePath @Arguments 2>&1 | Out-String)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return [pscustomobject]@{ Output = $output; ExitCode = $exitCode }
}

function Get-Java17Version([string]$JdkPath) {
    $java = Join-Path $JdkPath 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $java -PathType Leaf)) {
        Fail 'The configured JDK does not contain bin\java.exe.'
    }
    $result = Invoke-NativeCapture $java @('-version')
    if ($result.ExitCode -ne 0) {
        Fail 'Unable to run java.exe from the configured JDK.'
    }
    $match = [regex]::Match($result.Output, '(?m)(?:java|openjdk) version "(?<major>\d+)(?:[.][^"]*)?"')
    if (-not $match.Success -or $match.Groups['major'].Value -ne '17') {
        Fail 'The configured JDK must be exactly major version 17.'
    }
    return $match.Value
}

function ConvertFrom-DpapiCiphertext([string]$Ciphertext, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace($Ciphertext)) {
        Fail "The protected $Label is missing from the local configuration."
    }
    $secure = $null
    $bstr = [IntPtr]::Zero
    try {
        $secure = ConvertTo-SecureString -String $Ciphertext -ErrorAction Stop
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        if ([string]::IsNullOrEmpty($plain)) {
            Fail "The protected $Label is empty."
        }
        return $plain
    }
    catch {
        Fail "Unable to decrypt the protected $Label for the current Windows user."
    }
    finally {
        if ($bstr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
        if ($null -ne $secure) {
            $secure.Dispose()
        }
    }
}

function Get-ForbiddenRepositoryPath([string]$RelativePath) {
    $path = $RelativePath.Replace('\', '/').TrimStart('./')
    $name = [IO.Path]::GetFileName($path)
    $lowerName = $name.ToLowerInvariant()
    $lowerPath = $path.ToLowerInvariant()

    if ($lowerPath -match '(^|/)(build|\.eduflow)(/|$)') { return $path }
    if ($lowerName -match '\.(jks|keystore|p12|pem|key|apk|aab|db|sqlite|eduflow)$') { return $path }
    if ($lowerName -in @('local.properties', 'keystore.properties', 'signing.properties', 'release-config.json')) { return $path }
    if ($lowerName -match '^\.env(?:\.|$)') { return $path }
    if ($lowerName -match 'signing.*\.properties$|keystore.*\.properties$|release-config.*\.json$') { return $path }
    if ($lowerName -match '^eduflow-(?:backup|export)-') { return $path }
    return $null
}

function Assert-GitSafety([string]$RepositoryRoot) {
    $diffResult = Invoke-NativeCapture 'git' @('-C', $RepositoryRoot, 'diff', '--check')
    if ($diffResult.ExitCode -ne 0) {
        Fail 'git diff --check reported whitespace errors; correct them before the production build.'
    }
    if (-not [string]::IsNullOrWhiteSpace($diffResult.Output)) {
        Write-Warning 'git diff --check emitted non-fatal output (for example line-ending warnings).'
    }

    $tracked = @(& git -C $RepositoryRoot ls-files)
    if ($LASTEXITCODE -ne 0) { Fail 'Unable to inspect tracked files for Git safety.' }
    $staged = @(& git -C $RepositoryRoot diff --cached --name-only --diff-filter=ACMR)
    if ($LASTEXITCODE -ne 0) { Fail 'Unable to inspect staged files for Git safety.' }
    $forbidden = @($tracked + $staged | ForEach-Object { Get-ForbiddenRepositoryPath $_ } | Where-Object { $null -ne $_ } | Sort-Object -Unique)
    if ($forbidden.Count -gt 0) {
        Fail ('Forbidden sensitive or generated material is tracked or staged: ' + ($forbidden -join ', '))
    }
}

function Get-AndroidSdkTools([string]$RepositoryRoot) {
    $candidates = [Collections.Generic.List[string]]::new()
    foreach ($value in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        if (-not [string]::IsNullOrWhiteSpace($value)) { $candidates.Add($value) }
    }
    $localProperties = Join-Path $RepositoryRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($null -ne $sdkLine) {
            $candidates.Add((($sdkLine -replace '^\s*sdk\.dir\s*=\s*', '') -replace '\\\\', '\'))
        }
    }
    foreach ($sdkRoot in ($candidates | Select-Object -Unique)) {
        $buildToolsRoot = Join-Path $sdkRoot 'build-tools'
        if (-not (Test-Path -LiteralPath $buildToolsRoot -PathType Container)) { continue }
        $usable = Get-ChildItem -LiteralPath $buildToolsRoot -Directory | Where-Object {
            (Test-Path -LiteralPath (Join-Path $_.FullName 'aapt.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $_.FullName 'aapt2.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat') -PathType Leaf)
        } | Sort-Object @{ Expression = {
            $match = [regex]::Match($_.Name, '^(\d+(?:\.\d+){0,3})')
            if ($match.Success) { [version]$match.Groups[1].Value } else { [version]'0.0' }
        }; Descending = $true }
        $selected = $usable | Select-Object -First 1
        if ($null -ne $selected) {
            return [pscustomobject]@{
                SdkRoot = $sdkRoot
                BuildTools = $selected.FullName
                Aapt = Join-Path $selected.FullName 'aapt.exe'
                Aapt2 = Join-Path $selected.FullName 'aapt2.exe'
                ApkSigner = Join-Path $selected.FullName 'apksigner.bat'
            }
        }
    }
    Fail 'No installed Android SDK Build Tools with aapt, aapt2, and apksigner were found.'
}

function Invoke-Gradle([string]$GradleBat, [string]$Task) {
    Write-Host "Running Gradle task: $Task"
    & $GradleBat $Task
    if ($LASTEXITCODE -ne 0) {
        Fail "Gradle task failed: $Task"
    }
}

function ConvertFrom-GradleReleaseIdentityOutput([string]$Output) {
    $markerPrefix = 'EDUFLOW_RELEASE_IDENTITY|'
    $markers = [Collections.Generic.List[object]]::new()
    foreach ($line in ($Output -split '\r\n|\n|\r')) {
        $normalizedLine = $line.Trim()
        if (-not $normalizedLine.StartsWith($markerPrefix, [StringComparison]::Ordinal)) {
            continue
        }

        $fields = $normalizedLine.Split([char]'|')
        if ($fields.Length -ne 4 -or $fields[0] -cne 'EDUFLOW_RELEASE_IDENTITY') {
            Fail 'Gradle release identity marker is malformed.'
        }
        $package = $fields[1].Trim()
        $versionName = $fields[2].Trim()
        $versionCodeText = $fields[3].Trim()
        if ([string]::IsNullOrWhiteSpace($package) -or [string]::IsNullOrWhiteSpace($versionName)) {
            Fail 'Gradle release identity marker contains an empty package or versionName.'
        }
        $versionCode = 0
        if (-not [int]::TryParse($versionCodeText, [ref]$versionCode) -or $versionCode -le 0) {
            Fail 'Gradle release identity marker contains an invalid versionCode.'
        }

        $markers.Add([pscustomobject]@{
            Package = $package
            VersionName = $versionName
            VersionCode = $versionCode
        })
    }

    if ($markers.Count -eq 0) {
        Fail 'Gradle release identity marker was not found.'
    }

    $distinctMarkers = @($markers | Sort-Object Package, VersionName, VersionCode -Unique)
    if ($distinctMarkers.Count -gt 1) {
        Fail 'Conflicting Gradle release identity markers were returned.'
    }
    return $distinctMarkers[0]
}

function Get-ReleaseIdentity([string]$GradleBat) {
    $result = Invoke-NativeCapture $GradleBat @(':app:printReleaseIdentity')
    if ($result.ExitCode -ne 0) {
        if (-not [string]::IsNullOrWhiteSpace($result.Output)) {
            Write-Host 'Gradle identity task output:'
            Write-Host $result.Output.TrimEnd()
        }
        Fail 'Gradle could not report the configured release identity.'
    }
    try {
        return ConvertFrom-GradleReleaseIdentityOutput $result.Output
    }
    catch {
        if (-not [string]::IsNullOrWhiteSpace($result.Output)) {
            Write-Host 'Gradle identity task output:'
            Write-Host $result.Output.TrimEnd()
        }
        throw
    }
}

function Get-ReadableSize([Int64]$Bytes) {
    if ($Bytes -lt 1KB) { return "$Bytes B" }
    if ($Bytes -lt 1MB) { return ('{0:N2} KB' -f ($Bytes / 1KB)) }
    return ('{0:N2} MB' -f ($Bytes / 1MB))
}

function Get-CanonicalApkPath([string]$RepositoryRoot, [string]$VersionName) {
    if ([string]::IsNullOrWhiteSpace($VersionName) -or $VersionName -notmatch '^\d+\.\d+\.\d+$') {
        Fail "Production versionName '$VersionName' is not a stable X.Y.Z release version."
    }
    $releaseDirectory = Join-Path $RepositoryRoot 'app\build\outputs\apk\release'
    return Join-Path $releaseDirectory "EduFlow-v$VersionName.apk"
}

function Assert-ApkIdentity([pscustomobject]$Tools, [string]$ApkPath, [pscustomobject]$ExpectedIdentity) {
    $badgingResult = Invoke-NativeCapture $Tools.Aapt @('dump', 'badging', $ApkPath)
    if ($badgingResult.ExitCode -ne 0) { Fail 'aapt could not read the production APK.' }
    $badging = $badgingResult.Output
    $packageMatch = [regex]::Match($badging, "(?m)^package: name='(?<package>[^']+)' versionCode='(?<versionCode>[^']+)' versionName='(?<versionName>[^']*)'")
    if (-not $packageMatch.Success) { Fail 'aapt did not return APK package metadata.' }
    $actualPackage = $packageMatch.Groups['package'].Value
    $actualVersionName = $packageMatch.Groups['versionName'].Value
    $actualVersionCode = $packageMatch.Groups['versionCode'].Value

    if ($actualPackage -ne $ExpectedPackage -or $actualPackage -ne $ExpectedIdentity.Package) { Fail 'APK package does not match the production package expectation.' }
    if ($actualVersionName -ne $ExpectedIdentity.VersionName) { Fail 'APK versionName does not match the Gradle release identity.' }
    if ($actualVersionCode -ne $ExpectedIdentity.VersionCode) { Fail 'APK versionCode does not match the Gradle release identity.' }

    $packageResult = Invoke-NativeCapture $Tools.Aapt2 @('dump', 'packagename', $ApkPath)
    $packageName = $packageResult.Output.Trim()
    if ($packageResult.ExitCode -ne 0 -or $packageName -ne $actualPackage) { Fail 'aapt2 could not confirm the APK package identity.' }
    $manifestResult = Invoke-NativeCapture $Tools.Aapt @('dump', 'xmltree', $ApkPath, 'AndroidManifest.xml')
    if ($manifestResult.ExitCode -ne 0) { Fail 'aapt could not inspect the APK manifest.' }
    $manifest = $manifestResult.Output
    if ($badging -match '(?m)^application-debuggable\s*$' -or $manifest -match '(?im)android:debuggable.*(?:true|0xffffffff|0x0*1)') {
        Fail 'APK manifest is debuggable.'
    }
    return [pscustomobject]@{ Package = $actualPackage; VersionName = $actualVersionName; VersionCode = $actualVersionCode }
}

function Assert-ProductionCertificate([pscustomobject]$Tools, [string]$ApkPath) {
    $verificationResult = Invoke-NativeCapture $Tools.ApkSigner @('verify', '--verbose', '--print-certs', $ApkPath)
    if ($verificationResult.ExitCode -ne 0) { Fail 'apksigner signature verification failed.' }
    $verification = $verificationResult.Output
    $match = [regex]::Match($verification, '(?im)^Signer\s+#1\s+certificate\s+SHA-256\s+digest:\s*(?<digest>[0-9A-Fa-f:\s-]+)$')
    if (-not $match.Success) { Fail 'apksigner did not provide the signer certificate SHA-256 digest.' }
    $actual = ([regex]::Replace($match.Groups['digest'].Value, '[^0-9A-Fa-f]', '')).ToUpperInvariant()
    $expected = ([regex]::Replace($ExpectedCertificateSha256, '[^0-9A-Fa-f]', '')).ToUpperInvariant()
    if ($actual -ne $expected) { Fail 'APK signer certificate does not match the expected production certificate.' }
}

$phase = 'initialization'
$keystorePassword = $null
$keyPassword = $null

try {
    $phase = 'repository root detection'
    $repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    $gradleBat = Join-Path $repositoryRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradleBat -PathType Leaf)) { Fail 'gradlew.bat was not found at the repository root.' }
    $gitRootResult = Invoke-NativeCapture 'git' @('-C', $repositoryRoot, 'rev-parse', '--show-toplevel')
    $gitRoot = $gitRootResult.Output.Trim()
    if ($gitRootResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($gitRoot)) { Fail 'The script location is not inside a Git repository.' }

    $phase = 'local configuration loading'
    $configPath = Join-Path (Join-Path $env:USERPROFILE '.eduflow') 'release-config.json'
    if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
        Fail 'Local release configuration is missing. Run .\tools\setup-release-environment.ps1 first.'
    }
    if ([IO.Path]::GetFullPath($configPath).StartsWith($repositoryRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        Fail 'Local release configuration must remain outside the repository.'
    }
    try { $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json -ErrorAction Stop } catch { Fail 'Local release configuration is malformed.' }
    foreach ($field in @('jdkPath', 'keystorePath', 'keyAlias', 'keystorePasswordProtected', 'keyPasswordProtected')) {
        if ($null -eq $config.$field -or [string]::IsNullOrWhiteSpace([string]$config.$field)) { Fail "Local release configuration is missing $field." }
    }

    $phase = 'JDK validation'
    if (-not (Test-Path -LiteralPath $config.jdkPath -PathType Container)) { Fail 'Configured JDK path is missing.' }
    $javaVersion = Get-Java17Version $config.jdkPath
    $env:JAVA_HOME = $config.jdkPath
    $env:PATH = (Join-Path $config.jdkPath 'bin') + [IO.Path]::PathSeparator + $env:PATH
    Write-Host "JDK: $($config.jdkPath) ($javaVersion)"

    $phase = 'keystore validation'
    if (-not [IO.Path]::IsPathRooted($config.keystorePath) -or -not (Test-Path -LiteralPath $config.keystorePath -PathType Leaf)) { Fail 'Configured production keystore path is missing or is not absolute.' }
    if ([string]::IsNullOrWhiteSpace($config.keyAlias)) { Fail 'Configured production key alias is blank.' }

    $phase = 'Git safety'
    Assert-GitSafety $repositoryRoot

    $phase = 'Android SDK tool discovery'
    $tools = Get-AndroidSdkTools $repositoryRoot
    Write-Host "Android SDK: $($tools.SdkRoot)"
    Write-Host "Build Tools: $($tools.BuildTools)"

    $phase = 'credential decryption'
    $keystorePassword = ConvertFrom-DpapiCiphertext ([string]$config.keystorePasswordProtected) 'keystore password'
    $keyPassword = ConvertFrom-DpapiCiphertext ([string]$config.keyPasswordProtected) 'key password'
    $env:EDUFLOW_KEYSTORE_PATH = [string]$config.keystorePath
    $env:EDUFLOW_KEYSTORE_PASSWORD = $keystorePassword
    $env:EDUFLOW_KEY_ALIAS = [string]$config.keyAlias
    $env:EDUFLOW_KEY_PASSWORD = $keyPassword

    $phase = 'Gradle release identity'
    $expectedIdentity = Get-ReleaseIdentity $gradleBat
    if ($expectedIdentity.Package -ne $ExpectedPackage) { Fail 'Gradle production package does not match the expected production package.' }

    $phase = 'standard validation'
    foreach ($task in @('testDebugUnitTest', 'assembleDebug', 'assembleDebugAndroidTest', 'lintDebug', 'assembleQaRelease', 'lintQaRelease', 'lintRelease')) {
        Invoke-Gradle $gradleBat $task
    }
    Assert-GitSafety $repositoryRoot

    $phase = 'production build'
    Invoke-Gradle $gradleBat 'assembleRelease'
    $apkPath = Join-Path $repositoryRoot 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf) -or (Get-Item -LiteralPath $apkPath).Length -le 0) { Fail 'Expected production APK is missing or empty.' }

    $phase = 'APK identity verification'
    $apkIdentity = Assert-ApkIdentity $tools $apkPath $expectedIdentity

    $phase = 'APK signature verification'
    Assert-ProductionCertificate $tools $apkPath

    $phase = 'APK hash calculation'
    $apkFile = Get-Item -LiteralPath $apkPath
    $apkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash

    $phase = 'canonical artifact creation'
    $canonicalApkPath = Get-CanonicalApkPath $repositoryRoot $apkIdentity.VersionName
    Copy-Item -LiteralPath $apkPath -Destination $canonicalApkPath -Force
    $canonicalFile = Get-Item -LiteralPath $canonicalApkPath
    if ($canonicalFile.Length -le 0 -or $canonicalFile.Length -ne $apkFile.Length) {
        Fail 'Canonical production APK is missing, empty, or has an unexpected size.'
    }
    $canonicalHash = (Get-FileHash -LiteralPath $canonicalApkPath -Algorithm SHA256).Hash
    if ($canonicalHash -ne $apkHash) {
        Fail 'Canonical production APK hash does not match the verified build output.'
    }
    Remove-Item -LiteralPath $apkPath -Force
    if (Test-Path -LiteralPath $apkPath) {
        Fail 'Generic Gradle APK output could not be removed after canonicalization.'
    }
    $apkPath = $canonicalApkPath
    $apkFile = Get-Item -LiteralPath $apkPath
    $apkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash

    Write-Host ''
    Write-Host 'EDUFLOW RELEASE BUILD: PASS'
    Write-Host "versionName: $($apkIdentity.VersionName)"
    Write-Host "versionCode: $($apkIdentity.VersionCode)"
    Write-Host "package: $($apkIdentity.Package)"
    Write-Host "APK: $apkPath"
    Write-Host "size: $($apkFile.Length) bytes / $(Get-ReadableSize $apkFile.Length)"
    Write-Host "APK SHA-256: $apkHash"
    Write-Host 'certificate: MATCH'
    Write-Host 'validation: PASS'
    exit 0
}
catch {
    Write-Host ''
    Write-Host 'EDUFLOW RELEASE BUILD: FAIL'
    Write-Host "phase: $phase"
    Write-Host "reason: $($_.Exception.Message)"
    exit 1
}
finally {
    $keystorePassword = $null
    $keyPassword = $null
    Remove-Item Env:EDUFLOW_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:EDUFLOW_KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:EDUFLOW_KEYSTORE_PATH -ErrorAction SilentlyContinue
    Remove-Item Env:EDUFLOW_KEY_ALIAS -ErrorAction SilentlyContinue
}
