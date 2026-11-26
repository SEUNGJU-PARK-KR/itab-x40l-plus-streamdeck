export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
echo '--- stop ---'
"$HOME/stop-itab.sh"
sleep 3
for p in 8022 8081 7681; do
  echo -n "stop $p "
  (echo > /dev/tcp/127.0.0.1/$p) >/dev/null 2>&1 && echo open || echo closed
done
ps -ef 2>/dev/null | grep -E 'itab-watchdog|ttyd|http.server|sshd' | grep -v grep || echo 'no server processes'
echo '--- start ---'
"$HOME/start-itab.sh"
sleep 4
for p in 8022 8081 7681; do
  echo -n "start $p "
  (echo > /dev/tcp/127.0.0.1/$p) >/dev/null 2>&1 && echo open || echo closed
done
ps -ef 2>/dev/null | grep -E 'itab-watchdog|ttyd|http.server|sshd' | grep -v grep || echo 'no server processes'
