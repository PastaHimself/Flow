#!/usr/bin/env bash
set -euo pipefail

deb=${1:?DEB path is required}
work_dir=${2:?working directory is required}
rebuilt="${deb}.rebuilt"

if [[ ! -f "$deb" ]]; then
  echo "Expected Compose Desktop package at $deb" >&2
  exit 1
fi

rm -rf "$work_dir"
rm -f "$rebuilt"
trap 'rm -rf "$work_dir"; rm -f "$rebuilt"' EXIT
mkdir -p "$work_dir"
dpkg-deb -R "$deb" "$work_dir"

control="$work_dir/DEBIAN/control"
if [[ ! -f "$control" ]]; then
  echo "DEB control file was not extracted from $deb" >&2
  exit 1
fi

depends_lines=$(grep -c '^Depends:' "$control" || true)
if [[ "$depends_lines" -ne 1 ]]; then
  echo "Expected exactly one Depends field in $control, found $depends_lines" >&2
  exit 1
fi

trim() {
  local value=$1
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

has_dependency() {
  local dependencies=$1
  local package=$2
  printf '%s\n' "$dependencies" |
    tr ',|' '\n\n' |
    sed -E 's/^[[:space:]]*//; s/[[:space:]]*(\([^)]*\))?[[:space:]]*$//' |
    grep -Fx "$package" >/dev/null
}

depends=$(sed -n 's/^Depends:[[:space:]]*//p' "$control")
if ! has_dependency "$depends" "libasound2t64" && ! has_dependency "$depends" "libasound2"; then
  echo "Compose-generated DEB is missing the expected libasound2 dependency" >&2
  exit 1
fi
if ! has_dependency "$depends" "libpng16-16t64" && ! has_dependency "$depends" "libpng16-16"; then
  echo "Compose-generated DEB is missing the expected libpng16-16 dependency" >&2
  exit 1
fi

# Ubuntu 24.04's time_t transition renamed these runtime packages. The shared-library
# ABIs are the same on Debian 12 / Ubuntu 22.04, where the packages keep their old
# names, so express both valid package names as Debian alternatives.
normalized_depends=""
IFS=',' read -ra dependency_groups <<< "$depends"
for raw_group in "${dependency_groups[@]}"; do
  group=$(trim "$raw_group")
  if has_dependency "$group" "libasound2t64" || has_dependency "$group" "libasound2"; then
    group="libasound2t64 | libasound2"
  elif has_dependency "$group" "libpng16-16t64" || has_dependency "$group" "libpng16-16"; then
    group="libpng16-16t64 | libpng16-16"
  fi
  normalized_depends="${normalized_depends:+$normalized_depends, }$group"
done

for runtime_dependency in mpv yt-dlp ffmpeg; do
  if ! has_dependency "$normalized_depends" "$runtime_dependency"; then
    normalized_depends="$normalized_depends, $runtime_dependency"
  fi
done

for required_dependency in libasound2t64 libasound2 libpng16-16t64 libpng16-16 mpv yt-dlp ffmpeg; do
  if ! has_dependency "$normalized_depends" "$required_dependency"; then
    echo "Failed to normalize required dependency '$required_dependency'" >&2
    exit 1
  fi
done

awk -v dependencies="$normalized_depends" '
  /^Depends:/ { print "Depends: " dependencies; next }
  { print }
' "$control" > "$control.tmp"
mv "$control.tmp" "$control"

dpkg-deb --root-owner-group --build "$work_dir" "$rebuilt" >/dev/null

rebuilt_depends=$(dpkg-deb -f "$rebuilt" Depends)
for required_dependency in libasound2t64 libasound2 libpng16-16t64 libpng16-16 mpv yt-dlp ffmpeg; do
  if ! has_dependency "$rebuilt_depends" "$required_dependency"; then
    echo "Rebuilt DEB is missing required dependency '$required_dependency'" >&2
    exit 1
  fi
done

mv "$rebuilt" "$deb"
