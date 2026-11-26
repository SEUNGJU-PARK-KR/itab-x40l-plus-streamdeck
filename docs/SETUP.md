# Device Setup — Termux + Alpine Linux

ITAB X40L Plus에서 리눅스 서버(sshd / HTTP / ttyd / watchdog)를 구성하는 절차입니다.

## 1. Termux 설치

- F-Droid 또는 GitHub 릴리스의 Termux APK 설치 (Play Store 버전은 오래됨)
- 최초 실행 후:

```bash
pkg update -y
pkg install -y proot-distro openssh ttyd iproute2 curl python
```

## 2. Alpine 컨테이너 설치

```bash
proot-distro install alpine
proot-distro login alpine -- sh -c '/sbin/apk add --no-cache python3 bash'
```

Alpine 내부 서비스 파일 배치 (`device/alpine-start-services.sh` 참조):

```text
<proot>/root/start-services.sh      → 컨테이너 시작 시 실행
<proot>/srv/www/index.html          → :8081 웹 콘텐츠
```

컨테이너 루트 경로:

```text
/data/data/com.termux/files/usr/var/lib/proot-distro/containers/alpine/rootfs
```

## 3. 서비스 스크립트 배치

`device/` 폴더의 파일을 Termux home에 배치합니다.

```text
~/.termux/boot/start-itab.sh   ← Termux:Boot 자동 시작 래퍼
~/start-itab.sh                ← sshd + Alpine HTTP + ttyd + watchdog 시작
~/stop-itab.sh                 ← 전체 중지 (watchdog 포함)
~/itab-watchdog.sh             ← 20초 주기 자동 복구
```

스크립트는 **반드시 LF 줄바꿈**이어야 합니다. CRLF면 Android `sh`가 파싱에 실패합니다.

Windows에서 작성했다면 푸시 전에 변환:

```powershell
$t = [IO.File]::ReadAllText('script.sh') -replace "`r`n", "`n"
[IO.File]::WriteAllText('script.sh', $t)
adb push script.sh /data/local/tmp/
adb shell run-as com.termux cp /data/local/tmp/script.sh /data/data/com.termux/files/home/
adb shell run-as com.termux chmod +x /data/data/com.termux/files/home/script.sh
```

## 4. 시작/중지

```bash
~/start-itab.sh    # 시작
~/stop-itab.sh     # 중지
```

`start-itab.sh`는 `$HOME/.itab-server-enabled` 플래그를 만듭니다. 이 파일이 있는 동안만 watchdog이 서비스를 유지합니다.

## 5. 검증

```bash
# 포트 확인
netstat -ltn | grep -E ':(8022|8080|8081|7681)'

# Alpine 내부 확인
proot-distro login alpine -- /usr/bin/python3 --version
proot-distro login alpine -- /bin/busybox cat /etc/alpine-release
```

## 6. 부팅 자동 시작 (미검증)

`~/.termux/boot/start-itab.sh`가 Termux:Boot에 의해 실행됩니다. Termux:Boot 앱 설치 + Android 배터리 최적화 예외가 필요할 수 있습니다. 실제 재부팅 테스트는 아직 수행하지 않았습니다.

## 포트 요약

| 포트 | 바인딩 | 서비스 |
|---|---|---|
| 8022 | 0.0.0.0 | sshd (Termux) |
| 8080 | 0.0.0.0 | deck-launcher 대시보드 (앱 실행 중) |
| 8081 | 0.0.0.0 | Alpine python http.server |
| 7681 | 127.0.0.1 | ttyd 웹 터미널 (외부 차단, SSH 터널 권장) |
