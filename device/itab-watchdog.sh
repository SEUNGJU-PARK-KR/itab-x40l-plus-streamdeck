#!/data/data/com.termux/files/usr/bin/bash
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
last_ip=""
while true; do
  if [ -f "$HOME/.itab-server-enabled" ]; then
    pgrep sshd >/dev/null 2>&1 || sshd
    if ! pgrep -f 'http.server 8081' >/dev/null 2>&1; then
      nohup proot-distro login --no-kill-on-exit alpine -- /bin/bash /root/start-services.sh >>$HOME/alpine-server.log 2>&1 &
    fi
    if ! pgrep -f 'ttyd.*7681' >/dev/null 2>&1; then
      nohup ttyd -W -i 127.0.0.1 -p 7681 proot-distro login alpine >>$HOME/ttyd.log 2>&1 &
    fi
    ip=$(ip -4 addr show wlan0 2>/dev/null | awk '/inet / {print $2}' | cut -d/ -f1 | head -1)
    if [ "$ip" != "$last_ip" ]; then
      echo "$(date) wlan0 ip: ${ip:-none}" >> "$HOME/itab-network.log"
      last_ip="$ip"
    fi
  fi
  sleep 20
done
