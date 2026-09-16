[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:LASTEXITCODE = 0

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

function Test-WindowsHost {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        Fail 'This setup script must run on Windows.'
    }
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
    if (-not $match.Success) {
        Fail 'Unable to determine the configured Java version.'
    }
    if ($match.Groups['major'].Value -ne '17') {
        Fail 'The configured JDK must be exactly major version 17.'
    }
    return $match.Value
}

function Convert-SecureStringToDpapiCiphertext([Security.SecureString]$Secret) {
    try {
        return ConvertFrom-SecureString -SecureString $Secret -ErrorAction Stop
    }
    catch {
        Fail 'Windows DPAPI protection failed for a password.'
    }
}

function Test-NonEmptySecret([Security.SecureString]$Secret, [string]$Label) {
    $bstr = [IntPtr]::Zero
    try {
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
        if ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Length -eq 0) {
            Fail "$Label cannot be empty."
        }
    }
    finally {
        if ($bstr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
    }
}

function Protect-CurrentUserPath([string]$Path, [bool]$IsDirectory) {
    try {
        $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $acl = Get-Acl -LiteralPath $Path
        $acl.SetAccessRuleProtection($true, $false)
        $inheritance = if ($IsDirectory) {
            [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
        }
        else {
            [Security.AccessControl.InheritanceFlags]::None
        }
        $rule = [Security.AccessControl.FileSystemAccessRule]::new(
            $sid,
            [Security.AccessControl.FileSystemRights]::FullControl,
            $inheritance,
            [Security.AccessControl.PropagationFlags]::None,
            [Security.AccessControl.AccessControlType]::Allow
        )
        $acl.SetAccessRule($rule)
        Set-Acl -LiteralPath $Path -AclObject $acl
        return $true
    }
    catch {
        Write-Warning 'Could not fully restrict the local configuration ACL to the current user.'
        return $false
    }
}

function Test-OutsideRepository([string]$Candidate, [string]$RepositoryRoot) {
    $candidateFull = [IO.Path]::GetFullPath($Candidate)
    $repoFull = [IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\') + '\'
    return -not $candidateFull.StartsWith($repoFull, [StringComparison]::OrdinalIgnoreCase)
}

$phase = 'initialization'
$keystorePassword = $null
$keyPassword = $null

try {
    $phase = 'Windows host check'
    Test-WindowsHost

    $repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    $defaultJdk = 'C:\Users\DG_tao\.jdks\jbr-17.0.14'
    $defaultKeystore = 'C:\Users\DG_tao\Documents\EduFlow-Keys\eduflow-release.jks'
    $configDirectory = Join-Path $env:USERPROFILE '.eduflow'
    $configPath = Join-Path $configDirectory 'release-config.json'

    $phase = 'JDK validation'
    if (-not (Test-Path -LiteralPath $defaultJdk -PathType Container)) {
        Fail 'Default JDK path is missing. Update the script defaults only after installing a JDK 17.'
    }
    $javaVersion = Get-Java17Version $defaultJdk

    $phase = 'keystore validation'
    if (-not (Test-Path -LiteralPath $defaultKeystore -PathType Leaf)) {
        Fail 'The configured production keystore file is missing.'
    }
    $alias = 'eduflow-release'
    if ([string]::IsNullOrWhiteSpace($alias)) {
        Fail 'The configured key alias cannot be blank.'
    }

    $phase = 'secure password entry'
    $keystorePassword = Read-Host -Prompt 'Enter existing EduFlow keystore password' -AsSecureString
    Test-NonEmptySecret $keystorePassword 'Keystore password'
    $keyPassword = Read-Host -Prompt 'Enter existing EduFlow key password' -AsSecureString
    Test-NonEmptySecret $keyPassword 'Key password'

    $phase = 'local configuration safety'
    if (-not (Test-OutsideRepository $configPath $repositoryRoot)) {
        Fail 'The local configuration path must remain outside the repository.'
    }
    New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
    $directoryAclProtected = Protect-CurrentUserPath $configDirectory $true

    $phase = 'DPAPI protection'
    $configuration = [ordered]@{
        jdkPath = $defaultJdk
        keystorePath = $defaultKeystore
        keyAlias = $alias
        keystorePasswordProtected = Convert-SecureStringToDpapiCiphertext $keystorePassword
        keyPasswordProtected = Convert-SecureStringToDpapiCiphertext $keyPassword
    }
    $json = $configuration | ConvertTo-Json -Depth 3

    $phase = 'configuration write'
    $temporaryPath = Join-Path $configDirectory ('release-config.' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($temporaryPath, $json, [Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temporaryPath -Destination $configPath -Force
    }
    finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force -ErrorAction SilentlyContinue
        }
    }
    $fileAclProtected = Protect-CurrentUserPath $configPath $false

    $phase = 'Git safety check'
    $repositoryConfigPath = Join-Path $repositoryRoot '.eduflow\release-config.json'
    if (Test-Path -LiteralPath $repositoryConfigPath) {
        Fail 'A release configuration exists inside the repository; remove it before continuing.'
    }
    $status = (& git -C $repositoryRoot status --porcelain 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        Fail 'Unable to inspect Git status for the repository safety check.'
    }
    if ($status -match '(?im)(?:^|\s)(?:\.eduflow/|release-config(?:[.-]|\.json))') {
        Fail 'Git status contains a repository-local release configuration or secret config path.'
    }

    Write-Host ''
    Write-Host 'EDUFLOW RELEASE ENVIRONMENT: CONFIGURED'
    Write-Host 'configuration created: YES'
    Write-Host "config path: $configPath"
    Write-Host "JDK path: $defaultJdk"
    Write-Host "Java: $javaVersion"
    Write-Host "keystore path: $defaultKeystore"
    Write-Host "alias: $alias"
    Write-Host 'keystore password configured: YES'
    Write-Host 'key password configured: YES'
    Write-Host ("directory ACL hardened: " + $(if ($directoryAclProtected) { 'YES' } else { 'NO (warning issued)' }))
    Write-Host ("file ACL hardened: " + $(if ($fileAclProtected) { 'YES' } else { 'NO (warning issued)' }))
    Write-Host 'Passwords are DPAPI-protected for the current Windows user and are not stored in plaintext.'
    Write-Host 'Security note: a process running as this same Windows account may be able to decrypt the protected values.'
    exit 0
}
catch {
    Write-Host ''
    Write-Host 'EDUFLOW RELEASE ENVIRONMENT: FAIL'
    Write-Host "phase: $phase"
    Write-Host "reason: $($_.Exception.Message)"
    exit 1
}
finally {
    if ($null -ne $keystorePassword) { $keystorePassword.Dispose() }
    if ($null -ne $keyPassword) { $keyPassword.Dispose() }
}
