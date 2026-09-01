package com.movi_backend.global.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.movi_backend.domain.account.application.BalanceInquiryService;
import com.movi_backend.domain.account.dto.response.BalanceResponse;
import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.account.repository.OpenbankingConnectionRepository;
import com.movi_backend.domain.auth.application.AuthenticationService;
import com.movi_backend.domain.auth.dto.request.PinLoginRequest;
import com.movi_backend.domain.auth.dto.response.LoginResponse;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.DeviceRepository;
import com.movi_backend.domain.auth.repository.UserCredentialRepository;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.fds.repository.UserTransferProfileRepository;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.security.SensitiveDataCrypto;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "movi.seed.enabled=true",
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
})
@ActiveProfiles("test")
class DemoDataSeederIntegrationTest {

    private static final String DEMO_PHONE = "01012345678";
    private static final String OTHER_PHONE = "01099998888";

    /** {@code MockOpenBankingClient}가 들고 있는 기본 계좌 잔액. 폴백값과 우연히 같다 */
    private static final long PRIMARY_BALANCE = 530_000L;

    /** HIGH 판정 기준인 70만원을 넘어야 잔액 부족에 먼저 걸리지 않는다 */
    private static final long HIGH_RISK_THRESHOLD = 700_000L;

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCredentialRepository userCredentialRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OpenbankingConnectionRepository connectionRepository;

    @Autowired
    private TransferRecipientRepository recipientRepository;

    @Autowired
    private UserTransferProfileRepository profileRepository;

    @Autowired
    private GuardianLinkRepository guardianLinkRepository;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private BalanceInquiryService balanceInquiryService;

    @Autowired
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Autowired
    private SeedProperties seedProperties;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static DemoDataSeederIntegrationTest instance;

    @Test
    @DisplayName("시드를 켜면 시연에 필요한 사용자와 계좌가 만들어진다")
    void 시드를_켜면_시연에_필요한_사용자와_계좌가_만들어진다() {
        instance = this;

        // then
        assertThat(findDemoUser()).isNotNull();
        assertThat(findUser(OTHER_PHONE)).isNotNull();
        assertThat(accountRepository.findAllByUserIdAndActiveTrueOrderByPrimaryDescIdAsc(findDemoUser().getId()))
                .hasSize(2);
    }

    @Test
    @DisplayName("두 번 실행해도 데이터가 중복되지 않는다")
    void 두_번_실행해도_데이터가_중복되지_않는다() {
        instance = this;
        // given — 컨텍스트 기동 시 이미 한 번 실행됐다
        final long userCount = userRepository.count();
        final long accountCount = accountRepository.count();

        // when — 재기동을 흉내 낸다
        transactionTemplate.executeWithoutResult(status -> demoDataSeeder.run(null));

        // then
        assertThat(userRepository.count()).isEqualTo(userCount);
        assertThat(accountRepository.count()).isEqualTo(accountCount);
    }

    /**
     * 시드용 번호를 사람이 회원가입으로 먼저 써 버린 상황.
     *
     * <p>운영에서 실제로 일어났다. 시드 사용자는 지워졌는데 두 번째 사용자 번호를 쓰는 계정이
     * 남아 있어, 시더가 매 기동마다 UNIQUE 제약에 걸려 예외를 던졌다. ApplicationRunner 예외는
     * 컨텍스트를 닫으므로 컨테이너가 971번 재시작했고 서비스는 502를 냈다.
     *
     * <p>시드를 못 만드는 것은 감수해도, 그 때문에 서버가 뜨지 못하는 것은 감수하지 않는다.
     */
    @Test
    @DisplayName("시드 번호를 다른 계정이 이미 쓰고 있어도 기동을 막지 않는다")
    void 시드_번호가_이미_쓰이고_있어도_기동을_막지_않는다() {
        instance = this;
        // given — 시드 사용자의 해시를 바꿔 가드가 못 찾게 만든다.
        // 사용자를 지우면 계좌 FK에 걸리므로, 운영에서 일어난 상태(가드는 못 찾고
        // 두 번째 번호는 남이 쓰는 중)만 똑같이 재현한다.
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery(
                                "update User u set u.phoneHash = :changed where u.phoneHash = :demo")
                        .setParameter("changed", "seed-guard-miss")
                        .setParameter("demo", sensitiveDataCrypto.hash(DEMO_PHONE))
                        .executeUpdate());
        assertThat(userRepository.findByPhoneHash(sensitiveDataCrypto.hash(DEMO_PHONE)))
                .as("가드가 시드 사용자를 못 찾아야 재현이 된다")
                .isEmpty();
        assertThat(userRepository.findByPhoneHash(sensitiveDataCrypto.hash(OTHER_PHONE)))
                .as("두 번째 사용자 번호는 남아 있어야 충돌이 재현된다")
                .isPresent();

