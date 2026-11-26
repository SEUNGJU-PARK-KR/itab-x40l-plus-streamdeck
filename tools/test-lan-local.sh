export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
echo '--- curl wifi ip dashboard ---'
curl -sS --max-time 4 http://172.30.6.238:8080/health 2>&1
echo '--- curl wifi ip linux http ---'
curl -sS --max-time 4 http://172.30.6.238:8081/ 2>&1 | head -2
echo '--- listening ---'
netstat -ltn 2>/dev/null | grep -E ':(8080|8081|8022)'
