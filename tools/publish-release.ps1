[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:LASTEXITCODE = 0

$ExpectedOrigin = 'https://github.com/DGtao13/EduFlow.git'
$ExpectedRepository = 'DGtao13/EduFlow'

function Fail([string]$Message) {
    throw $Message
}

function Invoke-GitCapture([string]$RepositoryRoot, [string[]]$Arguments) {
    $output = (& git -C $RepositoryRoot @Arguments 2>&1 | Out-String)
    $exitCode = $LASTEXITCODE
    return [pscustomobject]@{ Output = $output; ExitCode = $exitCode }
}

function Get-CanonicalApkFileName([string]$ReleaseVersion) {
    if ([string]::IsNullOrWhiteSpace($ReleaseVersion) -or $ReleaseVersion -notmatch '^\d+\.\d+\.\d+$') {
        Fail "Release version '$ReleaseVersion' is not a stable X.Y.Z release version."
    }
    return "EduFlow-v$ReleaseVersion.apk"
}

function Resolve-GitHubCli {
    $candidates = [Collections.Generic.List[string]]::new()
    $pathCommand = Get-Command 'gh' -ErrorAction SilentlyContinue
    if ($null -ne $pathCommand -and -not [string]::IsNullOrWhiteSpace($pathCommand.Source)) {
        $candidates.Add($pathCommand.Source)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates.Add((Join-Path $env:LOCALAPPDATA 'Programs\GitHubCLI\bin\gh.exe'))
    }

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        & $candidate --version 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { return $candidate }
    }
    Fail 'GitHub CLI (gh) is required. Install it on PATH or at %LOCALAPPDATA%\Programs\GitHubCLI\bin\gh.exe, then authenticate before publishing.'
}

function Get-GitHubReleaseLookup([string]$GitHubCli, [string]$Repository, [string]$Tag) {
    # `gh release view` reports an absent release through human-readable stderr.
    # Query the release endpoint with response headers instead, so only a verified
    # HTTP 404 means absent; every other unsuccessful response fails closed.
    $output = (& $GitHubCli api --include --method GET "repos/$Repository/releases/tags/$Tag" 2>&1 | Out-String)
    $exitCode = $LASTEXITCODE
    $statusMatch = [regex]::Match($output, '(?m)^HTTP/\S+\s+(?<status>\d{3})\b')
    if (-not $statusMatch.Success) {
        Fail "GitHub Release lookup returned no HTTP status for $Tag (exit code $exitCode)."
    }
    $statusCode = [int]$statusMatch.Groups['status'].Value
    if ($statusCode -eq 200 -and $exitCode -eq 0) { return 'EXISTS' }
    if ($statusCode -eq 404 -and $exitCode -ne 0) { return 'ABSENT' }
    Fail "GitHub Release lookup failed for $Tag (HTTP $statusCode, exit code $exitCode)."
}

