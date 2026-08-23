package com.movi_backend.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserInfo(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {

    public String providerUserId() {
        if (id == null) {
            return null;
        }
        return String.valueOf(id);
    }

    public String nickname() {
        if (kakaoAccount == null || kakaoAccount.profile() == null) {
            return null;
        }
        return kakaoAccount.profile().nickname();
    }

    public String phoneNumber() {
        if (kakaoAccount == null) {
            return null;
        }
        return kakaoAccount.phoneNumber();
    }

    public record KakaoAccount(
            @JsonProperty("phone_number") String phoneNumber,
            Profile profile
    ) {
    }

    public record Profile(
            String nickname
    ) {
    }
}
