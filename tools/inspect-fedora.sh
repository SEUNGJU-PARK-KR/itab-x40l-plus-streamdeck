export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
echo "--- containers ---"
ls -la $PREFIX/var/lib/proot-distro/containers/alpine | head -30
echo "--- usr ---"
ls -la $PREFIX/var/lib/proot-distro/containers/alpine/usr 2>&1 | head -20
echo "--- help ---"
proot-distro --help 2>&1 | head -80
echo "--- login help ---"
proot-distro login --help 2>&1 | head -80
