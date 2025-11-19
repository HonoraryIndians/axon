# 예약 토큰 기반 결제 흐름

## 1. 목표

사용자의 실제 결제 시간을 고려하고, 대규모 트래픽에서도 안정적인 선착순 이벤트 시스템을 구축하기 위해 **'예약' 단계와 '결제 확정' 단계를 분리**한다. 이를 위해 '예약 토큰'을 사용하여 두 단계를 안전하게 연결한다.

## 2. 아키텍처 흐름

1.  **응모 요청 (to `EntryController`)**
    *   클라이언트가 `productId` 등과 함께 응모 요청을 보낸다.
    *   `EntryController`는 `core-service`에 문의하여 프론트가 보낸 정보(`productId` 등)가 유효한지 **교차 검증**하고, 신뢰할 수 있는 데이터(이하 '확정 데이터')를 확정한다.
    *   모든 자격 검증(빠른/무거운 검증)을 통과하면, Redis에 원자적으로 자리를 선점한다.

2.  **토큰 발급 및 '봉인'**
    *   `EntryController`는 위에서 확정된 모든 데이터(`userId`, `productId`, `timestamp` 등)를 `ReservationTokenPayload` 객체에 담는다.
    *   `ReservationTokenService`를 통해 임시 토큰(UUID)을 Key로, `ReservationTokenPayload`를 Value로 하여 Redis에 **'봉인'**한다. (TTL 설정)
    *   `EntryController`는 오직 '열쇠' 역할만 하는 **임시 토큰**만 클라이언트에게 응답한다.

3.  **결제 진행 (at Client)**
    *   클라이언트는 토큰을 받아 결제 페이지로 이동하거나 결제 모듈을 띄운다.
    *   사용자는 제한 시간 내에 외부 결제 시스템(현재는 Mock)을 통해 결제를 진행한다.

4.  **결제 확정 요청 (to `PaymentController`)**
    *   결제가 완료되면, 클라이언트는 받아뒀던 **임시 토큰**을 가지고 `PaymentController`에 결제 확정 요청을 보낸다.

5.  **인증, '개봉', 최종 처리**
    *   `PaymentController`는 요청을 보낸 사용자가 **로그인한 사용자인지 인증**한다.
    *   Redis에서 토큰으로 `ReservationTokenPayload`를 조회하여 **'개봉'**한다.
    *   **토큰의 주인(`payload.userId`)**과 **현재 로그인한 사용자(`currentUserId`)**가 일치하는지 검증하여 토큰 탈취 공격을 방어한다.
    *   검증 통과 시, '개봉'된 `ReservationTokenPayload` 안의 **신뢰할 수 있는 데이터**만을 사용하여 Kafka 메시지를 발행한다.
    *   사용한 임시 토큰은 Redis에서 삭제한다.

## 3. 주요 컴포넌트 및 책임

*   **`EntryController` (검증 및 봉인자):**
    *   최초 요청의 유효성을 교차 검증하고, 신뢰할 수 있는 데이터를 확정한다.
    *   확정된 데이터를 `ReservationTokenPayload`에 담아 Redis에 '봉인'하는 역할을 총괄한다.

*   **`ReservationTokenPayload` (보안 데이터 보관함):**
    *   `EntryController`가 검증하고 확정한 모든 정보(`userId`, `productId`, `timestamp` 등)를 담는 데이터 객체. Redis에 저장되어 `PaymentController`까지 안전하게 전달된다.

*   **`ReservationTokenService` (토큰 관리자):**
    *   `ReservationTokenPayload`를 Redis에 저장하고, 조회하고, 삭제하는 등 토큰의 전체 생명주기를 관리한다.

*   **`Frontend (Client)` (단순 메신저):**
    *   `EntryController`로부터 '열쇠'(토큰)를 받아, 결제 확정 시 `PaymentController`에 전달하는 단순한 메신저 역할을 한다. 데이터 자체에는 관여하지 않는다.

*   **`PaymentController` (인증 및 사용자):**
    *   사용자 인증과 토큰 소유권 검증을 수행한다.
    *   Redis에서 '봉인된' 데이터를 '개봉'하여, 그 안의 신뢰할 수 있는 데이터만을 최종적으로 사용(Kafka 발행 등)한다.

## 4. 보안 고려사항

*   **데이터 위변조 방지:** `productId`와 같은 중요한 상태 값은 클라이언트를 경유하지 않고, `EntryController`가 확정한 값을 Redis를 통해 `PaymentController`로 직접 전달하여 위변조를 원천 차단한다.
*   **토큰 탈취 방지:** `PaymentController`에서 토큰에 저장된 `userId`와 현재 요청을 보낸 사용자의 `userId`를 비교함으로써, 다른 사람이 탈취한 토큰을 사용할 수 없도록 막는다.
