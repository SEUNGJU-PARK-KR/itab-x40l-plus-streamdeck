export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
export https_proxy=http://127.0.0.1:18080
export http_proxy=http://127.0.0.1:18080
if ! command -v ip >/dev/null 2>&1; then
  yes 2>/dev/null | apt install -y iproute2 2>&1 | tail -20
fi
echo -n 'ip: '; command -v ip || true
ip -4 addr show wlan0 2>&1 | head -5
