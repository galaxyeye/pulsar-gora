<#
.SYNOPSIS
Bump the local project version to the next patch after the latest Maven Central release.

.DESCRIPTION
1. Query Maven Central for the latest released version of gora-core.
2. Compute the expected next-patch version (latest + 1 patch).
3. If the local version (from root pom.xml) is not that expected version,
   warn and ask for confirmation (unless --Yes).
4. Verify every pom.xml in the project agrees with the root pom.xml.
   If any child POM diverges, warn and exit.
5. Bump the version in all pom.xml files (or just report with --DryRun).
6. Optionally commit the changes with --Commit.

.PARAMETER DryRun
Print what would be changed without writing anything.

.PARAMETER Yes
Skip the confirmation prompt (non-interactive mode).

.PARAMETER Commit
Commit the version bump changes after applying them.
#>

param(
    [switch]$DryRun,
    [switch]$Yes,
    [switch]$Commit
)

$ErrorActionPreference = "Stop"

# ── helpers ──────────────────────────────────────────────────────────────────

function Write-Warn { Write-Host "WARN: $args" -ForegroundColor Yellow }
function Write-ErrorMsg { Write-Host "ERROR: $args" -ForegroundColor Red }
function Write-Info  { Write-Host "$args" -ForegroundColor Green }

function Die {
    Write-ErrorMsg $args
    exit 1
}

# ── paths ────────────────────────────────────────────────────────────────────

$ScriptDir = $PSScriptRoot
$ProjectRoot = (Resolve-Path "$ScriptDir/..").Path
$RootPom = Join-Path $ProjectRoot "pom.xml"

if (-not (Test-Path $RootPom)) {
    Die "Root pom.xml not found at $RootPom"
}

# ── maven coordinates ────────────────────────────────────────────────────────

$GroupId = "ai.platon.gora"
$ArtifactId = "gora-core"
$GroupPath = $GroupId.Replace('.', '/')
$MavenMetadataUrl = "https://repo1.maven.org/maven2/$GroupPath/$ArtifactId/maven-metadata.xml"

# ── 1. fetch latest released version from Maven Central ──────────────────────

Write-Info "Fetching latest released version from Maven Central…"
Write-Info "  URL: $MavenMetadataUrl"

try {
    $Response = Invoke-WebRequest -Uri $MavenMetadataUrl -TimeoutSec 30 -UseBasicParsing -ErrorAction Stop
    $Metadata = $Response.Content
} catch {
    Die "Failed to fetch maven-metadata.xml from Maven Central.`n  $_"
}

# Extract <latest> element, fall back to the last non-snapshot <version>
if ($Metadata -match '<latest>([^<]+)</latest>') {
    $LatestReleased = $Matches[1]
} else {
    $LatestReleased = $null
}

if (-not $LatestReleased) {
    $VersionMatches = [regex]::Matches($Metadata, '<version>([^<]+)</version>')
    $LatestReleased = ($VersionMatches |
        ForEach-Object { $_.Groups[1].Value } |
        Where-Object { $_ -notmatch 'snapshot' } |
        Select-Object -Last 1)
}

if (-not $LatestReleased) {
    Die "Could not determine latest released version from Maven metadata."
}
Write-Info "  Latest released: $LatestReleased"

# ── 2. compute expected next-patch version ───────────────────────────────────

if ($LatestReleased -match '^([0-9]+\.[0-9]+\.)([0-9]+)(-.*)?$') {
    $MajorMinor = $Matches[1]
    $Patch = [int]$Matches[2]
    $Suffix = if ($Matches[3]) { $Matches[3] } else { "" }
    $NextPatch = $Patch + 1
    $ExpectedVersion = "$MajorMinor$NextPatch$Suffix"
} else {
    Die "Cannot parse latest released version: '$LatestReleased'.  Expected format: X.Y.Z[-suffix]"
}

Write-Info "  Expected next patch: $ExpectedVersion"

# ── 3. read local version from root pom.xml ──────────────────────────────────

$RootPomContent = Get-Content $RootPom -Raw -Encoding UTF8

# Strip the <parent>…</parent> block first so we pick up the project-level
# <version>, not the parent POM version inherited from org.apache:apache.
$StrippedContent = $RootPomContent -replace '(?s)<parent>.*?</parent>', ''
if ($StrippedContent -match '<version>([^<]+)</version>') {
    $LocalVersion = $Matches[1]
} else {
    Die "Could not extract version from root pom.xml."
}

