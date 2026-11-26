kill 7351 8402 8494 2>/dev/null
sleep 1
kill -9 7351 8402 8494 2>/dev/null
export HOME=/data/data/com.termux/files/home
"$HOME/start-itab.sh"
sleep 3
echo '--- after clean start ---'
ps -ef 2>/dev/null | grep -E 'proot|python|sshd' | grep -v grep
