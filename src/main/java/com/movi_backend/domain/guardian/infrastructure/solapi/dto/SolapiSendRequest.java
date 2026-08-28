package com.movi_backend.domain.guardian.infrastructure.solapi.dto;

/** {@code POST /messages/v4/send} 단건 발송 요청 본문. */
public record SolapiSendRequest(SolapiMessage message) {

    public static SolapiSendRequest of(final SolapiMessage message) {
        return new SolapiSendRequest(message);
    }
}
