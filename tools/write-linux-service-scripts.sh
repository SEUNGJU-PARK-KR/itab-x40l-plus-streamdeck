export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
export TMPDIR=$HOME/tmp
mkdir -p "$HOME/.termux/boot" "$HOME/tmp"
mkdir -p "$PREFIX/var/lib/proot-distro/containers/alpine/rootfs/srv/www"
mkdir -p "$PREFIX/var/lib/proot-distro/containers/alpine/rootfs/var/log"
mkdir -p "$PREFIX/var/lib/proot-distro/containers/alpine/rootfs/root"

cat > "$PREFIX/var/lib/proot-distro/containers/alpine/rootfs/srv/www/index.html" <<'EOF'
<!doctype html>
<meta charset="utf-8">
<title>ITAB Linux Server</title>
<style>
body{font-family:sans-serif;background:#081120;color:#eaf2ff;display:grid;place-items:center;min-height:100vh;margin:0}
.card{padding:32px;border:1px solid #3e5d88;border-radius:24px;background:#101f35;box-shadow:0 20px 60px #0008;max-width:640px}
small{color:#9db3d5}
a{color:#7dd3fc}
</style>
<div class="card">
  <h1>ITAB Linux Server</h1>
  <p>Alpine Linux userland is running inside the Termux proot container.</p>
  <small>port 8081 | /srv/www | <a href="http://127.0.0.1:7681">web terminal</a></small>
</div>
EOF

cat > "$PREFIX/var/lib/proot-distro/containers/alpine/rootfs/root/start-services.sh" <<'EOF'
#!/bin/bash
mkdir -p /srv/www /var/log
if ! pgrep -f 'http.server 8081' >/dev/null 2>&1; then
  nohup python3 -m http.server 8081 --directory /srv/www >/var/log/itab-http.log 2>&1 &
fi
echo "ITAB Alpine services started: $(date)" >> /var/log/itab-services.log
tail -f /dev/null
EOF
chmod +x "$PREFIX/var/lib/proot-distro/containers/alpine/rootfs/root/start-services.sh"

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

cat > "$HOME/.termux/boot/start-itab.sh" <<'EOF'
#!/data/data/com.termux/files/usr/bin/sh
/data/data/com.termux/files/home/start-itab.sh
EOF
chmod +x "$HOME/.termux/boot/start-itab.sh"

echo '--- written ---'
ls -l "$HOME/start-itab.sh" "$HOME/.termux/boot/start-itab.sh" "$PREFIX/var/lib/proot-distro/containers/alpine/rootfs/root/start-services.sh"
