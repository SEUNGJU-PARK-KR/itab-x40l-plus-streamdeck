export HOME=/data/data/com.termux/files/home
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
echo updated
