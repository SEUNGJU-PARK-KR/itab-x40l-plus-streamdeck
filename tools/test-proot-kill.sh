export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
echo '--- kill help ---'
proot-distro kill --help 2>&1 | head -30
echo '--- kill alpine ---'
proot-distro kill alpine 2>&1
sleep 2
echo '--- remaining ---'
ps -ef 2>/dev/null | grep -E 'proot|http.server' | grep -v grep
