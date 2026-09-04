package com.movi_backend.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.infrastructure.openbanking.MockOpenBankingClient;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.account.repository.BalanceSnapshotRepository;
import com.movi_backend.domain.account.repository.OpenbankingConnectionRepository;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.domain.auth.application.DeviceRegistrationService;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.fds.repository.FdsAssessmentRepository;
import com.movi_backend.domain.fds.repository.UserTransferProfileRepository;
import com.movi_backend.domain.fds.entity.UserTransferProfile;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.guardian.repository.NotificationRepository;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransactionRepository;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.domain.voice.repository.VoiceCommandRepository;
import com.movi_backend.domain.voice.repository.VoiceSessionRepository;
import com.movi_backend.global.security.SensitiveDataCrypto;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * 직접 입력(키보드·터치) 송금 E2E.
 *
 * <p>음성을 쓸 수 없는 상황에서도 송금을 끝낼 수 있어야 하고, 그 경로도 <b>똑같이 돈이
 * 나간다.</b> 음성 경로는 {@link MviE2eScenarioTest}의 12개 시나리오로 종단 검증되는데
 * 직접 입력에는 같은 수준의 검증이 없었다.
 *
 * <p>단위 테스트는 {@code TransferExecutionService}를 목으로 대체하므로 컨트롤러부터 FDS·
 * 오픈뱅킹까지 실제로 이어지는지, 멱등성이 DB 제약까지 포함해 동작하는지를 보지 못한다.
 * 여기서는 HTTP 요청부터 DB 상태까지 실제 스택을 관통한다.
 */
@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
})
@ActiveProfiles("test")
class DirectTransferE2eTest {

    private static final String PRIMARY_FINTECH_NUM = "199000000000000000000001";
    private static final String SAVING_FINTECH_NUM = "199000000000000000000002";
    private static final long PRIMARY_BALANCE = 530_000L;
    private static final long SAVING_BALANCE = 1_200_000L;

    /** Mock FDS 가 HIGH 로 판정하는 하한 */
    private static final long HIGH_RISK_AMOUNT = 700_000L;

    private static final String RECIPIENT_ACCOUNT_NUM = "110123456789";

    /**
     * 주소록에 없는 계좌. DemoAccountDirectory 가 예금주 "김영희"로 답한다.
     *
     * <p>등록하지 않은 상대에게도 은행과 계좌번호를 말해 보낼 수 있어야 한다. 대신
     * 예금주조회로 확인된 계좌여야 하고, 확인됐다고 주소록에 올라가지는 않는다.
     */
    private static final String UNREGISTERED_ACCOUNT_NUM = "020112233445";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OpenbankingConnectionRepository connectionRepository;

    @Autowired
    private BalanceSnapshotRepository balanceSnapshotRepository;

    @Autowired
    private TransferRecipientRepository recipientRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private FdsAssessmentRepository fdsAssessmentRepository;

    @Autowired
    private UserTransferProfileRepository profileRepository;

    @Autowired
    private GuardianLinkRepository guardianLinkRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private VoiceSessionRepository voiceSessionRepository;

    @Autowired
    private VoiceCommandRepository voiceCommandRepository;

    @Autowired
    private MockOpenBankingClient mockOpenBankingClient;

    @Autowired
    private DeviceRegistrationService deviceRegistrationService;

    @Autowired
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private MockMvc mockMvc;
    private User user;
    private User otherUser;
    private Account primaryAccount;
    private Account savingAccount;
    private TransferRecipient recipient;
    private Account otherAccount;
    private TransferRecipient otherRecipient;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        resetMockBalances();
        notificationRepository.deleteAll();
        fdsAssessmentRepository.deleteAll();
        transactionRepository.deleteAll();
        transferRepository.deleteAll();
        voiceCommandRepository.deleteAll();
        voiceSessionRepository.deleteAll();
        deleteAllDevices();
        guardianLinkRepository.deleteAll();
        recipientRepository.deleteAll();
        balanceSnapshotRepository.deleteAll();
        accountRepository.deleteAll();
        connectionRepository.deleteAll();
        profileRepository.deleteAll();
        userCredentialCleanup();
        userRepository.deleteAll();

