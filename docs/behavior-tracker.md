# Behavior Tracker JS Snippet

Axon 웹 페이지에서 사용자 행동(페이지 뷰, 버튼 클릭 등)을 감지하고 `entry-service` → Kafka 로 전송하기 위한 경량 스니펫입니다.  
`docs/snippets/behavior-tracker.js`를 정적 자산으로 배포한 뒤, 모든 페이지(또는 필요한 화면)에서 초기화 코드를 실행하면 됩니다.

## 1. 설치 & 초기화

```html
<script src="https://static.axon.dev/behavior-tracker.js"></script>
<script>
  window.AxonBehaviorTracker.init({
    apiBaseUrl: 'https://entry.dev.axon.com',
    tokenProvider: () => localStorage.getItem('access_token'),
    userIdProvider: () => window.__AXON_USER_ID__,
    sessionIdProvider: () => window.__AXON_SESSION_ID__,
    debug: false,                // true 로 두면 콘솔에 상세 로그가 출력됩니다.
    withCredentials: true,       // SameSite=Lax 쿠키를 사용하는 경우 true 유지
    autoRefreshMs: 5 * 60 * 1000 // 활성 이벤트 목록을 5분마다 갱신
  });
</script>
```

> `behavior-tracker.js`를 CDN, 정적 호스팅(S3, Nginx) 등에 업로드한 뒤 `<script src>` 경로에 맞게 반영하세요.

## 2. 동작 개요

1. 스니펫이 초기화되면 `GET /api/v1/events/active`를 호출하여 활성 이벤트 정의 목록을 다운로드합니다.
2. 이벤트의 `triggerType`에 따라 필요한 핸들러를 자동으로 등록합니다.
   - `PAGE_VIEW` : History API(`pushState`, `replaceState`, `popstate`)와 `load` 이벤트를 감지해 URL 패턴 매칭
   - `CLICK` : document-level delegation으로 selector(`triggerPayload.selector`)를 감지
3. 조건이 충족되면 `POST /api/v1/behavior/events`에 아래 DTO를 전송합니다.
   ```json
   {
     "eventId": 123,
     "eventName": "상품 상세 페이지 뷰",
     "triggerType": "PAGE_VIEW",
     "occurredAt": "2025-03-05T12:34:56.789Z",
     "pageUrl": "https://shop.axon.com/products/1",
     "referrer": "https://shop.axon.com/",
     "userId": 42,
     "sessionId": "session-uuid",
     "properties": {
       "selector": "#buy-button",
       "elementText": "구매하기"
     }
   }
   ```
4. 엔트리 서비스는 DTO를 Kafka `axon.event.raw` 토픽으로 퍼블리시하고, 이후 Fluentd 등으로 전환 가능하게 퍼블리셔가 추상화돼 있습니다.

## 3. 설정 옵션

| 옵션 | 설명 | 기본값 |
| --- | --- | --- |
| `apiBaseUrl` | 엔트리 서비스 베이스 URL. 상대경로만 쓸 경우 빈 문자열 | `''` |
| `eventsEndpoint` | 활성 이벤트 정의 API 경로 | `/api/v1/events/active` |
| `collectEndpoint` | 행동 로그 수집 API 경로 | `/api/v1/behavior/events` |
| `tokenProvider` | Authorization 토큰을 반환하는 함수/Promise | `null` |
| `userIdProvider` | 사용자 ID 반환 함수/Promise | `null` |
| `sessionIdProvider` | 세션 ID 반환 함수/Promise | `null` |
| `withCredentials` | `fetch` 호출 시 `credentials: 'include'` 여부 | `true` |
| `autoRefreshMs` | 이벤트 정의 재조회 주기(ms). `0`이면 비활성화 | `5분` |
| `cooldownMs` | 동일 이벤트 중복 전송 최소 간격(ms) | `1500` |
| `debug` | 콘솔 디버그 로그 출력 여부 | `false` |

## 4. 트리거 페이로드 예시

| TriggerType | 키 | 설명 |
| --- | --- | --- |
| `PAGE_VIEW` | `urlPattern` | `*` 와일드카드가 허용된 경로 패턴 (예: `/products/*`) |
| `CLICK` | `selector` | CSS Selector (예: `#buy-button`, `.cta button`) |

> 해당 키 이름은 백엔드 이벤트 정의 시 `triggerPayload` JSON에 그대로 저장되어야 합니다.

## 5. 통합 체크리스트

1. `entry-service` CORS 설정에 스니펫을 삽입할 도메인이 포함돼 있는지 확인합니다.
2. 토큰/JWT를 사용하는 경우 `tokenProvider`에서 적절히 반환되는지 확인합니다.
3. 로컬/스테이징에서 `axon.event.raw` 토픽을 소비하며 실제 페이로드가 기대한 형태인지 검증합니다.
4. Fluentd 전환 시에는 `BehaviorEventPublisher` 구현체만 교체하면 되도록 구조가 이미 준비돼 있습니다.

## 6. 추가 아이디어

- `autoRefreshMs` 대신 `AxonBehaviorTracker.refreshEvents()`를 수동으로 호출해 이벤트 정의 변경을 즉시 반영할 수 있습니다.
- `CLICK` 외에도 스크롤, 폼 제출 등 다른 트리거 타입을 추가할 때는 스니펫의 `TriggerType` 상수와 핸들러를 확장하면 됩니다.
- 전송 실패 시 로컬 큐에 저장했다가 재전송하는 기능이 필요하면 `sendEvent` 메서드를 확장하세요.
