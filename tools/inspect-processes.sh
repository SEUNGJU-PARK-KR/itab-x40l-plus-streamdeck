ps -ef 2>/dev/null | grep -E 'proot|python|sshd' | grep -v grep
echo ---
command -v pkill
command -v pgrep
echo ---
pgrep -af proot 2>&1
pgrep -af python 2>&1
