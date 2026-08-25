package com.movi_backend.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 정의.
 *
 * <p>{@code message}는 개발자·로그·화면용, {@code voiceMessage}는 TTS로 읽히는 사용자 안내 문구다.
 * 이 서비스의 에러는 화면에 표시되는 데서 끝나지 않고 음성으로 전달되므로 두 문구를 분리한다.
 *
 * <p>추가·수정 시 docs/error-codes.md를 함께 갱신한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_4010", "인증이 필요합니다.",
            "로그인이 필요해요."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_4011", "유효하지 않은 액세스 토큰입니다.",
            "다시 로그인해 주세요."),
    EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_4012", "액세스 토큰이 만료되었습니다.",
            "로그인 시간이 지났어요. 다시 로그인해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_4013", "리프레시 토큰이 유효하지 않습니다.",
            "다시 로그인해 주세요."),
    INVALID_OAUTH_STATE(HttpStatus.UNAUTHORIZED, "AUTH_4014", "로그인 요청 상태가 유효하지 않습니다.",
            "로그인을 처음부터 다시 시도해 주세요."),
    PIN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_4020", "비밀번호가 일치하지 않습니다.",
            "비밀번호가 맞지 않아요. 다시 입력해 주세요."),
    PIN_LOCKED(HttpStatus.FORBIDDEN, "AUTH_4021", "비밀번호 입력 제한 횟수를 초과했습니다.",
            "비밀번호를 여러 번 잘못 입력하셨어요. 잠시 후 다시 시도해 주세요."),
    PIN_NOT_REGISTERED(HttpStatus.BAD_REQUEST, "AUTH_4022", "등록된 비밀번호가 없습니다.",
            "비밀번호를 먼저 등록해 주세요."),
    PIN_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_4090", "비밀번호가 이미 등록되어 있습니다.",
            "비밀번호가 이미 등록되어 있어요."),
    BIOMETRIC_NOT_ENABLED(HttpStatus.BAD_REQUEST, "AUTH_4023", "생체인증이 설정되어 있지 않습니다.",
            "지문이나 얼굴 인식이 설정되어 있지 않아요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_4030", "접근 권한이 없습니다.",
            "이 기능을 사용할 수 없어요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_4040", "회원을 찾을 수 없습니다.",
            "회원 정보를 찾을 수 없어요."),

    // 카카오 로그인
    KAKAO_TOKEN_IS_BLANK(HttpStatus.BAD_REQUEST, "KAKAO_4000", "카카오 토큰이 비어 있습니다.",
            "로그인에 실패했어요. 다시 시도해 주세요."),
    KAKAO_AUTHORIZATION_FAILED(HttpStatus.BAD_REQUEST, "KAKAO_4001", "카카오 인가 처리에 실패했습니다.",
            "카카오 로그인을 처음부터 다시 시도해 주세요."),
    KAKAO_REQUIRED_INFO_MISSING(HttpStatus.BAD_REQUEST, "KAKAO_4002", "카카오 필수 회원 정보가 없습니다.",
            "전화번호 제공에 동의한 뒤 다시 로그인해 주세요."),
    KAKAO_COMMUNICATION_ERROR(HttpStatus.BAD_GATEWAY, "KAKAO_5000", "카카오 통신에 실패하였습니다.",
            "카카오 로그인이 지금 안 돼요. 잠시 후 다시 시도해 주세요."),

    // 계좌
    ACCOUNT_ALREADY_REGISTERED(HttpStatus.BAD_REQUEST, "ACCOUNT_4001", "이미 등록된 계좌입니다.",
            "이미 등록된 계좌예요."),
    ACCOUNT_INACTIVE(HttpStatus.BAD_REQUEST, "ACCOUNT_4002", "사용할 수 없는 계좌입니다.",
            "사용할 수 없는 계좌예요. 다른 계좌를 선택해 주세요."),
    ACCOUNT_ALIAS_DUPLICATED(HttpStatus.BAD_REQUEST, "ACCOUNT_4003", "이미 사용 중인 계좌 별칭입니다.",
            "같은 이름의 계좌가 이미 있어요. 다른 이름을 말씀해 주세요."),
    PRIMARY_ACCOUNT_NOT_SET(HttpStatus.BAD_REQUEST, "ACCOUNT_4004", "기본 계좌가 설정되어 있지 않습니다.",
            "주로 쓰실 계좌를 먼저 정해 주세요."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOUNT_4040", "계좌를 찾을 수 없습니다.",
            "말씀하신 계좌를 찾을 수 없어요."),

    // 오픈뱅킹 연동
    INVALID_FINTECH_USE_NUM(HttpStatus.BAD_REQUEST, "OPENBANK_4001", "유효하지 않은 핀테크이용번호입니다.",
            "계좌 정보에 문제가 있어요. 계좌를 다시 연결해 주세요."),
    INVALID_OPENBANKING_STATE(HttpStatus.BAD_REQUEST, "OPENBANK_4002", "유효하지 않은 계좌 연결 요청입니다.",
            "계좌 연결에 실패했어요. 처음부터 다시 시도해 주세요."),
    CONNECTION_EXPIRED(HttpStatus.UNAUTHORIZED, "OPENBANK_4010", "오픈뱅킹 연결이 만료되었습니다.",
            "은행 연결이 끊어졌어요. 다시 연결해 주세요."),
    CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "OPENBANK_4040", "오픈뱅킹 연결 정보를 찾을 수 없습니다.",
            "은행 계좌가 연결되어 있지 않아요."),
    OPENBANK_COMMUNICATION_ERROR(HttpStatus.BAD_GATEWAY, "OPENBANK_5000", "오픈뱅킹 통신에 실패하였습니다.",
            "은행과 연결이 잠시 안 돼요. 조금 뒤에 다시 시도해 주세요."),
    BALANCE_INQUIRY_FAILED(HttpStatus.BAD_GATEWAY, "OPENBANK_5001", "잔액 조회에 실패했습니다.",
            "잔액을 확인하지 못했어요. 다시 말씀해 주세요."),
    TRANSFER_EXECUTION_FAILED(HttpStatus.BAD_GATEWAY, "OPENBANK_5002", "이체 실행에 실패했습니다.",
            "송금하지 못했어요. 돈은 빠져나가지 않았어요."),

    // 이체
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "TRANSFER_4001", "잔액이 부족합니다.",
            "잔액이 부족해요. 다른 금액을 말씀해 주세요."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "TRANSFER_4002", "유효하지 않은 이체 금액입니다.",
            "금액을 다시 말씀해 주세요."),
    AMOUNT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "TRANSFER_4003", "1회 이체 한도를 초과했습니다.",
            "한 번에 보낼 수 있는 금액을 넘었어요."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "TRANSFER_4004", "1일 이체 한도를 초과했습니다.",
            "오늘 보낼 수 있는 금액을 모두 쓰셨어요."),
    SELF_TRANSFER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "TRANSFER_4005", "본인 계좌로는 이체할 수 없습니다.",
            "같은 계좌로는 보낼 수 없어요."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "TRANSFER_4006", "처리할 수 없는 이체 상태입니다.",
            "이미 처리된 송금이에요."),
    TRANSFER_BLOCKED(HttpStatus.FORBIDDEN, "TRANSFER_4031", "위험 거래로 차단된 이체입니다.",
            "안전을 위해 이번 송금을 멈췄어요. 보호자에게 알려 드렸어요."),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSFER_4040", "이체 내역을 찾을 수 없습니다.",
            "송금 내역을 찾을 수 없어요."),
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSFER_4041", "등록된 수취인을 찾을 수 없습니다.",
            "그런 이름으로 저장된 분이 없어요. 다시 말씀해 주세요."),
    DUPLICATE_TRANSFER(HttpStatus.CONFLICT, "TRANSFER_4090", "이미 처리 중인 이체 요청입니다.",
            "방금 같은 송금을 요청하셨어요. 잠시만 기다려 주세요."),

    // 음성 인식·재질문
    AMOUNT_MISSING(HttpStatus.BAD_REQUEST, "VOICE_4001", "이체 금액이 누락되었습니다.",
            "얼마를 보내시겠어요?"),
    RECIPIENT_MISSING(HttpStatus.BAD_REQUEST, "VOICE_4002", "수취인이 누락되었습니다.",
            "누구에게 보내시겠어요?"),
    INTENT_UNKNOWN(HttpStatus.BAD_REQUEST, "VOICE_4003", "명령 의도를 파악하지 못했습니다.",
            "무엇을 도와드릴까요? 잔액 조회나 송금이라고 말씀해 주세요."),
    LOW_CONFIDENCE(HttpStatus.BAD_REQUEST, "VOICE_4004", "음성 인식 신뢰도가 낮습니다.",
            "잘 못 들었어요. 다시 한번 말씀해 주세요."),
    SLOT_EXPIRED(HttpStatus.BAD_REQUEST, "VOICE_4005", "대화 세션이 만료되었습니다.",
            "시간이 좀 지났어요. 처음부터 다시 말씀해 주세요."),
    RETRY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "VOICE_4006", "음성 인식 재시도 횟수를 초과했습니다.",
            "음성 인식이 잘 안 되네요. 잠시 후 다시 시도해 주세요."),
    INVALID_SESSION_STATE(HttpStatus.BAD_REQUEST, "VOICE_4007", "처리할 수 없는 음성 세션 상태입니다.",
            "지금은 처리할 수 없어요. 처음부터 다시 말씀해 주세요."),
    AUDIO_DURATION_INVALID(HttpStatus.BAD_REQUEST, "VOICE_4008", "음성 파일 재생 시간을 확인할 수 없습니다.",
            "음성 파일을 확인하지 못했어요. 다시 녹음해 주세요."),
    AUDIO_DURATION_EXCEEDED(HttpStatus.BAD_REQUEST, "VOICE_4009", "음성 파일은 15초 이하여야 합니다.",
            "음성은 15초 안으로 말씀해 주세요."),
    HISTORY_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "VOICE_4010", "조회할 수 없는 기간입니다.",
            "언제부터 언제까지 찾아 드릴지 다시 말씀해 주세요."),
    VOICE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "VOICE_4040", "음성 세션을 찾을 수 없습니다.",
            "처음부터 다시 말씀해 주세요."),
    STT_FAILED(HttpStatus.BAD_GATEWAY, "VOICE_5000", "음성 인식에 실패했습니다.",
            "소리를 알아듣지 못했어요. 다시 말씀해 주세요."),
    TTS_FAILED(HttpStatus.BAD_GATEWAY, "VOICE_5001", "음성 합성에 실패했습니다.",
            "안내 음성을 재생하지 못했어요."),

    // 이상거래 탐지
    HIGH_RISK_BLOCKED(HttpStatus.FORBIDDEN, "FDS_4031", "고위험 거래로 차단되었습니다.",
            "안전을 위해 이번 송금을 멈췄어요. 보호자에게 알려 드렸어요."),
    ASSESSMENT_FAILED(HttpStatus.BAD_GATEWAY, "FDS_5000", "위험도 평가에 실패했습니다.",
            "안전 확인을 하지 못해 송금을 진행하지 않았어요. 잠시 후 다시 시도해 주세요."),
    ASSESSMENT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "FDS_5001", "위험도 평가 응답이 지연되었습니다.",
            "안전 확인이 늦어지고 있어요. 잠시 후 다시 시도해 주세요."),

    // 보호자
    ALREADY_LINKED(HttpStatus.BAD_REQUEST, "GUARDIAN_4001", "이미 연결된 보호자입니다.",
            "이미 연결된 분이에요."),
    INVITE_EXPIRED(HttpStatus.BAD_REQUEST, "GUARDIAN_4002", "초대 링크가 만료되었습니다.",
            "초대가 만료됐어요. 다시 요청해 주세요."),
    INVALID_INVITE_TOKEN(HttpStatus.BAD_REQUEST, "GUARDIAN_4003", "유효하지 않은 초대 링크입니다.",
            "초대 정보가 올바르지 않아요."),
    SELF_LINK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "GUARDIAN_4004", "본인을 보호자로 등록할 수 없습니다.",
            "본인은 보호자로 등록할 수 없어요."),
    GUARDIAN_NO_PERMISSION(HttpStatus.FORBIDDEN, "GUARDIAN_4030", "보호자 권한이 없습니다.",
            "이 정보를 볼 권한이 없어요."),
    GUARDIAN_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "GUARDIAN_4040", "보호자 연결 정보를 찾을 수 없습니다.",
            "연결된 보호자가 없어요."),

    // 알림
    INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "NOTI_4001", "유효하지 않은 전화번호 형식입니다.",
            "전화번호가 올바르지 않아요. 다시 말씀해 주세요."),
    SMS_SEND_FAILED(HttpStatus.BAD_GATEWAY, "NOTI_5000", "SMS 전송에 실패했습니다.",
            "문자를 보내지 못했어요."),

    // 공통 요청 오류
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "REQ_4000", "잘못된 요청입니다.",
            "요청을 처리하지 못했어요. 다시 시도해 주세요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "REQ_4050", "지원하지 않는 HTTP 메서드입니다.",
            "요청을 처리하지 못했어요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "REQ_4150", "지원하지 않는 미디어 타입입니다.",
            "요청을 처리하지 못했어요."),

    // 서버 오류
    NOT_FOUND(HttpStatus.NOT_FOUND, "SRV_4040", "찾을 수 없습니다.",
            "찾을 수 없어요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SRV_5000", "서버 내부 오류가 발생했습니다.",
            "문제가 생겼어요. 잠시 후 다시 시도해 주세요."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SRV_5030", "현재 서비스를 사용할 수 없습니다.",
            "지금은 서비스를 이용할 수 없어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
    private final String voiceMessage;
}
