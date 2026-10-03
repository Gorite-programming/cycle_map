#!/usr/bin/env bash
# =============================================================================
# scripts/push_data.sh - Deploy CycleMap offline data to connected Android device
# =============================================================================
#
# Target directory on device:
#   /sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap/
#   (getExternalFilesDir(DOCUMENTS)/CycleMap/)
#
# Files deployed per prefecture:
#   - <Prefecture>.search.db (Overture + MLIT + OSM integrated POI search database)
#   - <Prefecture>.graph     (OSM routing graph)
#   - <Prefecture>.graph.idx (Routing graph node index)
#
# =============================================================================
set -euo pipefail

DEST_DIR="/sdcard/Android/data/com.gorite.cyclemap/files/Documents/CycleMap"
PACKAGE_NAME="com.gorite.cyclemap"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

DRY_RUN=false
DB_ONLY=false
TARGET_PREF=""

ALL_PREFS=("Tottori" "Shimane" "Okayama" "Hiroshima" "Yamaguchi")

usage() {
    cat <<EOF
Usage: $0 [options]

Deploy CycleMap offline data (*.search.db, *.graph, *.graph.idx) to an Android device.

Options:
  --dry-run       Show transfer plan and local hashes without making any changes
  --db-only       Transfer only search.db files (skip routing graph files)
  --pref <name>   Transfer single prefecture (e.g. Yamaguchi, Hiroshima, Okayama, Shimane, Tottori)
  -h, --help      Show this help message
EOF
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --db-only)
            DB_ONLY=true
            shift
            ;;
        --pref)
            TARGET_PREF="$2"
            shift 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage
            ;;
    esac
done

calc_sha256() {
    local file="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$file" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file" | awk '{print $1}'
    else
        python3 -c "import hashlib, sys; print(hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest())" "$file"
    fi
}

human_size() {
    local bytes="$1"
    python3 -c "
import sys
b = int(sys.argv[1])
if b >= 1073741824:
    print(f'{b/1073741824:.2f} GiB')
elif b >= 1048576:
    print(f'{b/1048576:.2f} MiB')
elif b >= 1024:
    print(f'{b/1024:.2f} KiB')
else:
    print(f'{b} B')
" "$bytes"
}

echo "=== CycleMap Data Deployment ==="
echo "Destination: ${DEST_DIR}"
echo "Mode: $(if [[ "$DRY_RUN" == true ]]; then echo "DRY RUN (no device changes)"; else echo "LIVE DEPLOY"; fi)"
if [[ "$DB_ONLY" == true ]]; then
    echo "Filter: search.db only"
fi

if [[ -n "$TARGET_PREF" ]]; then
    # Capitalize first letter
    TARGET_PREF="$(tr '[:lower:]' '[:upper:]' <<< "${TARGET_PREF:0:1}")$(tr '[:upper:]' '[:lower:]' <<< "${TARGET_PREF:1}")"
    PREFS=("$TARGET_PREF")
else
    PREFS=("${ALL_PREFS[@]}")
fi

# Check adb device connection if live mode
if [[ "$DRY_RUN" == false ]]; then
    if ! command -v adb >/dev/null 2>&1; then
        echo "ERROR: adb command not found on PATH." >&2
        exit 1
    fi
    DEVICE_LIST=$(adb devices | grep -v "List of devices" | grep "device$" || true)
    if [[ -z "$DEVICE_LIST" ]]; then
        echo "ERROR: No connected Android device found in 'device' state." >&2
        echo "Please connect device via USB and ensure USB debugging is enabled." >&2
        adb devices
        exit 1
    fi
    DEVICE_ID=$(echo "$DEVICE_LIST" | head -n1 | awk '{print $1}')
    echo "Connected device: ${DEVICE_ID}"

    echo "Stopping ${PACKAGE_NAME} to safely write files..."
    adb shell am force-stop "${PACKAGE_NAME}" || true

    echo "Ensuring remote directory exists: ${DEST_DIR}"
    adb shell mkdir -p "${DEST_DIR}"
fi

TMP_STAGE="${ROOT_DIR}/work/_push_stage"
mkdir -p "${TMP_STAGE}"
cleanup() {
    rm -rf "${TMP_STAGE}"
}
trap cleanup EXIT

