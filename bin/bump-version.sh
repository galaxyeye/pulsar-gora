#!/usr/bin/env bash
#
# bump-version.sh — Bump the local project version to the next patch after the
# latest Maven Central release.
#
# Usage:
#   ./bin/bump-version.sh [--dry-run] [--yes]
#
#   --dry-run   Print what would be changed without writing anything.
#   --yes       Skip the confirmation prompt (non-interactive mode).
#
# Flow:
#   1. Query Maven Central for the latest released version of gora-core.
#   2. Compute the expected next-patch version (latest + 1 patch).
#   3. If the local version (from root pom.xml) is not that expected version,
#      warn and ask for confirmation (unless --yes).
#   4. Verify every pom.xml in the project agrees with the root pom.xml.
#      If any child POM diverges, warn and exit.
#   5. Bump the version in all pom.xml files (or just report with --dry-run).
#
set -euo pipefail

# ── helpers ──────────────────────────────────────────────────────────────────

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

warn()  { printf "${YELLOW}WARN: %s${NC}\n" "$*" >&2; }
error() { printf "${RED}ERROR: %s${NC}\n" "$*" >&2; }
info()  { printf "${GREEN}%s${NC}\n" "$*"; }

die() {
  error "$*"
  exit 1
}

# ── args ─────────────────────────────────────────────────────────────────────

DRY_RUN=false
SKIP_CONFIRM=false

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    --yes)     SKIP_CONFIRM=true ;;
    -h|--help)
      sed -n '2,/^$/p' "$0" | sed 's/^# \?//'
      exit 0
      ;;
    *)
      die "Unknown argument: $arg  (use --help for usage)"
      ;;
  esac
done

# ── paths ────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT_POM="$PROJECT_ROOT/pom.xml"

[[ -f "$ROOT_POM" ]] || die "Root pom.xml not found at $ROOT_POM"

# ── maven coordinates ────────────────────────────────────────────────────────

GROUP_ID="ai.platon.gora"
ARTIFACT_ID="gora-core"
MAVEN_METADATA_URL="https://repo1.maven.org/maven2/${GROUP_ID//.//}/${ARTIFACT_ID}/maven-metadata.xml"

# ── 1. fetch latest released version from Maven Central ──────────────────────

info "Fetching latest released version from Maven Central…"
info "  URL: $MAVEN_METADATA_URL"

METADATA=$(curl -sS --connect-timeout 10 --max-time 30 "$MAVEN_METADATA_URL") ||
  die "Failed to fetch maven-metadata.xml from Maven Central."

# Extract the <latest> or <release> element.  Prefer <latest>; fall back to
# the last <version> that is not a snapshot / does not contain "SNAPSHOT".
LATEST_RELEASED=$(echo "$METADATA" |
  sed -n 's/.*<latest>\(.*\)<\/latest>.*/\1/p' |
  head -1)

if [[ -z "$LATEST_RELEASED" ]]; then
  # Fallback: pick the last non-snapshot <version>
  LATEST_RELEASED=$(echo "$METADATA" |
    grep -o '<version>[^<]*</version>' |
    sed 's/<[^>]*>//g' |
    grep -vi 'snapshot' |
    tail -1)
fi

[[ -n "$LATEST_RELEASED" ]] || die "Could not determine latest released version from Maven metadata."
info "  Latest released: $LATEST_RELEASED"

# ── 2. compute expected next-patch version ──────────────────────────────────

# Split the version into  numeric-prefix and optional -suffix.
# E.g. "1.0.2-slim" → prefix="1.0.2"  suffix="-slim"
#      "1.0.2"       → prefix="1.0.2"  suffix=""
if [[ "$LATEST_RELEASED" =~ ^([0-9]+\.[0-9]+\.)([0-9]+)(-.*)?$ ]]; then
  MAJOR_MINOR="${BASH_REMATCH[1]}"          # e.g. "1.0."
  PATCH="${BASH_REMATCH[2]}"                # e.g. "2"
  SUFFIX="${BASH_REMATCH[3]:-}"             # e.g. "-slim" or ""
  NEXT_PATCH=$((PATCH + 1))
  EXPECTED_VERSION="${MAJOR_MINOR}${NEXT_PATCH}${SUFFIX}"
