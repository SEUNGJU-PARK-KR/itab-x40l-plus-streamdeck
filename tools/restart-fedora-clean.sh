export HOME=/data/data/com.termux/files/home
for p in $(pgrep -f 'http.server 8081' 2>/dev/null); do echo killing-http-$p; kill $p 2>/dev/null; done
for p in $(pgrep -f 'proot-distro login' 2>/dev/null); do echo killing-wrapper-$p; kill $p 2>/dev/null; done
for p in $(pgrep proot 2>/dev/null); do echo killing-proot-$p; kill $p 2>/dev/null; done
sleep 2
for p in $(pgrep -f 'http.server 8081' 2>/dev/null); do kill -9 $p 2>/dev/null; done
for p in $(pgrep -f 'proot-distro login' 2>/dev/null); do kill -9 $p 2>/dev/null; done
for p in $(pgrep proot 2>/dev/null); do kill -9 $p 2>/dev/null; done
echo '--- before start ---'
ps -ef 2>/dev/null | grep -E 'proot|http.server' | grep -v grep
"$HOME/start-itab.sh"
sleep 5
echo '--- after start ---'
ps -ef 2>/dev/null | grep -E 'proot|http.server|sshd' | grep -v grep
echo '--- proot ps ---'
proot-distro ps 2>&1
echo '--- log ---'
tail -20 "$HOME/alpine-server.log" 2>&1
