package com.movi_backend.domain.notification.infrastructure.solapi.dto;

public record SolapiMessage(String to, String from, String text) {

    public static SolapiMessage of(final String to, final String from, final String text) {
        return new SolapiMessage(to, from, text);
    }
}
