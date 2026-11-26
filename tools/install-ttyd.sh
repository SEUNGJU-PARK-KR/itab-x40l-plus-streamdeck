export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
echo '--- direct repo test ---'
curl -I -L --max-time 8 https://packages-cf.termux.dev/apt/termux-main/ 2>&1 | head -3
echo '--- ttyd package ---'
apt-cache show ttyd 2>&1 | head -12
echo '--- install ---'
apt-get install -y ttyd 2>&1
echo '--- result ---'
command -v ttyd 2>&1
ttyd --version 2>&1 | head -2
