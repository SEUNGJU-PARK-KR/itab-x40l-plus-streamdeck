#!/data/data/com.termux/files/usr/bin/sh
# Snapshot restorable state: alpine rootfs + termux home + package list
set -e
export PATH=/data/data/com.termux/files/usr/bin:$PATH
HOME=/data/data/com.termux/files/home
ROOTFS=/data/data/com.termux/files/usr/var/lib/proot-distro/containers/alpine
OUT=$HOME/snapshot-out
mkdir -p "$OUT"

echo "=== alpine rootfs ==="
du -sh "$ROOTFS/rootfs" 2>/dev/null || true

echo "=== creating alpine tarball ==="
tar czf "$OUT/alpine-rootfs.tar.gz" \
  --exclude='alpine/rootfs/data' --exclude='alpine/rootfs/apex' \
  --exclude='alpine/rootfs/odm' --exclude='alpine/rootfs/product' \
  --exclude='alpine/rootfs/system' --exclude='alpine/rootfs/system_ext' \
  --exclude='alpine/rootfs/vendor' --exclude='alpine/rootfs/linkerconfig' \
  --exclude='alpine/shm' \
  -C "$ROOTFS/.." alpine
ls -la "$OUT"

echo "=== ssh keys present? ==="
ls -la "$HOME/.ssh" 2>/dev/null || echo "no .ssh"

echo "=== termux home tarball ==="
tar czf "$OUT/termux-home.tar.gz" \
  --exclude='snapshot-out' --exclude='./snapshot-out' \
  --exclude='./snapshot-out/*' --exclude='*.tar.gz' \
  -C "$HOME" .
ls -la "$OUT"

echo "=== package list ==="
export PREFIX=/data/data/com.termux/files/usr
export HOME=/data/data/com.termux/files/home
export PATH=$PREFIX/bin:$PATH
export LD_LIBRARY_PATH=$PREFIX/lib
pkg list-installed > "$OUT/pkg-list.txt" 2>/dev/null || true
wc -l < "$OUT/pkg-list.txt"
echo DONE
