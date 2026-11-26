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
