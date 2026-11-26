export HOME=/data/data/com.termux/files/home
cat > "$HOME/start-itab.sh" <<'EOF'
#!/data/data/com.termux/files/usr/bin/sh
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
termux-wake-lock 2>/dev/null
pgrep sshd >/dev/null 2>&1 || sshd
if ! pgrep -f 'http.server 8081' >/dev/null 2>&1; then
  nohup proot-distro login --no-kill-on-exit alpine -- /bin/bash /root/start-services.sh >$HOME/alpine-server.log 2>&1 &
fi
if ! pgrep -f 'ttyd.*7681' >/dev/null 2>&1; then
  nohup ttyd -W -i 127.0.0.1 -p 7681 proot-distro login alpine >$HOME/ttyd.log 2>&1 &
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
proot-distro kill alpine 2>/dev/null
for p in $(pgrep -f 'ttyd.*7681' 2>/dev/null); do kill $p 2>/dev/null; done
for p in $(pgrep sshd 2>/dev/null); do kill $p 2>/dev/null; done
termux-wake-unlock 2>/dev/null
echo "ITAB server stopped: $(date)" >> $HOME/itab-server.log
EOF
chmod +x "$HOME/stop-itab.sh"

"$HOME/start-itab.sh"
sleep 3
echo '--- ttyd process ---'
ps -ef 2>/dev/null | grep ttyd | grep -v grep
echo '--- ttyd log ---'
tail -20 "$HOME/ttyd.log" 2>&1
