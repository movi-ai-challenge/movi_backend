package com.movi_backend.global.seed;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.account.repository.OpenbankingConnectionRepository;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.domain.auth.entity.Device;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.entity.UserCredential;
import com.movi_backend.domain.auth.repository.DeviceRepository;
import com.movi_backend.domain.auth.repository.UserCredentialRepository;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.fds.entity.UserTransferProfile;
import com.movi_backend.domain.fds.repository.UserTransferProfileRepository;
import com.movi_backend.domain.guardian.entity.GuardianLink;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시연·E2E용 시드 데이터.
 *
 * <p><b>SQL 파일로는 만들 수 없다.</b> {@code users.phone}은 AES-GCM 암호문,
 * {@code phone_hash}는 HMAC, {@code pin_hash}는 BCrypt다. 전부 애플리케이션 키로 만들어지므로
 * 같은 컴포넌트를 거쳐 생성해야 로그인과 복호화가 맞물린다.
 *
 * <p><b>지우지 않는다.</b> 이미 있으면 건너뛰고 넘어간다. 재기동마다 데이터를 갈아엎으면
 * 시연 중 서버를 한 번 올렸다 내렸다는 이유로 방금 만든 이체 기록이 사라진다. 운영 DB를
 * 초기화할 수단을 코드에 두지도 않는다.
 *
 * <p>계좌의 핀테크이용번호는 {@code MockOpenBankingClient}가 아는 값을 쓴다. 모르는 번호면
 * 잔액이 고정 폴백값이 되고 이체는 {@code OPENBANK_4001}로 실패해 시연이 성립하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "movi.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    /** {@code MockOpenBankingClient}가 잔액 53만원으로 들고 있는 계좌 */
    private static final String PRIMARY_FINTECH_NUM = "199000000000000000000001";

    /** 같은 Mock의 120만원 계좌. 70만원 이상인 HIGH 시연은 이 계좌에서 나가야 한다 */
    private static final String SAVING_FINTECH_NUM = "199000000000000000000002";

    /** Mock이 모르는 번호. 두 번째 사용자는 이체 시연 대상이 아니라 소유권 거부 확인용이다 */
    private static final String OTHER_FINTECH_NUM = "199000000000000000000901";

    private static final String DEMO_PHONE = "01012345678";
    private static final String OTHER_PHONE = "01099998888";
    private static final String GUARDIAN_PHONE = "01099047809";

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final DeviceRepository deviceRepository;
    private final OpenbankingConnectionRepository connectionRepository;
    private final AccountRepository accountRepository;
    private final TransferRecipientRepository recipientRepository;
    private final UserTransferProfileRepository profileRepository;
    private final GuardianLinkRepository guardianLinkRepository;
    private final SensitiveDataCrypto sensitiveDataCrypto;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seedProperties;
    private final TransactionTemplate transactionTemplate;

    /**
     * <b>시드 실패가 서비스를 내리지 않는다.</b> 이 클래스는 {@link ApplicationRunner}라 예외를
     * 그대로 올리면 Spring Boot가 컨텍스트를 닫고 프로세스를 종료한다. 컨테이너 재시작 정책과
     * 맞물리면 무한 재시작이 된다. 실제로 운영에서 시드 번호가 이미 쓰이고 있다는 이유만으로
     * 서버가 971번 재기동한 적이 있다.
     *
     * <p>시연 데이터가 없는 것은 불편이고, 서비스가 뜨지 않는 것은 장애다. 둘을 맞바꾸지 않는다.
     */
    @Override
    public void run(final ApplicationArguments args) {
        try {
            transactionTemplate.executeWithoutResult(status -> seedAll());
        } catch (final SeedStepFailedException exception) {
            log.warn("[SEED] '{}' 단계에서 시연 데이터를 만들지 못했습니다. "
                            + "서비스는 그대로 기동합니다. 원인={}",
                    exception.step(),
                    exception.causeType());
        } catch (final RuntimeException exception) {
            log.warn("[SEED] 시연 데이터를 만들지 못했습니다. 서비스는 그대로 기동합니다. 원인={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void seedAll() {
        runStep("시연 사용자", this::seedDemoUser);
        runStep("두 번째 사용자", this::seedOtherUser);
        log.info("[SEED] 시연 데이터를 확인했습니다. PIN 로그인: {} / {}",
                DEMO_PHONE, seedProperties.pin());
    }

    /**
     * 어느 단계에서 실패했는지 붙여서 다시 던진다.
     *
     * <p>시드는 사용자·계좌·기기·거래를 차례로 만들고 각각 UNIQUE 제약이 있다. 예외 클래스
     * 이름만 남기면 어디를 봐야 할지 알 수 없다. 실제로 운영은 {@code users.phone_hash} 에서,
     * 같은 상황을 재현한 로컬은 {@code accounts.fintech_use_num} 에서 걸렸다.
     *
     * <p><b>예외 메시지는 싣지 않는다.</b> {@code Duplicate entry '199000...'} 처럼 계좌
     * 식별자가 그대로 들어 있어, 로그에 남기면 민감정보를 남기지 않는다는 규칙을 어긴다.
     * 단계 이름만으로도 어느 코드를 볼지는 정해진다.
     */
    private void runStep(final String step, final Runnable seeding) {
        try {
            seeding.run();
        } catch (final RuntimeException exception) {
            throw new SeedStepFailedException(step, exception);
        }
    }

    /** 실패한 시드 단계를 알리는 예외. 원인 예외는 트랜잭션 롤백을 위해 그대로 감싼다. */
    private static final class SeedStepFailedException extends RuntimeException {

        private final String step;

        private SeedStepFailedException(final String step, final RuntimeException cause) {
            super(cause);
            this.step = step;
        }

        private String step() {
            return this.step;
        }

        private String causeType() {
            return getCause().getClass().getSimpleName();
        }
    }

    /**
     * 시연의 주인공. 세 위험도를 모두 재현할 수 있는 상태로 만든다.
     *
     * <p>LOW는 신뢰 기기·거래 이력·30일 프로필이 모두 갖춰져야 나온다. 하나라도 빠지면
     * Mock FDS가 MEDIUM으로 올려 정상 송금 시연에도 보호자 알림이 나간다.
     */
    private void seedDemoUser() {
        if (isPhoneTaken(DEMO_PHONE)) {
            log.info("[SEED] 시연 사용자가 이미 있습니다. 건너뜁니다.");
            return;
        }
        final User user = userRepository.save(User.builder()
                .name("김철수")
                .phone(encrypt(DEMO_PHONE))
                .phoneHash(hash(DEMO_PHONE))
                .birthDate(LocalDate.of(1950, 3, 2))
                .userType(UserType.SENIOR)
                .build());
        registerPin(user);
        registerTrustedDevice(user);

        final OpenbankingConnection connection = connect(user, "1100000001");
        savePrimaryAccount(user, connection);
        saveAccount(user, connection, SAVING_FINTECH_NUM, "088", "신한은행",
                "110-***-****22", "비상금 통장", AccountType.SAVING);

        // 거래 이력이 있는 상대 — LOW 시연용
        saveRecipient(user, "엄마", "088", "110123456789", "이영자", 5);
        saveRecipient(user, "아들", "004", "004987654321", "김민수", 2);
        // 처음 보내는 상대 — MEDIUM 시연용
        saveRecipient(user, "김영희", "020", "020112233445", "김영희", 0);

        saveEstablishedProfile(user);
        linkGuardian(user);
    }

    /**
     * 소유권 검증 시연용 두 번째 사용자.
     *
     * <p>남의 계좌·수취인에 접근하면 거부된다는 것을 보이려면 "남"이 실재해야 한다.
     * 이 사용자로 이체하지는 않으므로 Mock이 모르는 계좌번호로 둔다.
     */
    private void seedOtherUser() {
        if (isPhoneTaken(OTHER_PHONE)) {
            log.info("[SEED] 두 번째 사용자의 번호가 이미 쓰이고 있습니다. 건너뜁니다.");
            return;
        }
        final User user = userRepository.save(User.builder()
                .name("이순자")
                .phone(encrypt(OTHER_PHONE))
                .phoneHash(hash(OTHER_PHONE))
                .birthDate(LocalDate.of(1948, 11, 20))
                .userType(UserType.VISUALLY_IMPAIRED)
                .build());
        registerPin(user);

        final OpenbankingConnection connection = connect(user, "1100000002");
        final Account account = Account.builder()
                .user(user)
                .connection(connection)
                .fintechUseNum(OTHER_FINTECH_NUM)
                .bankCode("011")
                .bankName("농협은행")
                .accountNumMasked("352-****-**99")
                .alias("생활비 통장")
                .accountType(AccountType.DEPOSIT)
                .build();
        account.designateAsPrimary();
        accountRepository.save(account);

        saveRecipient(user, "딸", "004", "004555666777", "이미영", 1);
    }

    private void savePrimaryAccount(final User user, final OpenbankingConnection connection) {
        final Account account = saveAccount(user, connection, PRIMARY_FINTECH_NUM, "004",
                "국민은행", "123456-**-*****1", "생활비 통장", AccountType.DEPOSIT);
        account.designateAsPrimary();
    }

    private Account saveAccount(
            final User user,
            final OpenbankingConnection connection,
            final String fintechUseNum,
            final String bankCode,
            final String bankName,
            final String accountNumMasked,
            final String alias,
            final AccountType accountType
    ) {
        return accountRepository.save(Account.builder()
                .user(user)
                .connection(connection)
                .fintechUseNum(fintechUseNum)
                .bankCode(bankCode)
                .bankName(bankName)
                .accountNumMasked(accountNumMasked)
                .alias(alias)
                .accountType(accountType)
                .build());
    }

    private OpenbankingConnection connect(final User user, final String userSeqNo) {
        return connectionRepository.save(OpenbankingConnection.builder()
                .user(user)
                .userSeqNo(userSeqNo)
                .accessToken(encrypt("seed-access-token-" + userSeqNo))
                .refreshToken(encrypt("seed-refresh-token-" + userSeqNo))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .scope("login inquiry transfer")
                .build());
    }

    private void registerPin(final User user) {
        userCredentialRepository.save(UserCredential.builder()
                .user(user)
                .pinHash(passwordEncoder.encode(seedProperties.pin()))
                .biometricEnabled(false)
                .build());
    }

    /**
     * 신뢰 기기가 없으면 Mock FDS가 모든 이체를 MEDIUM 이상으로 올린다.
     *
     * <p>프런트는 이 {@code deviceUuid}를 그대로 보내야 LOW 시연이 재현된다.
     */
    private void registerTrustedDevice(final User user) {
        final Device device = Device.builder()
                .user(user)
                .deviceUuid(seedProperties.deviceUuid())
                .deviceModel("Seed Demo Device")
                .osVersion("Android 14")
                .build();
        device.trust();
        device.recordLogin(LocalDateTime.now());
        deviceRepository.save(device);
    }

    private void saveRecipient(
            final User user,
            final String nickname,
            final String bankCode,
            final String accountNum,
            final String holderName,
            final int transferCount
    ) {
        final TransferRecipient recipient = TransferRecipient.builder()
                .user(user)
                .nickname(nickname)
                .bankCode(bankCode)
                .accountNum(encrypt(accountNum))
                .accountNumHash(hash(accountNum))
                .holderName(holderName)
                .build();
        for (int count = 0; count < transferCount; count++) {
            recipient.recordTransfer(LocalDateTime.now().minusDays(transferCount - count));
        }
        recipientRepository.save(recipient);
    }

    /** 30일 프로필이 비면 cold start 로 잡혀 LOW 가 나오지 않는다. */
    private void saveEstablishedProfile(final User user) {
        final UserTransferProfile profile = UserTransferProfile.builder().user(user).build();
        profile.refresh(50_000L, 80_000L, new BigDecimal("10000.00"), "[10,14,19]", 12, 3);
        profileRepository.save(profile);
    }

    private void linkGuardian(final User user) {
        final GuardianLink link = GuardianLink.builder()
                .protecteeUser(user)
                .guardianName("김보호")
                .guardianPhone(encrypt(GUARDIAN_PHONE))
                .relation("자녀")
                .inviteToken(UUID.randomUUID().toString())
                .inviteExpiresAt(LocalDateTime.now().plusDays(30))
                .permissionScope("[\"ALERT\"]")
                .build();
        link.accept(null, LocalDateTime.now());
        guardianLinkRepository.save(link);
    }

    private String encrypt(final String plainText) {
        return sensitiveDataCrypto.encrypt(plainText);
    }

    private String hash(final String plainText) {
        return sensitiveDataCrypto.hash(plainText);
    }

    /**
     * 시드 대상 번호를 이미 누가 쓰고 있는지 본다.
     *
     * <p>시드 사용자가 만든 계정인지, 사람이 회원가입으로 만든 계정인지는 구분하지 않는다.
     * {@code users.phone_hash}에 UNIQUE 제약이 있어 어느 쪽이든 INSERT 하면 터진다.
     * 실제로 시드용 번호를 쓰는 테스트 계정 하나 때문에 운영이 멈춘 적이 있다.
     */
    private boolean isPhoneTaken(final String phoneNumber) {
        return userRepository.findByPhoneHash(hash(phoneNumber)).isPresent();
    }
}
