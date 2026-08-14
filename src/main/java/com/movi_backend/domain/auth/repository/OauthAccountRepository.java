package com.movi_backend.domain.auth.repository;

import com.movi_backend.domain.auth.entity.OauthAccount;
import com.movi_backend.domain.auth.type.OauthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, Long> {

    Optional<OauthAccount> findByProviderAndProviderUserId(
            OauthProvider provider,
            String providerUserId
    );
}