else
  die "Cannot parse latest released version: '$LATEST_RELEASED'.  Expected format: X.Y.Z[-suffix]"
fi

info "  Expected next patch: $EXPECTED_VERSION"

# ── 3. read local version from root pom.xml ─────────────────────────────────

# Strip the <parent>…</parent> block first so we pick up the project-level
# <version>, not the parent POM version inherited from org.apache:apache.
LOCAL_VERSION=$(sed '/<parent>/,/<\/parent>/d' "$ROOT_POM" |
  sed -n 's|.*<version>\([^<]*\)</version>.*|\1|p' |
  head -1)

[[ -n "$LOCAL_VERSION" ]] || die "Could not extract version from root pom.xml."
info "  Local version:       $LOCAL_VERSION"

# ── 4. compare and (maybe) confirm ──────────────────────────────────────────

if [[ "$LOCAL_VERSION" == "$EXPECTED_VERSION" ]]; then
  info "Local version already matches the expected next patch.  Nothing to bump."
  exit 0
fi

warn "Local version ($LOCAL_VERSION) does NOT match expected next patch ($EXPECTED_VERSION)."
warn "Latest Maven Central release is $LATEST_RELEASED, so the next development"
warn "version should be $EXPECTED_VERSION."

if [[ "$SKIP_CONFIRM" != "true" ]]; then
  printf "\nBump local version from %s to %s? [y/N] " "$LOCAL_VERSION" "$EXPECTED_VERSION"
  read -r REPLY
  case "$REPLY" in
    [yY]|[yY][eE][sS]) ;;
    *) die "Aborted by user." ;;
  esac
fi

# ── 5. verify all POMs are consistent with root BEFORE changing anything ─────

info "Verifying all pom.xml files agree with root…"

mapfile -t POM_FILES < <(find "$PROJECT_ROOT" -maxdepth 2 -name 'pom.xml' | sort)

DIVERGED=()
for pom in "${POM_FILES[@]}"; do
  # Skip the root — it's our reference.
  [[ "$pom" == "$ROOT_POM" ]] && continue

  # A child POM is "consistent" if $LOCAL_VERSION appears anywhere in a
  # <version> element.  This covers three cases:
  #   1. Explicit project <version> (most modules).
  #   2. Version inherited via <parent><version> (modules without explicit version).
  #   3. Both of the above.
  if ! grep -q "<version>$LOCAL_VERSION</version>" "$pom"; then
    DIVERGED+=("  $(realpath --relative-to="$PROJECT_ROOT" "$pom"): version '$LOCAL_VERSION' not found")
  fi
done

if [[ ${#DIVERGED[@]} -gt 0 ]]; then
  error "The following pom.xml files diverge from the root version ($LOCAL_VERSION):"
  for entry in "${DIVERGED[@]}"; do
    error "$entry"
  done
  die "All pom.xml files must have the same version as the root.  Fix them first and re-run."
fi

info "  All $((${#POM_FILES[@]} - 1)) child POMs agree with root ($LOCAL_VERSION)."

# ── 6. bump versions ────────────────────────────────────────────────────────

if [[ "$DRY_RUN" == "true" ]]; then
  info ""
  info "=== DRY RUN — no files will be modified ==="
  info "Would replace '$LOCAL_VERSION' → '$EXPECTED_VERSION' in:"
  for pom in "${POM_FILES[@]}"; do
    printf "  %s\n" "$(realpath --relative-to="$PROJECT_ROOT" "$pom")"
  done
  info "=== end dry run ==="
  exit 0
fi

info "Bumping version from $LOCAL_VERSION → $EXPECTED_VERSION …"

for pom in "${POM_FILES[@]}"; do
  REL=$(realpath --relative-to="$PROJECT_ROOT" "$pom")
  # Replace all occurrences of the old version string.
  # This correctly handles:
  #   - Root POM:    parent is org.apache:apache:38 (no match), only project version changes.
  #   - Child POMs:  parent/version, project/version, and internal dependency
  #                   references (e.g. gora-core-shaded) all get bumped together.
  sed -i "s|<version>$LOCAL_VERSION</version>|<version>$EXPECTED_VERSION</version>|g" "$pom"
  info "  Updated: $REL"
done

info ""
info "Version bumped successfully: $LOCAL_VERSION → $EXPECTED_VERSION"
info "Review the changes with: git diff"
