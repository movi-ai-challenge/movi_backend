package com.movi_backend.domain.auth.application.event;

/**
 * 인증에 성공한 기기를 신뢰 기기로 올려 달라는 요청.
 *
 * <p>기기 등록을 인증 트랜잭션 안에서 바로 부르지 않고 이 이벤트로 미루는 이유가 있다.
 * {@code devices}는 {@code users}를 FK로 참조한다. 회원가입은 {@code users} 행을 방금
 * 넣었고 PIN 등록은 방금 고쳤는데, 그 행은 아직 커밋되지 않아 배타 잠금이 걸려 있다.
 * 기기 등록은 실패가 로그인을 무너뜨리면 안 되므로 별도 트랜잭션에서 도는데, <b>별도
 * 트랜잭션이라 같은 행의 잠금을 기다린다.</b> 자기 자신과 교착에 빠져 50초 뒤
 * {@code Lock wait timeout}으로 터진다.
 *
 * <p>커밋이 끝난 뒤에 처리하면 잠금이 이미 풀려 있어 이 문제가 생기지 않는다.
 */
public record TrustedDeviceRegistrationRequested(
        Long userId,
        String deviceUuid,
        String deviceModel,
        String osVersion
) {
}
