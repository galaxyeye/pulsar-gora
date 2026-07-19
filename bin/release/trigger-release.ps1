#!/usr/bin/env pwsh

<#
.SYNOPSIS
Create and push a release tag to trigger the CI release workflow.

.DESCRIPTION
1. Read the current version from the root pom.xml.
2. Strip -SNAPSHOT suffix to produce the release version.
3. Show changes since the previous tag.
4. Create and push a lightweight or annotated tag to origin.

.PARAMETER remote
The remote to push the tag to (default: origin).

.PARAMETER message
Optional message for an annotated tag. If omitted, a lightweight tag is created.

.PARAMETER Yes
Skip all confirmation prompts (non-interactive mode).
#>

param(
    [string]$remote = "origin",
    [string]$message = "",
    [switch]$Yes
)

$ErrorActionPreference = "Stop"

$repoRoot = (git rev-parse --show-toplevel 2>$null)
Push-Location $repoRoot

# ── helpers ──────────────────────────────────────────────────────────────────

function Write-Warn { Write-Host "WARN: $args" -ForegroundColor Yellow }
function Write-ErrorMsg { Write-Host "ERROR: $args" -ForegroundColor Red }
function Write-Info  { Write-Host "$args" -ForegroundColor Green }

function Die {
    Write-ErrorMsg $args
    Pop-Location
    exit 1
}

# ── preflight checks ─────────────────────────────────────────────────────────

Write-Info "Working in: $repoRoot"

if (-not (Test-Path ".git")) {
    Die "Not a git repository"
}

$branch = git rev-parse --abbrev-ref HEAD
Write-Host "Current branch: $branch"

# Check for uncommitted changes
$status = git status --porcelain
if ($status) {
    Write-Warn "Uncommitted changes detected"
    if (-not $Yes) {
        $continue = Read-Host "Continue anyway? (y/n)"
        if ($continue -ne 'y') {
            Write-Host "Cancelled"
            Pop-Location
            exit 0
        }
    } else {
        Write-Host "Auto-continuing (-Yes)"
    }
}

# ── read version from root pom.xml ───────────────────────────────────────────

$RootPom = Join-Path $repoRoot "pom.xml"
if (-not (Test-Path $RootPom)) {
    Die "Root pom.xml not found at $RootPom"
}

$pomContent = Get-Content $RootPom -Raw -Encoding UTF8

# Strip <parent> block so we match the project-level <version>, not the parent POM
$strippedContent = $pomContent -replace '(?s)<parent>.*?</parent>', ''
if ($strippedContent -match '<version>([^<]+)</version>') {
    $rawVersion = $Matches[1]
} else {
    Die "Could not extract version from root pom.xml."
}

Write-Host "Version from pom.xml: $rawVersion"

# Strip -SNAPSHOT suffix for the release tag
if ($rawVersion -match '^(.+)-SNAPSHOT$') {
    $version = $Matches[1]
    Write-Info "Release version (SNAPSHOT stripped): $version"
} else {
    $version = $rawVersion
}

# Validate version format (supports x.y.z and x.y.z-rc.N)
if ($version -notmatch "^\d+\.\d+\.\d+(?:-rc\.\d+)?$") {
    Die "Invalid version format: '$version'. Expected X.Y.Z or X.Y.Z-rc.N"
}

$newTag = "v$version"

# ── check if tag already exists ──────────────────────────────────────────────

$existingTag = git tag -l $newTag
if ($existingTag) {
    Write-Warn "Tag '$newTag' already exists"

    if (-not $Yes) {
        $confirm = Read-Host "Do you want to overwrite it? (y/n)"
        if ($confirm -ne 'y') {
            Write-Host "Cancelled"
            Pop-Location
            exit 0
        }
    }
    try {
        git tag -d $newTag
        Write-Info "Deleted local tag: $newTag"

        $remoteTag = git ls-remote --tags $remote "refs/tags/$newTag" 2>$null
        if ($remoteTag) {
            git push $remote --delete $newTag
            Write-Info "Deleted remote tag: $newTag"
        }
    } catch {
        Die "Failed to delete existing tag: $_"
    }
}

# ── show changes since previous tag ──────────────────────────────────────────

function Get-TagSortKey {
    param([string]$Tag)

    $clean = $Tag -replace '^v',''
    if ($clean -notmatch '^(?<base>\d+\.\d+\.\d+)(?:-rc\.(?<rc>\d+))?$') {
        return $null
    }

    $baseVersion = [version]$matches['base']
    $rcValue = if ($matches['rc']) { [int]$matches['rc'] } else { [int]::MaxValue }

    return [pscustomobject]@{
        Base = $baseVersion
        Rc = $rcValue
    }
}

$tagCandidates = git tag --list | Where-Object { $_ -match '^(v\d+\.\d+\.\d+|\d+\.\d+\.\d+-rc\.\d+)$' }
$prevTag = $tagCandidates |
    ForEach-Object {
        $key = Get-TagSortKey $_
        if ($key) {
            [pscustomobject]@{ Tag = $_; Base = $key.Base; Rc = $key.Rc }
        }
    } |
    Sort-Object Base, Rc -Descending |
    Select-Object -First 1 |
    ForEach-Object { $_.Tag }

if ($prevTag) {
    Write-Info "`nChanges since $prevTag :"
    $changes = git log --oneline --no-merges "$prevTag..HEAD"
    if ($changes) {
        $changes | ForEach-Object { Write-Host "  - $_" }
    } else {
        Write-Host "  No changes"
    }
} else {
    Write-Info "`nRecent commits:"
    git log --oneline --no-merges -5 | ForEach-Object { Write-Host "  • $_" }
}

# ── prompt for tag message if not provided ───────────────────────────────────

if ([string]::IsNullOrWhiteSpace($message) -and -not $Yes) {
    Write-Host ""
    $message = Read-Host "Enter release message (optional, press Enter to skip)"
}

# ── confirm and push ─────────────────────────────────────────────────────────

Write-Host ""
$tagType = if ([string]::IsNullOrWhiteSpace($message)) { "lightweight" } else { "annotated" }
if (-not $Yes) {
    $confirm = Read-Host "Create and push $tagType tag '$newTag'? (y/n)"
    if ($confirm -ne 'y') {
        Write-Host "Cancelled"
        Pop-Location
        exit 0
    }
}

try {
    if ([string]::IsNullOrWhiteSpace($message)) {
        git tag $newTag
        Write-Info "Created lightweight tag: $newTag"
    } else {
        git tag -a $newTag -m $message
        Write-Info "Created annotated tag: $newTag"
    }

    git push $remote $newTag
    Write-Info "Successfully pushed tag: $newTag"

    # Try to show GitHub release URL
    $remoteUrl = git config --get remote.$remote.url
    if ($remoteUrl -match 'github\.com[:/](.+?)(?:\.git)?$') {
        $repo = $matches[1]
        Write-Info "Release URL: https://github.com/$repo/releases/tag/$newTag"
        Write-Info "Actions:    https://github.com/$repo/actions"
    }

    Write-Output $newTag
} catch {
    Die "Failed to create/push tag: $_"
} finally {
    Pop-Location
}
