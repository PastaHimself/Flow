#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT

package_root="$root/package"
mkdir -p "$package_root/DEBIAN"
cat > "$package_root/DEBIAN/control" <<'EOF'
Package: flow-normalizer-test
Version: 1.0-1
Architecture: amd64
Maintainer: Flow Test <test@example.invalid>
Description: Normalizer fixture
Depends: libasound2 (>= 1.2) | custom-audio,
 libpng16-16 (>= 1.6) | custom-png, libc6, yt-dlp

EOF

deb="$root/fixture.deb"
dpkg-deb --root-owner-group --build "$package_root" "$deb" >/dev/null

assert_dependency() {
  local dependencies=$1
  local package=$2
  printf '%s\n' "$dependencies" |
    tr ',|' '\n\n' |
    sed -E 's/^[[:space:]]*//; s/[[:space:]]*(\([^)]*\))?[[:space:]]*$//' |
    grep -Fx "$package" >/dev/null
}

bash "$script_dir/normalize-deb-dependencies.sh" "$deb" "$root/work"
first=$(dpkg-deb -f "$deb" Depends)

for dependency in libasound2t64 libasound2 custom-audio libpng16-16t64 libpng16-16 custom-png libc6 mpv ffmpeg; do
  assert_dependency "$first" "$dependency"
done
if assert_dependency "$first" yt-dlp; then
  echo "yt-dlp should be recommended rather than required" >&2
  exit 1
fi
first_recommends=$(dpkg-deb -f "$deb" Recommends)
assert_dependency "$first_recommends" yt-dlp

contents=$(dpkg-deb --contents "$deb")
grep -Eq '[[:space:]]\./usr/share/applications/$' <<< "$contents"
grep -Eq '[[:space:]]\./usr/share/desktop-directories/$' <<< "$contents"

bash "$script_dir/normalize-deb-dependencies.sh" "$deb" "$root/work"
second=$(dpkg-deb -f "$deb" Depends)
second_recommends=$(dpkg-deb -f "$deb" Recommends)

if [[ "$first" != "$second" ]]; then
  printf 'Dependency normalization is not idempotent.\nFirst:  %s\nSecond: %s\n' "$first" "$second" >&2
  exit 1
fi
if [[ "$first_recommends" != "$second_recommends" ]]; then
  printf 'Recommends normalization is not idempotent.\nFirst:  %s\nSecond: %s\n' "$first_recommends" "$second_recommends" >&2
  exit 1
fi
