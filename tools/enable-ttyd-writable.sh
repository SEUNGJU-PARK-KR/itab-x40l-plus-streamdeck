export HOME=/data/data/com.termux/files/home
sed -i 's/ttyd -i 127.0.0.1 -p 7681/ttyd -W -i 127.0.0.1 -p 7681/' "$HOME/start-itab.sh"
for p in $(pgrep -f 'ttyd.*7681' 2>/dev/null); do kill $p 2>/dev/null; done
"$HOME/start-itab.sh"
sleep 2
ps -ef 2>/dev/null | grep ttyd | grep -v grep
