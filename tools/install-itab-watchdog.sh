export HOME=/data/data/com.termux/files/home
cat > "$HOME/start-itab.sh" <<'EOF'
#!/data/data/com.termux/files/usr/bin/sh
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
touch "$HOME/.itab-server-enabled"
termux-wake-lock 2>/dev/null
pgrep sshd >/dev/null 2>&1 || sshd
if ! pgrep -f 'http.server 8081' >/dev/null 2>&1; then
  nohup proot-distro login --no-kill-on-exit alpine -- /bin/bash /root/start-services.sh >$HOME/alpine-server.log 2>&1 &
fi
if ! pgrep -f 'ttyd.*7681' >/dev/null 2>&1; then
  nohup ttyd -W -i 127.0.0.1 -p 7681 proot-distro login alpine >$HOME/ttyd.log 2>&1 &
fi
if ! pgrep -f 'itab-watchdog.sh' >/dev/null 2>&1; then
  nohup "$HOME/itab-watchdog.sh" >$HOME/itab-watchdog.log 2>&1 &
fi
echo "ITAB server started: $(date)" >> $HOME/itab-server.log
EOF
chmod +x "$HOME/start-itab.sh"

cat > "$HOME/stop-itab.sh" <<'EOF'
#!/data/data/com.termux/files/usr/bin/sh
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
rm -f "$HOME/.itab-server-enabled"
for p in $(pgrep -f 'itab-watchdog.sh' 2>/dev/null); do kill $p 2>/dev/null; done
proot-distro kill alpine 2>/dev/null
for p in $(pgrep -f 'ttyd.*7681' 2>/dev/null); do kill $p 2>/dev/null; done
for p in $(pgrep sshd 2>/dev/null); do kill $p 2>/dev/null; done
termux-wake-unlock 2>/dev/null
echo "ITAB server stopped: $(date)" >> $HOME/itab-server.log
EOF
chmod +x "$HOME/stop-itab.sh"

cat > "$HOME/itab-watchdog.sh" <<'EOF'
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
EOF
chmod +x "$HOME/itab-watchdog.sh"

"$HOME/start-itab.sh"
sleep 3
echo '--- processes ---'
ps -ef 2>/dev/null | grep -E 'itab-watchdog|ttyd|http.server|sshd' | grep -v grep
echo '--- network log ---'
tail -5 "$HOME/itab-network.log" 2>&1