Write-Info "  Local version:       $LocalVersion"

# ── 4. compare and (maybe) confirm ───────────────────────────────────────────

if ($LocalVersion -eq $ExpectedVersion) {
    Write-Info "Local version already matches the expected next patch.  Nothing to bump."
    exit 0
}

Write-Warn "Local version ($LocalVersion) does NOT match expected next patch ($ExpectedVersion)."
Write-Warn "Latest Maven Central release is $LatestReleased, so the next development"
Write-Warn "version should be $ExpectedVersion."

if (-not $Yes) {
    $Reply = Read-Host "`nBump local version from $LocalVersion to $ExpectedVersion? [y/N]"
    if ($Reply -notmatch '^[yY]([eE][sS])?$') {
        Die "Aborted by user."
    }
}

# ── 5. verify all POMs are consistent with root BEFORE changing anything ─────

Write-Info "Verifying all pom.xml files agree with root…"

# -Depth 1  =  one level of recursion  =  find -maxdepth 2
$PomFiles = @(Get-ChildItem -Path $ProjectRoot -Recurse -Depth 1 -Filter 'pom.xml' |
    Sort-Object FullName |
    ForEach-Object { $_.FullName })

$Diverged = @()
foreach ($pom in $PomFiles) {
    if ($pom -eq $RootPom) { continue }

    # A child POM is "consistent" if $LocalVersion appears anywhere in a
    # <version> element.  This covers three cases:
    #   1. Explicit project <version> (most modules).
    #   2. Version inherited via <parent><version> (modules without explicit version).
    #   3. Both of the above.
    $content = Get-Content $pom -Raw -Encoding UTF8
    $versionTag = "<version>$LocalVersion</version>"
    if ($content -notmatch [regex]::Escape($versionTag)) {
        $relPath = $pom.Substring($ProjectRoot.Length).TrimStart('\', '/')
        $Diverged += "  $relPath : version '$LocalVersion' not found"
    }
}

if ($Diverged.Count -gt 0) {
    Write-ErrorMsg "The following pom.xml files diverge from the root version ($LocalVersion):"
    foreach ($entry in $Diverged) {
        Write-ErrorMsg $entry
    }
    Die "All pom.xml files must have the same version as the root.  Fix them first and re-run."
}

$ChildCount = $PomFiles.Count - 1
Write-Info "  All $ChildCount child POMs agree with root ($LocalVersion)."

# ── 6. bump versions ────────────────────────────────────────────────────────

if ($DryRun) {
    Write-Info ""
    Write-Info "=== DRY RUN — no files will be modified ==="
    Write-Info "Would replace '$LocalVersion' → '$ExpectedVersion' in:"
    foreach ($pom in $PomFiles) {
        $relPath = $pom.Substring($ProjectRoot.Length).TrimStart('\', '/')
        Write-Host "  $relPath"
    }
    Write-Info "=== end dry run ==="
    exit 0
}

Write-Info "Bumping version from $LocalVersion → $ExpectedVersion …"

$EscapeOld = [regex]::Escape("<version>$LocalVersion</version>")
$Replacement = "<version>$ExpectedVersion</version>"

foreach ($pom in $PomFiles) {
    $relPath = $pom.Substring($ProjectRoot.Length).TrimStart('\', '/')
    $content = Get-Content $pom -Raw -Encoding UTF8
    $content = $content -replace $EscapeOld, $Replacement
    Set-Content $pom -Value $content -NoNewline -Encoding UTF8
    Write-Info "  Updated: $relPath"
}

Write-Info ""
Write-Info "Version bumped successfully: $LocalVersion → $ExpectedVersion"

# ── 7. commit if requested ──────────────────────────────────────────────────

if ($Commit) {
    Write-Info ""
    Write-Info "Committing version bump…"

    $commitMessage = "Bump local version from $LocalVersion to $ExpectedVersion"

    Push-Location $ProjectRoot
    try {
        foreach ($pom in $PomFiles) {
            $relPath = $pom.Substring($ProjectRoot.Length).TrimStart('\', '/')
            git add $relPath
        }
        git commit -m $commitMessage
    } finally {
        Pop-Location
    }

    Write-Info "Committed: $commitMessage"
} else {
    Write-Info "Review the changes with: git diff"
}
