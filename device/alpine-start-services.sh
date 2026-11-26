#!/bin/bash
mkdir -p /srv/www /var/log
if ! pgrep -f 'http.server 8081' >/dev/null 2>&1; then
  nohup python3 -m http.server 8081 --directory /srv/www >/var/log/itab-http.log 2>&1 &
fi
echo "ITAB Alpine services started: $(date)" >> /var/log/itab-services.log
tail -f /dev/null
