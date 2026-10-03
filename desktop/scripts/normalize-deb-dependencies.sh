#!/usr/bin/env bash
set -euo pipefail

deb=${1:?DEB path is required}
work_dir=${2:?working directory is required}

if [[ ! -f "$deb" ]]; then
  echo "Expected Compose Desktop package at $deb" >&2
  exit 1
fi

rm -rf "$work_dir"
mkdir -p "$work_dir"
dpkg-deb -R "$deb" "$work_dir"

control="$work_dir/DEBIAN/control"
if [[ ! -f "$control" ]]; then
  echo "DEB control file was not extracted from $deb" >&2
  exit 1
fi

# Ubuntu 24.04's time_t transition renamed these runtime packages. The shared-library
# ABIs are the same on Debian 12 / Ubuntu 22.04, where the packages keep their old
# names, so express both valid package names as Debian alternatives.
sed -E -i \
  -e 's/libasound2t64( \| libasound2)*/libasound2t64 | libasound2/g' \
  -e 's/libpng16-16t64( \| libpng16-16)*/libpng16-16t64 | libpng16-16/g' \
  "$control"

rebuilt="${deb}.rebuilt"
rm -f "$rebuilt"
dpkg-deb --root-owner-group --build "$work_dir" "$rebuilt" >/dev/null
mv "$rebuilt" "$deb"
rm -rf "$work_dir"
