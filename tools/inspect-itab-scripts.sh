export HOME=/data/data/com.termux/files/home
echo '[start]'
cat "$HOME/start-itab.sh" 2>&1
echo '[boot]'
cat "$HOME/.termux/boot/start-itab.sh" 2>&1
echo '[ports]'
for p in 8022 8081 7681; do
  echo -n "$p "
  (echo > /dev/tcp/127.0.0.1/$p) >/dev/null 2>&1 && echo open || echo closed
done
