export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin
export LD_LIBRARY_PATH=$PREFIX/lib
echo "PREFIX=$PREFIX"
echo "proot=$(command -v proot-distro)"
echo "--- proot-distro head ---"
sed -n '1,90p' "$(command -v proot-distro)" 2>/dev/null
echo "--- alpine dirs ---"
find "$PREFIX" "$HOME" -maxdepth 5 -type d -iname '*alpine*' 2>/dev/null
echo "--- proot distro related dirs ---"
find "$PREFIX" -maxdepth 4 -type d -iname '*distro*' 2>/dev/null
