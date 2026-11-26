# Disk Cleanup Log

측정 시각: 2026-11-26 · 기기: ITAB_X40L_Plus (Android 11, 24GB f2fs userdata)

## 결과

```text
/data 전체:   24.18 GiB
사용:        ~1.71 GiB  (8%)
여유:        ~22.27 GiB
```

이전 상태(약 3.28 GiB used) 대비 **약 1.5GB 이상 회수**.

## 제거/정리 내역

| 항목 | 회수량 | 방법 |
|---|---|---|
| Termux clang/llvm 체인 | ~511 MB | `pkg remove clang llvm` + autoremove (python-pip 권장 의존성일 뿐 필수 아님) |
| Payhere 주문 앱 | ~320 MB | `pm uninstall` (POS/주문 앱, 미사용) |
| Google 앱 데이터/캐시 | ~135 MB | `pm clear` 대상: gms, inputmethod.latin, googlequicksearchbox, tts, chromium.browser, photos, wellbeing, nbu.files, calendar, configupdater |
| 재생성 가능 캐시 | 수백 MB | Termux/pkg 캐시, `pm trim-caches` |
| Fedora rootfs → Alpine | net ~250–300 MB | proot-distro alpine 설치 후 fedora 제거. Fedora 365MB → Alpine ~63MB |

## 유지한 것 (제외)

- `com.android.webview` — WebView는 대시보드/웹뷰 표시에 필요
- `permissioncontroller`, `packageinstaller` — 시스템 핵심
- `networkstack`, `gsf` — 네트워크/Google 서비스 프레임워크
- Termux 본체 + proot-distro + openssh + ttyd + iproute2 + curl + python

## 파티션 구조 참고

```text
/ (system)        1.0G   RO  ← dm-verity, 변경 불가
/system_ext       327M   RO
/vendor           554M   RO
/product          1.1G   RO
APEX loop 이미지   ~293M  RO
/cache             90M   (거의 비어있음)
/data             24G    RW f2fs  ← 실제 사용자 영역
```

OS 파티션들은 읽기 전용 + dm-verity라 일반 방법으로 줄일 수 없습니다. 실질적으로 정리 가능한 건 `/data`뿐이며, 현재 8% 사용률입니다.

## OS 최소화 조사 결론

- SoC: Unisoc SC9863A (`sp9863a`/`s9863a3h10`), Android 11, 커널 4.14.193
- postmarketOS 네이티브 포팅: 터치(HX83102)/디스플레이(WCN Wi-Fi)/GPU(PowerVR GE8322)/오디오 드라이버 부재로 비현실적
- 대안: **Droidian (Debian + libhybris)** — 동일 SoC 기기(Teclast P20HD)에서 포팅 진행 중인 선례 존재
- 당장의 최적 경로: 현행 Android + Termux/Alpine + 디블로트
