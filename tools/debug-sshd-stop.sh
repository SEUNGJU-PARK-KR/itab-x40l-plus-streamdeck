echo before
pgrep sshd 2>&1
ps -ef 2>/dev/null | grep sshd | grep -v grep
for p in $(pgrep sshd 2>/dev/null); do echo killing-$p; kill $p 2>&1; done
sleep 1
echo after-term
pgrep sshd 2>&1
ps -ef 2>/dev/null | grep sshd | grep -v grep
for p in $(pgrep sshd 2>/dev/null); do echo killing9-$p; kill -9 $p 2>&1; done
sleep 1
echo after-kill9
pgrep sshd 2>&1
