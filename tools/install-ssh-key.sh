mkdir -p /data/data/com.termux/files/home/.ssh
printf '%s\n' 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAID53ultjKJlcR+XZIZt6Oz45vmXOQfVgGckzc01WpyoL itab-tablet-server' >> /data/data/com.termux/files/home/.ssh/authorized_keys
chmod 700 /data/data/com.termux/files/home/.ssh
chmod 600 /data/data/com.termux/files/home/.ssh/authorized_keys
echo key-installed
