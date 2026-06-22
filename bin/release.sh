#!/bin/bash
#
# release.sh — Tag and deploy a Gora release to Maven Central (Sonatype)
#
# Usage:
#   ./bin/release.sh                  # derive version from pom.xml, strip -SNAPSHOT
#   ./bin/release.sh 1.0.2-slim       # explicit release version
#
# What it does:
#   1. Strips -SNAPSHOT from the current POM version (if present)
#   2. Updates all pom.xml files to the release version
#   3. Creates an annotated Git tag (v<version>)
#   4. Runs "mvn clean deploy" with the "release" profile (sources, javadoc, GPG,
#      checksums, Sonatype Central publisher)
#
# Prerequisites:
#   - GPG key configured in ~/.m2/settings.xml (server id "central")
#   - Sonatype Central credentials in ~/.m2/settings.xml
#   - Working tree is clean (script will refuse to run otherwise)
#   - JDK 17+

set -euo pipefail

# ── helpers ──────────────────────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

log()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()   { echo -e "${YELLOW}[WARN]${NC}  $*"; }
die()    { echo -e "${RED}[FATAL]${NC} $*" >&2; exit 1; }

# ── pre-flight checks ────────────────────────────────────────────────────────

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# Must be a git repo
git rev-parse --git-dir >/dev/null 2>&1 || die "Not inside a git repository."

# Working tree must be clean
if ! git diff-index --quiet HEAD -- 2>/dev/null; then
  die "Working tree has uncommitted changes. Please commit or stash them first."
fi

# Must be on a branch (not detached HEAD) — soft check
BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "HEAD")
if [ "$BRANCH" = "HEAD" ]; then
  warn "You are on a detached HEAD. Continuing, but make sure that's intentional."
fi

# ── determine release version ────────────────────────────────────────────────

# Extract current version from the root pom.xml.
# We use awk to skip <version> inside the <parent> block — the project <version>
# is the one after </packaging> (or the first direct <version> child of <project>
# that is not inside <parent>).
CURRENT_VERSION=$(awk '
  /<parent>/ { in_parent=1 }
  /<\/parent>/ { in_parent=0; next }
  !in_parent && /^\s*<version>/ { gsub(/.*<version>|<\/version>.*/, ""); print; exit }
' pom.xml)

if [ -z "$CURRENT_VERSION" ]; then
  die "Could not extract version from pom.xml"
fi

if [ $# -ge 1 ]; then
  RELEASE_VERSION="$1"
  log "Version supplied on command line: ${RELEASE_VERSION}"
else
  # Strip -SNAPSHOT suffix if present
  RELEASE_VERSION="${CURRENT_VERSION%-SNAPSHOT}"
  if [ "$RELEASE_VERSION" != "$CURRENT_VERSION" ]; then
    log "Stripped -SNAPSHOT suffix: ${CURRENT_VERSION} -> ${RELEASE_VERSION}"
  else
    log "Version is already a release version: ${RELEASE_VERSION}"
  fi
fi

TAG_NAME="v${RELEASE_VERSION}"

# ── confirm ──────────────────────────────────────────────────────────────────

echo ""
echo -e "  ${BOLD}Branch:${NC}  ${BRANCH}"
echo -e "  ${BOLD}Tag:${NC}     ${TAG_NAME}"
echo -e "  ${BOLD}Version:${NC} ${RELEASE_VERSION}"
echo ""

read -r -p "Proceed with release? [y/N] " REPLY
if [ "${REPLY,,}" != "y" ] && [ "${REPLY,,}" != "yes" ]; then
  die "Aborted by user."
fi

# ── update version (if it changed) ───────────────────────────────────────────

if [ "$RELEASE_VERSION" != "$CURRENT_VERSION" ]; then
  log "Setting POM versions to ${RELEASE_VERSION} via mvn versions:set ..."
  mvn versions:set -DnewVersion="${RELEASE_VERSION}" -DgenerateBackupPoms=false
  log "Committing version change ..."
  git add . && git commit -m "chore: set release version ${RELEASE_VERSION}"
fi

# ── create tag ───────────────────────────────────────────────────────────────

# Check if tag already exists
if git rev-parse "${TAG_NAME}" >/dev/null 2>&1; then
  warn "Tag ${TAG_NAME} already exists, skipping tag creation."
else
  log "Creating annotated tag ${TAG_NAME} ..."
  git tag -a "${TAG_NAME}" -m "Release ${RELEASE_VERSION}"
  log "Tag ${TAG_NAME} created on commit $(git rev-parse --short HEAD)."
fi

# ── deploy ───────────────────────────────────────────────────────────────────

log "Running: mvn clean deploy -P release -DskipTests ..."
echo ""

mvn clean deploy -P release -DskipTests

echo ""
log "Release ${RELEASE_VERSION} completed successfully."
log "Tag: ${TAG_NAME}"
