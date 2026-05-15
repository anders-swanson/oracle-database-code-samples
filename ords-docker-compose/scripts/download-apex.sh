#!/usr/bin/env bash
set -euo pipefail

APEX_SOURCE_DIR="${APEX_SOURCE_DIR:-/apex-local}"
APEX_TARGET_DIR="${APEX_TARGET_DIR:-/apex-files}"
APEX_DOWNLOAD_URL="${APEX_DOWNLOAD_URL:-https://download.oracle.com/otn_software/apex/apex-latest.zip}"
APEX_DOWNLOAD_SHA256="${APEX_DOWNLOAD_SHA256:-}"

is_apex_enabled() {
  case "${APEX_ENABLED:-false}" in
    true | TRUE | 1 | yes | YES | Yes)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

reset_target_dir() {
  case "${APEX_TARGET_DIR}" in
    "" | "/" | "${APEX_SOURCE_DIR}")
      echo "ERROR: Refusing to clear unsafe APEX target directory: ${APEX_TARGET_DIR}" >&2
      exit 1
      ;;
  esac

  mkdir -p "${APEX_TARGET_DIR}"
  find "${APEX_TARGET_DIR}" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
}

copy_apex_files() {
  local source_dir="$1"

  reset_target_dir
  tar -C "${source_dir}" -cf - . | tar -C "${APEX_TARGET_DIR}" -xf -
}

verify_apex_files() {
  if [ ! -f "${APEX_TARGET_DIR}/apxsilentins.sql" ]; then
    echo "ERROR: APEX files were not installed into ${APEX_TARGET_DIR}; apxsilentins.sql is missing." >&2
    exit 1
  fi
}

patch_apex_silent_installer() {
  local installer="${APEX_TARGET_DIR}/apxsilentins.sql"
  local original_installer="${APEX_TARGET_DIR}/apxsilentins.oracle.sql"

  if [ ! -f "${original_installer}" ]; then
    if [ ! -f "${installer}" ]; then
      echo "ERROR: Cannot patch APEX installer because ${installer} is missing." >&2
      exit 1
    fi
    mv "${installer}" "${original_installer}"
  fi

  cat > "${installer}" <<'SQL'
Rem Wrapper for the ORDS container image.
Rem The image invokes apxsilentins.sql with weak hard-coded "oracle" passwords.
Rem APEX 26.1 rejects those values, so this wrapper calls the original installer
Rem with strong local-demo passwords while preserving ORDS' first four arguments.

set define '^'
set concat on
set verify off

define DATTS   = '^1'
define FF_TBLS = '^2'
define TEMPTBL = '^3'
define IMGPR   = '^4'
define PREFIX  = '@'

@^PREFIX.apxsilentins.oracle.sql ^DATTS ^FF_TBLS "^TEMPTBL" ^IMGPR Welcome12345!1 Welcome12345!2 Welcome12345!3 Welcome12345!4
SQL
}

if ! is_apex_enabled; then
  echo "APEX_ENABLED is not true; skipping APEX download."
  exit 0
fi

if [ -f "${APEX_TARGET_DIR}/apxsilentins.sql" ]; then
  echo "APEX files already exist in ${APEX_TARGET_DIR}; reusing the existing volume."
  patch_apex_silent_installer
  verify_apex_files
  exit 0
fi

if [ -f "${APEX_SOURCE_DIR}/apxsilentins.sql" ]; then
  echo "Using local APEX files from ${APEX_SOURCE_DIR}."
  copy_apex_files "${APEX_SOURCE_DIR}"
  patch_apex_silent_installer
  verify_apex_files
  exit 0
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

zip_file="${work_dir}/apex.zip"
extract_dir="${work_dir}/extract"

echo "Downloading APEX from ${APEX_DOWNLOAD_URL}."
curl --fail --location --show-error --output "${zip_file}" "${APEX_DOWNLOAD_URL}"

if [ -n "${APEX_DOWNLOAD_SHA256}" ]; then
  echo "${APEX_DOWNLOAD_SHA256}  ${zip_file}" | sha256sum -c -
else
  echo "WARNING: APEX_DOWNLOAD_SHA256 is not set; skipping checksum verification for the moving APEX download URL." >&2
fi

mkdir -p "${extract_dir}"
(
  cd "${extract_dir}"
  jar xf "${zip_file}"
)

if [ ! -f "${extract_dir}/apex/apxsilentins.sql" ]; then
  echo "ERROR: Downloaded archive did not contain apex/apxsilentins.sql." >&2
  exit 1
fi

copy_apex_files "${extract_dir}/apex"
patch_apex_silent_installer
verify_apex_files
echo "APEX files are ready in ${APEX_TARGET_DIR}."
