export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
echo '--- sizes ---'
du -sh "$PREFIX/var/lib/proot-distro/containers/alpine" 2>/dev/null || true
du -sh "$PREFIX" 2>/dev/null || true
du -sh "$HOME" 2>/dev/null || true
echo '--- processes ---'
ps -ef 2>/dev/null | grep -E 'itab-watchdog|ttyd|http.server|sshd' | grep -v grep || true
echo '--- ports ---'
for p in 8022 8081 7681; do
  echo -n "$p "
  (echo > /dev/tcp/127.0.0.1/$p) >/dev/null 2>&1 && echo open || echo closed
done
echo '--- alpine ---'
proot-distro login alpine -- /bin/sh -lc 'cat /etc/alpine-release 2>/dev/null; python3 --version 2>/dev/null' 2>&1
