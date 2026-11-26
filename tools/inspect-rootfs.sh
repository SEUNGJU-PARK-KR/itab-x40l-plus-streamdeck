export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
R=$PREFIX/var/lib/proot-distro/containers/alpine/rootfs
echo "--- rootfs root ---"
ls -la "$R" | head -40
echo "--- usr bin ---"
ls -la "$R/usr/bin" 2>&1 | head -15
echo "--- bin link ---"
ls -la "$R/bin" "$R/usr/bin/python3" "$R/sbin/apk" 2>&1
echo "--- os release ---"
cat "$R/etc/alpine-release" 2>&1 | head -8
cat "$R/etc/os-release" 2>&1 | head -8
echo "--- login test ---"
proot-distro login alpine -- /usr/bin/env 2>&1 | head -30
