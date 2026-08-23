package com.movi_backend.domain.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountAliasChangeRequest(
        @NotBlank
        @Size(max = 50)
        String alias
) {
    public AccountAliasChangeRequest {
        if (alias != null) {
            alias = alias.strip();
        }
    }
}