        // when & then — 예외가 밖으로 나가면 운영에서 프로세스가 죽는다
        transactionTemplate.executeWithoutResult(status -> demoDataSeeder.run(null));
    }

    @Test
    @DisplayName("시드된 사용자로 PIN 로그인이 된다")
    void 시드된_사용자로_PIN_로그인이_된다() {
        instance = this;

        // when
        final LoginResponse response = authenticationService.loginWithPin(
                new PinLoginRequest(DEMO_PHONE, seedProperties.pin(), null, null, null)
        );

        // then
        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("기본 계좌 잔액조회가 Mock이 들고 있는 실제 금액을 반환한다")
    void 기본_계좌_잔액조회가_Mock이_들고_있는_실제_금액을_반환한다() {
        instance = this;
        // given — Mock 이 모르는 핀테크이용번호면 폴백값이 나와 이체 시연이 어긋난다

        // when
        final BalanceResponse balance = balanceInquiryService.inquire(
                findDemoUser().getId(),
                null
        );

        // then
        assertThat(balance.balanceAmount()).isEqualTo(PRIMARY_BALANCE);
    }

    @Test
    @DisplayName("LOW 판정에 필요한 신뢰 기기와 거래 이력과 프로필이 모두 갖춰진다")
    void LOW_판정에_필요한_조건이_모두_갖춰진다() {
        instance = this;
        // given — 하나라도 빠지면 Mock FDS 가 MEDIUM 으로 올려 정상 송금에도 알림이 나간다
        final User demoUser = findDemoUser();

        // then
        assertThat(deviceRepository.findByUserIdAndDeviceUuid(
                demoUser.getId(),
                seedProperties.deviceUuid()
        )).get().satisfies(device -> assertThat(device.isTrusted()).isTrue());

        assertThat(recipientRepository.findByUserIdAndNickname(demoUser.getId(), "엄마"))
                .get()
                .satisfies(recipient -> assertThat(recipient.isFirstTime()).isFalse());

        assertThat(profileRepository.findById(demoUser.getId()))
                .get()
                .satisfies(profile -> assertThat(profile.getTransferCount30d())
                        .isGreaterThanOrEqualTo(3));
    }

    @Test
    @DisplayName("MEDIUM 시연용으로 처음 보내는 수취인이 준비된다")
    void MEDIUM_시연용으로_처음_보내는_수취인이_준비된다() {
        instance = this;

        // then
        assertThat(recipientRepository.findByUserIdAndNickname(findDemoUser().getId(), "김영희"))
                .get()
                .satisfies(recipient -> assertThat(recipient.isFirstTime()).isTrue());
    }

    @Test
    @DisplayName("HIGH 시연이 잔액 부족에 먼저 걸리지 않도록 고액 계좌를 둔다")
    void HIGH_시연이_잔액_부족에_먼저_걸리지_않도록_고액_계좌를_둔다() {
        instance = this;
        // given — 기본 계좌는 53만원이라 70만원 이체가 FDS 가 아니라 잔액에서 막힌다

        // when
        final BalanceResponse balance = balanceInquiryService.inquire(
                findDemoUser().getId(),
                "비상금 통장"
        );

        // then
        assertThat(balance.balanceAmount()).isGreaterThan(HIGH_RISK_THRESHOLD);
    }

    @Test
    @DisplayName("보호자가 활성 상태로 연결돼 위험 알림을 받을 수 있다")
    void 보호자가_활성_상태로_연결된다() {
        instance = this;

        // then
        assertThat(guardianLinkRepository.findAllByProtecteeUserIdAndStatus(
                findDemoUser().getId(),
                com.movi_backend.domain.guardian.type.GuardianLinkStatus.ACTIVE
        )).isNotEmpty();
    }

    @Test
    @DisplayName("두 번째 사용자의 수취인은 데모 사용자 것과 섞이지 않는다")
    void 두_번째_사용자의_수취인은_데모_사용자_것과_섞이지_않는다() {
        instance = this;
        // given — 소유권 거부 시연에는 "남"이 실재해야 한다
        final User otherUser = findUser(OTHER_PHONE);

        // when
        final List<TransferRecipient> demoRecipients =
                recipientRepository.findAllByUserIdOrderByNicknameAsc(findDemoUser().getId());
        final List<TransferRecipient> otherRecipients =
                recipientRepository.findAllByUserIdOrderByNicknameAsc(otherUser.getId());

        // then
        assertThat(demoRecipients).extracting(TransferRecipient::getNickname)
                .doesNotContainAnyElementsOf(
                        otherRecipients.stream().map(TransferRecipient::getNickname).toList()
                );
    }

    private User findDemoUser() {
        return findUser(DEMO_PHONE);
    }

    private User findUser(final String phone) {
        return userRepository.findByPhoneHash(sensitiveDataCrypto.hash(phone)).orElse(null);
    }

    /**
     * 시드는 커밋된 상태로 남는다. 같은 H2 인스턴스를 쓰는 다른 테스트가 이 데이터를
     * 만나지 않도록 이 클래스가 끝나면 지운다.
     */
    @AfterAll
    static void cleanUp() {
        if (instance == null) {
            return;
        }
        instance.transactionTemplate.executeWithoutResult(status -> {
            deleteAll(instance.entityManager, "Notification");
            deleteAll(instance.entityManager, "GuardianLink");
            deleteAll(instance.entityManager, "TransferRecipient");
            deleteAll(instance.entityManager, "BalanceSnapshot");
            deleteAll(instance.entityManager, "Account");
            deleteAll(instance.entityManager, "OpenbankingConnection");
            deleteAll(instance.entityManager, "UserTransferProfile");
            deleteAll(instance.entityManager, "Device");
            deleteAll(instance.entityManager, "UserCredential");
            deleteAll(instance.entityManager, "User");
        });
    }

    private static void deleteAll(final EntityManager entityManager, final String entityName) {
        entityManager.createQuery("delete from " + entityName).executeUpdate();
    }
}
