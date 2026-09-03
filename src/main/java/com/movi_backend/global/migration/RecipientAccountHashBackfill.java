package com.movi_backend.global.migration;

import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code transfer_recipients.account_num_hash} 일회성 백필.
 *
 * <p><b>이 일을 SQL 로 할 수 없어서 애플리케이션에 둔다.</b> {@code account_num} 은 무작위 IV 로
 * 암호화(AES/GCM)돼 있어 복호화에 애플리케이션 키가 필요하고, 해시는 또 다른 키로 만드는
 * HMAC-SHA256 이라 MySQL 에 대응하는 함수가 없다. {@code DemoDataSeeder} 가 시연 데이터를
 * SQL 이 아니라 코드로 만드는 것과 같은 이유다.
 *
 * <p>적용 순서와 배경은
 * {@code docs/migrations/20260903_add_recipient_account_num_hash.sql} 에 있다.
 * <b>모든 환경의 백필이 끝나면 이 클래스와 {@code findAllByAccountNumHashIsNull},
 * {@code TransferRecipient#backfillAccountNumHash} 를 함께 지운다.</b>
 *
 * <p>중복은 <b>찾아서 알리기만 하고 지우지 않는다.</b> 사용자가 직접 만든 데이터이고 이체가
 * 참조하고 있을 수 있어, 무엇을 남길지는 사람이 정해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "movi.migration.recipient-account-hash",
        name = "enabled",
        havingValue = "true"
)
public class RecipientAccountHashBackfill implements ApplicationRunner {

    private final TransferRecipientRepository transferRecipientRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;
    private final TransactionTemplate transactionTemplate;

    /**
     * <b>백필 실패가 서비스를 내리지 않는다.</b> {@link ApplicationRunner} 에서 예외를 그대로
     * 올리면 Spring Boot 가 컨텍스트를 닫고 프로세스를 종료한다. 컨테이너 재시작 정책과 맞물리면
     * 무한 재시작이 된다({@code DemoDataSeeder} 가 실제로 겪은 문제다).
     *
     * <p>해시가 비어 있는 것은 다음 기동에서 다시 채우면 되는 불편이고, 서비스가 뜨지 않는 것은
     * 장애다. 둘을 맞바꾸지 않는다.
     */
    @Override
    public void run(final ApplicationArguments args) {
        try {
            transactionTemplate.executeWithoutResult(status -> backfill());
        } catch (final RuntimeException exception) {
            log.warn("[MIGRATION] 수취인 계좌 해시를 채우지 못했습니다. 서비스는 그대로 기동합니다. 원인={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void backfill() {
        final List<TransferRecipient> targets =
                transferRecipientRepository.findAllByAccountNumHashIsNull();
        if (targets.isEmpty()) {
            log.info("[MIGRATION] 채울 수취인 계좌 해시가 없습니다. 이미 끝났거나 대상이 없습니다.");
            reportDuplicates();
            return;
        }

        int filled = 0;
        final List<Long> failed = new ArrayList<>();
        for (final TransferRecipient recipient : targets) {
            final String accountNumber = decryptOrNull(recipient.getAccountNum());
            if (accountNumber == null) {
                failed.add(recipient.getId());
                continue;
            }
            recipient.backfillAccountNumHash(sensitiveDataCrypto.hash(accountNumber));
            filled++;
        }

        log.info("[MIGRATION] 수취인 계좌 해시 {}건을 채웠습니다. (대상 {}건)", filled, targets.size());
        if (!failed.isEmpty()) {
            /*
             * 복호화에 실패한 행은 지금 키로는 읽을 수 없다는 뜻이다. 키를 바꾼 적이 있는지
             * 확인해야 한다. 이 행들이 남아 있으면 3단계 NOT NULL 이 실패한다.
             */
            log.warn("[MIGRATION] 계좌번호를 복호화하지 못한 수취인이 있습니다. recipient_id={}", failed);
        }
        reportDuplicates();
    }

    /**
     * 같은 사용자가 같은 계좌를 여러 이름으로 등록해 둔 행을 찾는다.
     *
     * <p>이 행들이 남아 있으면 마이그레이션 3단계의 {@code uk_recipient_user_account} 추가가
     * 실패한다. 지우는 것은 사람이 판단한다.
     */
    private void reportDuplicates() {
        final Map<String, List<Long>> byUserAndAccount = new HashMap<>();
        for (final TransferRecipient recipient : transferRecipientRepository.findAll()) {
            if (recipient.getAccountNumHash() == null) {
                continue;
            }
            final String key = recipient.getUser().getId() + ":" + recipient.getAccountNumHash();
            byUserAndAccount.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(recipient.getId());
        }

        final Set<Long> duplicated = new HashSet<>();
        for (final List<Long> recipientIds : byUserAndAccount.values()) {
            if (recipientIds.size() > 1) {
                duplicated.addAll(recipientIds);
            }
        }
        if (duplicated.isEmpty()) {
            log.info("[MIGRATION] 같은 계좌를 여러 이름으로 등록한 수취인은 없습니다. 3단계를 적용해도 됩니다.");
            return;
        }
        log.warn("[MIGRATION] 같은 계좌가 여러 이름으로 등록돼 있습니다. 정리하기 전에는 "
                        + "uk_recipient_user_account 추가가 실패합니다. recipient_id={}",
                duplicated);
    }

    /**
     * 복호화 실패를 예외로 올리지 않는다. 한 행 때문에 전체 백필이 멈추면 나머지 행도 계속
     * 비어 있게 된다. 읽지 못한 행은 따로 모아 알린다.
     */
    private String decryptOrNull(final String encrypted) {
        try {
            return sensitiveDataCrypto.decrypt(encrypted);
        } catch (final RuntimeException exception) {
            return null;
        }
    }
}
