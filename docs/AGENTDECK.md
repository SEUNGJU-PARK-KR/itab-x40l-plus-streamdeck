# AgentDeck Connection

태블릿 스트림덱(`DeckActivity`)은 AgentDeck daemon의 WebSocket 프로토콜 클라이언트입니다.

## 1. PC에서 daemon 실행

AgentDeck은 짝수 Node 버전(22/24/26)이 필요합니다. 시스템 Node가 맞지 않으면 portable Node를 사용하세요.

```powershell
# 예: portable Node 24 + npm prefix 설치
npm install --prefix D:\project\itab-tools\agentdeck-global @agentdeck/bridge

# daemon 시작 (port 9120)
node D:\project\itab-tools\agentdeck-global\node_modules\@agentdeck\bridge\dist\cli.js daemon start

# 상태 / 페어링 코드
node ...\cli.js daemon status
node ...\cli.js pair
```

## 2. 연결 방법

### USB reverse (개발/테스트용)

```powershell
adb reverse tcp:9120 tcp:9120
```

태블릿 스트림덱 `설정`에서 bridge 주소를 `127.0.0.1:9120`으로 설정합니다.

주의: USB를 뽑거나 재부팅하면 `adb reverse`를 다시 실행해야 합니다.

### 같은 LAN (실사용)

태블릿과 PC가 같은 네트워크에 있으면 스트림덱 `설정`에서 `<PC-IP>:9120`을 입력합니다.

인증이 필요하면 PC에서 `pair` 명령으로 6자리 코드를 발급하고 태블릿에 입력하면 `POST /pair`로 토큰을 받습니다.

자동 검색은 daemon의 UDP beacon(9121)을 듣습니다. 브로드캐스트가 도달하는 같은 서브넷에서만 동작하며, 서브넷이 다르면 수동 입력이 필요합니다.

## 3. 프로토콜 메모

구현 참고: `deck-launcher/app/src/main/java/com/itab/deck/agentdeck/`

- 연결: `ws://<host>:9120` (+ `?token=`)
- 등록: `client_register`
- 세션 목록: `sessions_list` → 타일 그리드
- 세션 제어: `focus_session`, `select_option`, `permission_decision`, `interrupt`, `escape`
- 사용량: `query_usage` → `usage_update`
- 권한 요청: `requestId`가 있으면 `permission_decision allow|deny`
- 옵션 프롬프트: 실제 `options` 배열만 버튼으로 표시 (임의 생성 금지)
- observed + `liveAnswerable=false` 세션은 읽기 전용으로 표시

## 4. 원본 참고

원본 AgentDeck 프로젝트의 Android 프로토콜 구현을 참고했습니다.

- `Protocol.kt`, `BridgeConnection.kt` — 메시지 형식/재연결
- `session-slot-manager.ts` — 세션 버튼/그리드 동작
- `manifest.json` (Stream Deck plugin) — 액션 정의
- `design/brand/*.svg` — 에이전트 브랜드 아이콘 (lobe-icons, MIT)