        user = saveUser("김철수", "01012345678", UserType.SENIOR);
        final OpenbankingConnection connection = saveConnection(user, "1100000001");
        primaryAccount = savePrimaryAccount(user, connection);
        savingAccount = saveAccount(user, connection, SAVING_FINTECH_NUM, "088", "신한은행",
                "110-***-****22", "비상금 통장", AccountType.SAVING);
        recipient = saveRecipient(user, "엄마", RECIPIENT_ACCOUNT_NUM, "이영자");

        otherUser = saveUser("이순자", "01099998888", UserType.VISUALLY_IMPAIRED);
        final OpenbankingConnection otherConnection = saveConnection(otherUser, "1100000002");
        otherAccount = saveAccount(otherUser, otherConnection, "199000000000000000000901",
                "011", "농협은행", "352-****-**99", "생활비 통장", AccountType.DEPOSIT);
        otherRecipient = saveRecipient(otherUser, "딸", "004555666777", "이미영");
    }

    // ---------------------------------------------------------------- 1

    @Test
    @DisplayName("1. 등록 수취인 목록의 계좌번호는 마스킹돼 나온다")
    void 수취인_목록의_계좌번호는_마스킹된다() throws Exception {
        mockMvc.perform(get("/api/transfers/recipients").header("X-Dev-User-Id", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.recipients[0].nickname").value("엄마"))
                .andExpect(jsonPath("$.data.recipients[0].maskedAccountNumber").value("***6789"));
    }

    // ---------------------------------------------------------------- 2

    @Test
    @DisplayName("2. 검토를 거쳐 확인하면 이체가 완료된다")
    void 검토를_거쳐_확인하면_이체가_완료된다() throws Exception {
        makeLowRiskContext();
        final String confirmationId = review(primaryAccount, 50_000L);

        final MvcResult result = executeTransfer(confirmationId, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus())
                .withFailMessage("송금 실패: %s", result.getResponse().getContentAsString())
                .isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .contains("COMPLETED")
                .contains("LOW")
                .contains("이영자 님에게 5만원을 보냈어요.");
        assertThat(transferRepository.findAll()).singleElement()
                .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETED));
        assertThat(currentBalance(PRIMARY_FINTECH_NUM)).isEqualTo(PRIMARY_BALANCE - 50_000L);
    }

    // ---------------------------------------------------------------- 3

    @Test
    @DisplayName("3. 검토만으로는 이체가 생기지 않는다")
    void 검토만으로는_이체가_생기지_않는다() throws Exception {
        review(primaryAccount, 50_000L);

        assertThat(transferRepository.findAll()).isEmpty();
        assertThat(currentBalance(PRIMARY_FINTECH_NUM)).isEqualTo(PRIMARY_BALANCE);
    }

    // ---------------------------------------------------------------- 4

    @Test
    @DisplayName("4. 같은 확인을 다른 멱등성 키로 실행하면 거부되고 이체는 1건이다")
    void 같은_확인을_다른_키로_실행하면_이체는_1건이다() throws Exception {
        makeLowRiskContext();
        final String confirmationId = review(primaryAccount, 50_000L);
        executeTransfer(confirmationId, UUID.randomUUID().toString());

        // 실행 버튼을 두 번 눌러 키가 새로 만들어진 상황
        final MvcResult second =
                executeTransfer(confirmationId, UUID.randomUUID().toString());

        assertThat(second.getResponse().getContentAsString()).contains("TRANSFER_4007");
        assertThat(transferRepository.findAll()).hasSize(1);
        assertThat(currentBalance(PRIMARY_FINTECH_NUM)).isEqualTo(PRIMARY_BALANCE - 50_000L);
    }

    // ---------------------------------------------------------------- 5

    @Test
    @DisplayName("5. 같은 키로 동시에 요청해도 이체는 1건이다")
    void 같은_키로_동시에_요청해도_이체는_1건이다() throws Exception {
        makeLowRiskContext();
        final String confirmationId = review(primaryAccount, 50_000L);
        final String idempotencyKey = UUID.randomUUID().toString();

        final ConcurrentRequestResult result = requestConcurrently(
                () -> executeTransfer(confirmationId, idempotencyKey)
        );

        result.assertOnlyExpectedStatuses();
        assertThat(transferRepository.findAll()).hasSize(1);
        assertThat(currentBalance(PRIMARY_FINTECH_NUM)).isEqualTo(PRIMARY_BALANCE - 50_000L);
    }

    // ---------------------------------------------------------------- 6

    @Test
    @DisplayName("6. 잘못된 확인 ID로는 이체가 실행되지 않는다")
    void 잘못된_확인_ID로는_이체가_실행되지_않는다() throws Exception {
        review(primaryAccount, 50_000L);

        final MvcResult result = executeTransfer(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null
        );

        assertThat(result.getResponse().getContentAsString()).contains("TRANSFER_4007");
        assertThat(transferRepository.findAll()).isEmpty();
        assertThat(currentBalance(PRIMARY_FINTECH_NUM)).isEqualTo(PRIMARY_BALANCE);
    }

    // ---------------------------------------------------------------- 7

    @Test
    @DisplayName("7. 다른 사용자의 계좌·수취인으로는 검토조차 되지 않는다")
    void 다른_사용자의_계좌와_수취인으로는_검토조차_되지_않는다() throws Exception {
        mockMvc.perform(reviewRequest(user.getId(), otherRecipient.getId(), 50_000L, null))
                .andExpect(status().isForbidden());

        mockMvc.perform(reviewRequest(
                        user.getId(),
                        recipient.getId(),
                        50_000L,
                        otherAccount.getId()
                ))
                .andExpect(status().isForbidden());

        assertThat(transferRepository.findAll()).isEmpty();
    }

    // ---------------------------------------------------------------- 8

    @Test
    @DisplayName("8. 고위험 이체는 차단되고 오픈뱅킹 잔액이 줄지 않는다")
    void 고위험_이체는_차단되고_잔액이_줄지_않는다() throws Exception {
        // 기본 계좌는 53만원이라 70만원이 잔액에서 먼저 막힌다. 120만원 계좌에서 보낸다
        final String confirmationId = review(savingAccount, HIGH_RISK_AMOUNT);

        final MvcResult result =
                executeTransfer(confirmationId, UUID.randomUUID().toString(), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .contains("BLOCKED")
                .contains("HIGH");
        assertThat(transferRepository.findAll()).singleElement()
                .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TransferStatus.BLOCKED));
        assertThat(currentBalance(SAVING_FINTECH_NUM)).isEqualTo(SAVING_BALANCE);
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    // ---------------------------------------------------------------- 9

    @Test
    @DisplayName("9. 응답 어디에도 계좌번호 원문이 없다")
    void 응답_어디에도_계좌번호_원문이_없다() throws Exception {
        makeLowRiskContext();

        final String recipients = mockMvc
                .perform(get("/api/transfers/recipients").header("X-Dev-User-Id", user.getId()))
                .andReturn().getResponse().getContentAsString();
        final MvcResult reviewResult = mockMvc
                .perform(reviewRequest(user.getId(), recipient.getId(), 50_000L, null))
                .andReturn();
        final String reviewBody = reviewResult.getResponse().getContentAsString();
        final String executeBody = executeTransfer(
                readConfirmationId(reviewBody),
                UUID.randomUUID().toString()
        ).getResponse().getContentAsString();

        assertThat(recipients).doesNotContain(RECIPIENT_ACCOUNT_NUM);
        assertThat(reviewBody).doesNotContain(RECIPIENT_ACCOUNT_NUM);
        assertThat(executeBody).doesNotContain(RECIPIENT_ACCOUNT_NUM);
    }

    // ---------------------------------------------------------------- 10

    @Test
    @DisplayName("10. 등록하지 않은 계좌도 예금주가 확인되면 보낼 수 있고 주소록은 그대로다")
    void 미등록_계좌로_보내도_주소록은_그대로다() throws Exception {
        makeLowRiskContext();

        final MvcResult reviewResult = mockMvc
                .perform(oneTimeReviewRequest("020", UNREGISTERED_ACCOUNT_NUM, 30_000L))
                .andReturn();
        final String reviewBody = reviewResult.getResponse().getContentAsString();
        assertThat(reviewResult.getResponse().getStatus())
                .withFailMessage("검토 실패: %s", reviewBody)
                .isEqualTo(200);

        final MvcResult executed = executeTransfer(
                readConfirmationId(reviewBody), UUID.randomUUID().toString());

        // 확인 문장은 사용자가 적은 이름이 아니라 조회로 확인된 예금주를 읽는다
        assertThat(reviewBody).contains("김영희");
        assertThat(executed.getResponse().getContentAsString()).contains("COMPLETED");
        assertThat(currentBalance(PRIMARY_FINTECH_NUM)).isEqualTo(PRIMARY_BALANCE - 30_000L);

        // 주소록은 늘지 않는다 — 이름은 사용자가 등록 흐름에서 직접 지을 때만 붙는다
        assertThat(addressBookCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 11

    @Test
    @DisplayName("11. 확인하지 않고 두면 주소록도 잔액도 그대로다")
    void 확인하지_않으면_주소록도_잔액도_그대로다() throws Exception {
        makeLowRiskContext();

        mockMvc.perform(oneTimeReviewRequest("020", UNREGISTERED_ACCOUNT_NUM, 30_000L))
                .andExpect(status().isOk());

        assertThat(transferRepository.findAll()).isEmpty();
        assertThat(currentBalance(PRIMARY_FINTECH_NUM)).isEqualTo(PRIMARY_BALANCE);
        assertThat(addressBookCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 12

    @Test
    @DisplayName("12. 확인되지 않는 계좌로는 검토조차 되지 않는다")
    void 확인되지_않는_계좌로는_검토되지_않는다() throws Exception {
        // given - 앞자리는 등록 계좌와 같지만 뒷자리가 다른 번호
        final MvcResult result = mockMvc
                .perform(oneTimeReviewRequest("020", "020112230000", 30_000L))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("TRANSFER_4011");
        assertThat(transferRepository.findAll()).isEmpty();
        assertThat(addressBookCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 13

    @Test
    @DisplayName("13. 금액을 고쳐 다시 검토하면 이전 확인으로는 실행되지 않는다")
    void 금액을_고치면_이전_확인으로는_실행되지_않는다() throws Exception {
        makeLowRiskContext();
        final String staleConfirmationId = review(primaryAccount, 50_000L);

        // 사용자가 금액을 고쳐 다시 검토했다
        review(primaryAccount, 30_000L);

        final MvcResult result = executeTransfer(
                staleConfirmationId, UUID.randomUUID().toString());

        assertThat(result.getResponse().getContentAsString()).contains("TRANSFER_4007");
        assertThat(transferRepository.findAll()).isEmpty();
        assertThat(currentBalance(PRIMARY_FINTECH_NUM)).isEqualTo(PRIMARY_BALANCE);
    }

    // ---------------------------------------------------------------- helpers

    /** 주소록에 보이는 수취인 수. 일회성 송금 대상은 세지 않는다. */
    private long addressBookCount() {
        return recipientRepository
                .findAllByUserIdAndAddressBookTrueOrderByNicknameAsc(user.getId())
                .size();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            oneTimeReviewRequest(
                    final String bankCode,
                    final String accountNumber,
                    final long amount
            ) {
        return post("/api/transfers/review")
                .header("X-Dev-User-Id", user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bankCode":"%s","accountNumber":"%s","amount":%d,"fromAccountId":null}
                        """.formatted(bankCode, accountNumber, amount));
    }

    private String review(final Account fromAccount, final long amount) throws Exception {
        final MvcResult result = mockMvc
                .perform(reviewRequest(user.getId(), recipient.getId(), amount, fromAccount.getId()))
                .andReturn();
        final String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus())
                .withFailMessage("검토 실패: %s", body)
                .isEqualTo(200);
        return readConfirmationId(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            reviewRequest(
                    final Long userId,
                    final Long recipientId,
                    final long amount,
                    final Long fromAccountId
            ) {
        final String accountField = fromAccountId == null ? "null" : fromAccountId.toString();
        return post("/api/transfers/review")
                .header("X-Dev-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipientId":%d,"amount":%d,"fromAccountId":%s}
                        """.formatted(recipientId, amount, accountField));
    }

    /**
     * 직접 입력은 음성 세션이 없으므로 <b>실행 요청이 기기를 실어 보낸다.</b>
     * 이것이 빠지면 비신뢰 기기로 평가돼 소액·기존 수취인이어도 MEDIUM 이 된다.
     */
    private MvcResult executeTransfer(
            final String confirmationId,
            final String idempotencyKey
    ) throws Exception {
        return executeTransfer(confirmationId, idempotencyKey, DEVICE_UUID);
    }

    private MvcResult executeTransfer(
            final String confirmationId,
            final String idempotencyKey,
            final String deviceUuid
    ) throws Exception {
        final String deviceField = deviceUuid == null ? "null" : "\"" + deviceUuid + "\"";
        return mockMvc.perform(post("/api/transfers")
                        .header("X-Dev-User-Id", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmationId":"%s","idempotencyKey":"%s","deviceUuid":%s}
                                """.formatted(confirmationId, idempotencyKey, deviceField)))
                .andReturn();
    }

    private String readConfirmationId(final String body) {
        final Matcher matcher = Pattern.compile("\"confirmationId\":\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find())
                .withFailMessage("확인 ID 없음: %s", body)
                .isTrue();
        return matcher.group(1);
    }

    /**
     * LOW 판정에 필요한 조건을 모두 갖춘다.
     *
     * <p>거래 이력·30일 프로필·신뢰 기기 셋 중 하나라도 빠지면 Mock FDS 가 MEDIUM 으로
     * 올린다. 정상 송금에도 보호자 알림이 나가면 위험도 분기 자체가 보이지 않는다.
     */
    private void makeLowRiskContext() {
        transactionTemplate.executeWithoutResult(status -> {
            final TransferRecipient managed = recipientRepository.findById(recipient.getId())
                    .orElseThrow();
            managed.recordTransfer(LocalDateTime.now().minusDays(3));
            managed.recordTransfer(LocalDateTime.now().minusDays(2));
            recipientRepository.save(managed);

            final UserTransferProfile profile = UserTransferProfile.builder()
                    .user(entityManager.getReference(User.class, user.getId()))
                    .build();
            profile.refresh(50_000L, 80_000L, new BigDecimal("10000.00"), null, 12, 3);
            profileRepository.save(profile);
        });
        registerTrustedDevice();
    }

    private void registerTrustedDevice() {
        transactionTemplate.executeWithoutResult(status ->
                deviceRegistrationService.registerTrusted(
                        user.getId(),
                        DEVICE_UUID,
                        "Direct E2E",
                        "Android 14"
                ));
    }

    private static final String DEVICE_UUID = "direct-e2e-device";

    private User saveUser(final String name, final String phone, final UserType userType) {
        return userRepository.save(User.builder()
                .name(name)
                .phone(sensitiveDataCrypto.encrypt(phone))
                .phoneHash(sensitiveDataCrypto.hash(phone))
                .birthDate(LocalDate.of(1950, 3, 2))
                .userType(userType)
                .build());
    }

    private OpenbankingConnection saveConnection(final User owner, final String userSeqNo) {
        return connectionRepository.save(OpenbankingConnection.builder()
                .user(owner)
                .userSeqNo(userSeqNo)
                .accessToken(sensitiveDataCrypto.encrypt("mock-access-token"))
                .refreshToken(sensitiveDataCrypto.encrypt("mock-refresh-token"))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .scope("login inquiry transfer")
                .build());
    }

    private Account savePrimaryAccount(final User owner, final OpenbankingConnection connection) {
        final Account account = Account.builder()
                .user(owner)
                .connection(connection)
                .fintechUseNum(PRIMARY_FINTECH_NUM)
                .bankCode("004")
                .bankName("국민은행")
                .accountNumMasked("123456-**-*****1")
                .alias("생활비 통장")
                .accountType(AccountType.DEPOSIT)
                .build();
        account.designateAsPrimary();
        return accountRepository.save(account);
    }

    private Account saveAccount(
            final User owner,
            final OpenbankingConnection connection,
            final String fintechUseNum,
            final String bankCode,
            final String bankName,
            final String accountNumMasked,
            final String alias,
            final AccountType accountType
    ) {
        return accountRepository.save(Account.builder()
                .user(owner)
                .connection(connection)
                .fintechUseNum(fintechUseNum)
                .bankCode(bankCode)
                .bankName(bankName)
                .accountNumMasked(accountNumMasked)
                .alias(alias)
                .accountType(accountType)
                .build());
    }

    private TransferRecipient saveRecipient(
            final User owner,
            final String nickname,
            final String accountNum,
            final String holderName
    ) {
        return recipientRepository.save(TransferRecipient.builder()
                .user(owner)
                .nickname(nickname)
                .bankCode("088")
                .accountNum(sensitiveDataCrypto.encrypt(accountNum))
                .accountNumHash(sensitiveDataCrypto.hash(accountNum))
                .holderName(holderName)
                .addressBook(true)
                .verifiedAt(java.time.LocalDateTime.now())
                .build());
    }

    private void deleteAllDevices() {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("delete from Device").executeUpdate());
    }

    private void userCredentialCleanup() {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("delete from UserCredential").executeUpdate());
    }

    @SuppressWarnings("unchecked")
    private long currentBalance(final String fintechUseNum) {
        final Map<String, AtomicLong> balances = (Map<String, AtomicLong>)
                ReflectionTestUtils.getField(mockOpenBankingClient, "balances");
        return balances.get(fintechUseNum).get();
    }

    @SuppressWarnings("unchecked")
    private void resetMockBalances() {
        final Map<String, AtomicLong> balances = (Map<String, AtomicLong>)
                ReflectionTestUtils.getField(mockOpenBankingClient, "balances");
        if (balances != null) {
            balances.get(PRIMARY_FINTECH_NUM).set(PRIMARY_BALANCE);
            balances.get(SAVING_FINTECH_NUM).set(SAVING_BALANCE);
        }
    }

    /**
     * 같은 요청을 여러 스레드에서 동시에 보낸다.
     *
     * <p>예외를 삼키거나 {@code await} 결과를 버리면 요청이 멈춰도, 절반이 500으로 터져도
     * 테스트가 초록불이 된다. 경합에서 밀린 요청이 <b>409로 밀리는 것과 500으로 터지는 것은
     * 다르다</b> — 500은 사용자에게 오류 문구가 TTS로 읽히는 상황이다.
     */
    private ConcurrentRequestResult requestConcurrently(final ConcurrentRequest request)
            throws InterruptedException {
        final int threads = 4;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        final List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        final List<String> failures = Collections.synchronizedList(new ArrayList<>());

        for (int index = 0; index < threads; index++) {
            pool.submit(() -> {
                try {
                    start.await();
                    statuses.add(request.perform().getResponse().getStatus());
                } catch (final Exception exception) {
                    failures.add(exception.toString());
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        final boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        return new ConcurrentRequestResult(finished, List.copyOf(statuses), List.copyOf(failures));
    }

    @FunctionalInterface
    private interface ConcurrentRequest {
        MvcResult perform() throws Exception;
    }

    private record ConcurrentRequestResult(
            boolean finished,
            List<Integer> statuses,
            List<String> failures
    ) {

        /**
         * 200(기존 결과 반환)과 409(아직 처리 중)만 정상이다.
         *
         * <p>둘 다 "돈은 한 번만 나갔다"와 모순되지 않는다. 그 밖의 상태는 멱등성 처리가
         * 아니라 결함이다.
         */
        private void assertOnlyExpectedStatuses() {
            assertThat(this.finished)
                    .withFailMessage("동시 요청이 30초 안에 끝나지 않았다: %s", this.statuses)
                    .isTrue();
            assertThat(this.failures)
                    .withFailMessage("요청 중 예외 발생: %s", this.failures)
                    .isEmpty();
            assertThat(this.statuses)
                    .withFailMessage("허용되지 않은 응답 상태: %s", this.statuses)
                    .isNotEmpty()
                    .allMatch(status -> status == 200 || status == 409);
            assertThat(this.statuses)
                    .withFailMessage("성공한 요청이 없다: %s", this.statuses)
                    .contains(200);
        }
    }
}