TOTAL_FILES=0
TOTAL_BYTES=0

echo ""
echo "--- Preparing and Transferring Files ---"

for pref in "${PREFS[@]}"; do
    pref_lower="$(tr '[:upper:]' '[:lower:]' <<< "$pref")"
    echo ">> Prefecture: ${pref}"

    # 1. Search DB (prefer sandbox/data-tool/out, fallback to packages zip)
    db_src="${ROOT_DIR}/sandbox/data-tool/out/${pref_lower}.search.db"
    db_staged="${TMP_STAGE}/${pref}.search.db"
    if [[ -f "$db_src" ]]; then
        cp -f "$db_src" "$db_staged"
    elif [[ -f "${ROOT_DIR}/packages/${pref}.zip" ]]; then
        unzip -p "${ROOT_DIR}/packages/${pref}.zip" "${pref}.search.db" > "$db_staged"
    else
        echo "WARNING: No search.db found for ${pref}" >&2
    fi

    # Files to transfer for this prefecture
    files_to_push=()
    if [[ -f "$db_staged" ]]; then
        files_to_push+=("$db_staged")
    fi

    # 2. Graph & Graph Index (from packages zip or work)
    if [[ "$DB_ONLY" == false ]]; then
        zip_file="${ROOT_DIR}/packages/${pref}.zip"
        graph_staged="${TMP_STAGE}/${pref}.graph"
        idx_staged="${TMP_STAGE}/${pref}.graph.idx"

        if [[ -f "$zip_file" ]]; then
            unzip -p "$zip_file" "${pref}.graph" > "$graph_staged" 2>/dev/null || true
            unzip -p "$zip_file" "${pref}.graph.idx" > "$idx_staged" 2>/dev/null || true
        fi
        if [[ ! -s "$graph_staged" && -f "${ROOT_DIR}/work/${pref}/${pref}.graph" ]]; then
            cp -f "${ROOT_DIR}/work/${pref}/${pref}.graph" "$graph_staged"
        fi
        if [[ ! -s "$idx_staged" && -f "${ROOT_DIR}/work/${pref}/${pref}.graph.idx" ]]; then
            cp -f "${ROOT_DIR}/work/${pref}/${pref}.graph.idx" "$idx_staged"
        fi

        if [[ -s "$graph_staged" ]]; then
            files_to_push+=("$graph_staged")
        fi
        if [[ -s "$idx_staged" ]]; then
            files_to_push+=("$idx_staged")
        fi
    fi

    for local_path in "${files_to_push[@]}"; do
        file_name="$(basename "$local_path")"
        file_bytes="$(wc -c < "$local_path" | tr -d ' ')"
        local_hash="$(calc_sha256 "$local_path")"
        remote_path="${DEST_DIR}/${file_name}"

        echo "  [FILE] ${file_name} ($(human_size "$file_bytes"))"
        echo "         SHA-256: ${local_hash}"

        TOTAL_FILES=$((TOTAL_FILES + 1))
        TOTAL_BYTES=$((TOTAL_BYTES + file_bytes))

        if [[ "$DRY_RUN" == true ]]; then
            echo "         -> Would push to: ${remote_path}"
        else
            echo "         -> Pushing to device..."
            adb push "$local_path" "$remote_path"

            echo "         -> Verifying on-device SHA-256..."
            remote_hash_line=$(adb shell sha256sum "$remote_path" 2>/dev/null || true)
            remote_hash=$(echo "$remote_hash_line" | awk '{print $1}' | tr -d '\r\n')

            if [[ "$local_hash" != "$remote_hash" ]]; then
                echo "ERROR: Hash mismatch for ${file_name}!" >&2
                echo "  Local:  ${local_hash}" >&2
                echo "  Remote: ${remote_hash}" >&2
                exit 1
            fi
            echo "         -> Verified OK (SHA-256 match)"
        fi
    done
done

echo ""
echo "=== Deployment Summary ==="
echo "Total files: ${TOTAL_FILES}"
echo "Total data size: $(human_size "$TOTAL_BYTES")"
if [[ "$DRY_RUN" == true ]]; then
    echo "Status: DRY RUN completed successfully (no files transferred)."
else
    echo "Status: ALL FILES TRANSFERRED AND VERIFIED SUCCESSFULLY."
fi