try {
    $repositoryRootResult = (& git rev-parse --show-toplevel 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { Fail 'Run this script from inside the EduFlow Git repository.' }
    $repositoryRoot = $repositoryRootResult.Trim()
    if ([string]::IsNullOrWhiteSpace($repositoryRoot)) { Fail 'Git did not return a repository root.' }

    $dirty = Invoke-GitCapture $repositoryRoot @('status', '--porcelain')
    if ($dirty.ExitCode -ne 0) { Fail 'Unable to inspect the Git working tree.' }
    if (-not [string]::IsNullOrWhiteSpace($dirty.Output)) {
        Fail 'Refusing to publish from a dirty working tree.'
    }

    $origin = Invoke-GitCapture $repositoryRoot @('remote', 'get-url', 'origin')
    if ($origin.ExitCode -ne 0 -or $origin.Output.Trim() -ne $ExpectedOrigin) {
        Fail "Origin must be exactly $ExpectedOrigin."
    }

    $tag = "v$Version"
    $title = "EduFlow v$Version"
    $notesPath = Join-Path $repositoryRoot "docs\release-notes-v$Version.md"
    $apkFileName = Get-CanonicalApkFileName $Version
    $apkPath = Join-Path $repositoryRoot "app\build\outputs\apk\release\$apkFileName"
    if ([IO.Path]::GetFileName($apkPath) -cne $apkFileName) {
        Fail "Resolved APK path does not match the requested release version: $apkPath"
    }
    if (-not (Test-Path -LiteralPath $notesPath -PathType Leaf) -or (Get-Item -LiteralPath $notesPath).Length -le 0) {
        Fail "Required release notes are missing or empty: $notesPath"
    }
    if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf) -or (Get-Item -LiteralPath $apkPath).Length -le 0) {
        Fail "Required production APK is missing or empty: $apkPath"
    }

    $localHead = (Invoke-GitCapture $repositoryRoot @('rev-parse', 'HEAD'))
    if ($localHead.ExitCode -ne 0) { Fail 'Unable to resolve local HEAD.' }
    $localCommit = $localHead.Output.Trim()
    $remoteTags = Invoke-GitCapture $repositoryRoot @('ls-remote', 'origin', "refs/tags/$tag", "refs/tags/$tag^{}")
    if ($remoteTags.ExitCode -ne 0) { Fail 'Unable to inspect the requested remote tag.' }
    $remotePeeled = @($remoteTags.Output -split '\r?\n' | Where-Object { $_ -match ("\trefs/tags/" + [regex]::Escape($tag) + '\^\{\}$') } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($remotePeeled.Count -ne 1 -or [string]::IsNullOrWhiteSpace($remotePeeled[0])) {
        Fail "Required annotated remote tag $tag is missing or does not expose a peeled commit."
    }
    if ($remotePeeled[0] -ne $localCommit) {
        Fail "Remote tag $tag does not point to local HEAD; refusing to publish."
    }

    $gh = Resolve-GitHubCli
    & $gh --version | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'GitHub CLI could not run.' }
    & $gh auth status --hostname github.com 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'GitHub CLI is not authenticated for github.com.' }

    $releaseLookup = Get-GitHubReleaseLookup $gh $ExpectedRepository $tag
    if ($releaseLookup -eq 'EXISTS') { Fail "GitHub Release $tag already exists; refusing to modify it." }
    if ($releaseLookup -ne 'ABSENT') { Fail "Unable to determine whether GitHub Release $tag already exists; refusing to create it." }

    & $gh release create $tag $apkPath --repo $ExpectedRepository --title $title --notes-file $notesPath --verify-tag
    if ($LASTEXITCODE -ne 0) { Fail "GitHub Release creation failed for $tag." }

    $releaseJson = (& $gh release view $tag --repo $ExpectedRepository --json url,name,tagName,isDraft,isPrerelease,assets 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { Fail "Unable to verify GitHub Release $tag after creation." }
    $release = $releaseJson | ConvertFrom-Json -ErrorAction Stop
    if ($release.tagName -ne $tag -or $release.name -ne $title -or $release.isDraft -or $release.isPrerelease) {
        Fail 'Created release metadata does not match the required stable release.'
    }
    $assets = @($release.assets)
    if ($assets.Count -ne 1 -or $assets[0].name -cne $apkFileName) {
        Fail "Created release does not contain exactly the required $apkFileName asset."
    }
    $localSize = (Get-Item -LiteralPath $apkPath).Length
    if ([int64]$assets[0].size -ne $localSize) {
        Fail 'Uploaded APK size does not match the verified local APK.'
    }

    Write-Host 'EDUFLOW GITHUB RELEASE: PASS'
    Write-Host "repository: $ExpectedRepository"
    Write-Host "tag: $tag"
    Write-Host "release commit: $localCommit"
    Write-Host "title: $title"
    Write-Host "release URL: $($release.url)"
    Write-Host "APK: $($assets[0].name)"
    Write-Host "APK local size: $localSize bytes"
    Write-Host "APK remote size: $($assets[0].size) bytes"
    Write-Host 'draft: false'
    Write-Host 'prerelease: false'
    Write-Host 'publication: PASS'
}
catch {
    Write-Host 'EDUFLOW GITHUB RELEASE: FAIL'
    Write-Host "reason: $($_.Exception.Message)"
    exit 1
}
