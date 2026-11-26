export PATH=/data/data/com.termux/files/usr/bin
echo -n 'ip: '; command -v ip || true
echo -n 'ifconfig: '; command -v ifconfig || true
echo '[ip addr]'
ip -4 addr show wlan0 2>&1 | head -5
echo '[ifconfig]'
ifconfig wlan0 2>&1 | head -8
echo '[watchdog log]'
tail -20 /data/data/com.termux/files/home/itab-watchdog.log 2>&1
