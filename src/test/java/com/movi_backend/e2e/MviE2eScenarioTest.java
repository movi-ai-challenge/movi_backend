package com.movi_backend.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.movi_backend.domain.account.entity.Account;
import com.movi_backend.domain.account.entity.OpenbankingConnection;
import com.movi_backend.domain.account.repository.AccountRepository;
import com.movi_backend.domain.account.infrastructure.openbanking.MockOpenBankingClient;
import com.movi_backend.domain.account.repository.BalanceSnapshotRepository;
import com.movi_backend.domain.account.repository.OpenbankingConnectionRepository;
import com.movi_backend.domain.account.type.AccountType;
import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import com.movi_backend.domain.auth.type.UserType;
import com.movi_backend.domain.fds.client.FdsAssessmentClient;
import com.movi_backend.domain.fds.entity.UserTransferProfile;
import com.movi_backend.domain.fds.repository.FdsAssessmentRepository;
import com.movi_backend.domain.fds.repository.UserTransferProfileRepository;
import com.movi_backend.domain.guardian.application.port.SmsNotificationSender;
import com.movi_backend.domain.guardian.repository.GuardianLinkRepository;
import com.movi_backend.domain.guardian.repository.NotificationRepository;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.domain.transfer.repository.TransactionRepository;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransferStatus;
import com.movi_backend.domain.voice.client.VoiceAnalysisClient;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;
import com.movi_backend.domain.voice.client.dto.VoiceEntities;
import com.movi_backend.domain.voice.client.dto.VoiceEntityConfidences;
import com.movi_backend.domain.voice.entity.VoiceSession;
import com.movi_backend.domain.voice.repository.VoiceCommandRepository;
import com.movi_backend.domain.voice.repository.VoiceSessionRepository;
import com.movi_backend.domain.voice.type.VoiceIntent;
import com.movi_backend.domain.voice.type.VoiceSessionStatus;
import com.movi_backend.domain.voice.type.VoiceSlot;
import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import com.movi_backend.domain.auth.application.DeviceRegistrationService;
import com.movi_backend.global.security.SensitiveDataCrypto;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * MVP 완료 조건 E2E (docs/execution-plan.md 1절의 12개 시나리오).
 *
 * <p>HTTP 요청부터 DB 상태까지 실제 스택을 관통한다. 도메인별 단위 테스트가 모두 통과해도
 * 조립하면 깨지는 일이 흔하고, 시연 당일에 발견하면 고칠 시간이 없기 때문이다.
 *
 * <p>외부 연동은 Mock 어댑터를 쓴다. <b>AI Voice만 스텁으로 대체</b>하는데, 기본 Mock은
 * 항상 같은 발화를 돌려줘 HIGH/MEDIUM 분기를 유도할 수 없어서다. FDS는 실제 판정 로직을
 * 그대로 태우고 금액으로 위험도를 움직인다.
 *
 * <p><b>NCP 배포 환경 검증은 이 테스트의 범위가 아니다.</b> execution-plan이 요구하는 최종
 * 조건은 실 서버 통과이며, 여기서는 코드 레벨에서 흐름이 이어지는지를 고정한다.
 */
@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
})
@ActiveProfiles("test")
class MviE2eScenarioTest {

    /** MockOpenBankingClient 의 기본 계좌 핀테크이용번호와 초기 잔액 */
    private static final String PRIMARY_FINTECH_NUM = "199000000000000000000001";
    private static final long PRIMARY_BALANCE = 530_000L;

    private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("0.95");

    /** 등록과 검증이 같은 번호를 보게 묶어 둔다 */
    private static final String GUARDIAN_PHONE = "01055556666";

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
    private GuardianLinkRepository guardianLinkRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserTransferProfileRepository profileRepository;

    @Autowired
    private FdsAssessmentRepository fdsAssessmentRepository;

    @Autowired
    private VoiceSessionRepository voiceSessionRepository;

    @Autowired
    private VoiceCommandRepository voiceCommandRepository;

    @Autowired
    private MockOpenBankingClient mockOpenBankingClient;

    @Autowired
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Autowired
    private DeviceRegistrationService deviceRegistrationService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private VoiceAnalysisClient voiceAnalysisClient;

    /** 기본 판정 로직을 그대로 쓰고 시나리오 9에서만 실패를 주입한다. */
    @MockitoSpyBean
    private FdsAssessmentClient fdsAssessmentClient;

    /**
     * SMS 게이트웨이 경계. test 프로필에서는 Mock 구현이 붙고, 여기서는 그 구현에
     * <b>무엇이 전달되는지</b>를 본다. 운영에서는 같은 자리에 솔라피 구현이 들어간다.
     */
    @MockitoSpyBean
    private SmsNotificationSender smsNotificationSender;

    private MockMvc mockMvc;
    private User user;
    private Account account;
    private TransferRecipient recipient;

    /** 확인 대기 응답으로 받은 값. 확인 발화에 그대로 되돌려 줘야 한다. */
    private String confirmationId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        confirmationId = null;
        Mockito.reset(fdsAssessmentClient, smsNotificationSender);
        willCallRealMethod().given(fdsAssessmentClient).assess(any());
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
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .name("김철수")
                .phone(sensitiveDataCrypto.encrypt("01012345678"))
                .phoneHash(sensitiveDataCrypto.hash("01012345678"))
                .birthDate(LocalDate.of(1950, 3, 2))
                .userType(UserType.SENIOR)
                .build());

        final OpenbankingConnection connection = connectionRepository.save(
                OpenbankingConnection.builder()
                        .user(user)
                        .userSeqNo("1100000001")
                        .accessToken(sensitiveDataCrypto.encrypt("mock-access-token"))
                        .refreshToken(sensitiveDataCrypto.encrypt("mock-refresh-token"))
                        .expiresAt(LocalDateTime.now().plusDays(30))
                        .scope("login inquiry transfer")
                        .build());

        final Account newAccount = Account.builder()
                .user(user)
                .connection(connection)
                .fintechUseNum(PRIMARY_FINTECH_NUM)
                .bankCode("004")
                .bankName("국민은행")
                .accountNumMasked("123456-**-*****1")
                .alias("생활비 통장")
                .accountType(AccountType.DEPOSIT)
                .build();
        newAccount.designateAsPrimary();
        account = accountRepository.save(newAccount);

        recipient = recipientRepository.save(TransferRecipient.builder()
                .user(user)
                .nickname("엄마")
                .bankCode("088")
                .accountNum(sensitiveDataCrypto.encrypt("110123456789"))
                .accountNumHash(sensitiveDataCrypto.hash("110123456789"))
                .holderName("이영자")
                .build());
    }

    // ---------------------------------------------------------------- 1

    @Test
    @DisplayName("1. 기본 계좌 잔액을 조회하면 한국어 음성 안내와 함께 반환한다")
    void 시나리오1_기본_계좌_잔액조회() throws Exception {
        mockMvc.perform(get("/api/accounts/balance").header("X-Dev-User-Id", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.balanceAmount").value(PRIMARY_BALANCE))
                .andExpect(jsonPath("$.voiceMessage").value("국민은행 생활비 통장에 53만원 있어요."));
    }

    // ---------------------------------------------------------------- 2

    @Test
    @DisplayName("2. 저위험 음성 송금이 확인을 거쳐 완료된다")
    void 시나리오2_정상_LOW_음성_송금() throws Exception {
        makeRecipientFamiliar();
        makeProfileEstablished();
        stubUtterance(transferUtterance(50_000L, "엄마"));

        final Long sessionId = startSession(trustDevice());
        awaitConfirmation(sessionId);

        stubUtterance(confirmUtterance());
        final String idempotencyKey = UUID.randomUUID().toString();
        final MvcResult result =
                mockMvc.perform(voiceCommand(sessionId, idempotencyKey)).andReturn();
        assertThat(result.getResponse().getStatus())
                .withFailMessage("송금 실패: %s", result.getResponse().getContentAsString())
                .isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .contains("COMPLETED")
                .contains("LOW")
                .contains("이영자 님에게 5만원을 보냈어요.");

        assertThat(transferRepository.findAll()).singleElement()
                .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TransferStatus.COMPLETED));
    }

    // ---------------------------------------------------------------- 3

    @Test
    @DisplayName("3. 금액이 누락되면 재질문하고 후속 발화로 보완된다")
    void 시나리오3_금액_누락_재질문() throws Exception {
        stubUtterance(transferUtterance(null, "엄마"));
        final Long sessionId = startSession();

        mockMvc.perform(voiceCommand(sessionId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("CLARIFYING"))
                .andExpect(jsonPath("$.data.missingSlots[0]").value("AMOUNT"))
                .andExpect(jsonPath("$.voiceMessage").value("얼마를 보내시겠어요?"));

        stubUtterance(transferUtterance(50_000L, null));
        mockMvc.perform(voiceCommand(sessionId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.amount").value(50_000L))
                .andExpect(jsonPath("$.data.recipient.holderName").value("이영자"));
    }

    // ---------------------------------------------------------------- 4

    @Test
    @DisplayName("4. 수취인이 누락되면 재질문하고 후속 발화로 보완된다")
    void 시나리오4_수취인_누락_재질문() throws Exception {
        stubUtterance(transferUtterance(50_000L, null));
        final Long sessionId = startSession();

        mockMvc.perform(voiceCommand(sessionId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("CLARIFYING"))
                .andExpect(jsonPath("$.data.missingSlots[0]").value("RECIPIENT"))
                .andExpect(jsonPath("$.voiceMessage").value("누구에게 보내시겠어요?"));

        stubUtterance(transferUtterance(null, "엄마"));
        mockMvc.perform(voiceCommand(sessionId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.amount").value(50_000L));
    }

    // ---------------------------------------------------------------- 5

    @Test
    @DisplayName("5. 최종 확인에서 취소하면 이체가 생성되지 않는다")
    void 시나리오5_최종_확인_취소() throws Exception {
        stubUtterance(transferUtterance(50_000L, "엄마"));
        final Long sessionId = startSession();
        awaitConfirmation(sessionId);

        stubUtterance(cancelUtterance());
        mockMvc.perform(voiceCommand(sessionId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("CANCELED"))
                .andExpect(jsonPath("$.voiceMessage").value("송금을 취소했어요."));

        assertThat(transferRepository.findAll()).isEmpty();
        final VoiceSession session = voiceSessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getPendingSlots()).isNull();
    }

    // ---------------------------------------------------------------- 6

    @Test
    @DisplayName("6. 확인 대기 세션이 만료되면 슬롯을 폐기하고 거부한다")
    void 시나리오6_세션_만료() throws Exception {
        stubUtterance(transferUtterance(50_000L, "엄마"));
        final Long sessionId = startSession();
        awaitConfirmation(sessionId);
        expireSession(sessionId);

        mockMvc.perform(voiceCommand(sessionId, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VOICE_4005"));

        final VoiceSession expired = voiceSessionRepository.findById(sessionId).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(VoiceSessionStatus.EXPIRED);
        assertThat(expired.getPendingSlots()).isNull();
        assertThat(transferRepository.findAll()).isEmpty();
    }

    // ---------------------------------------------------------------- 7

    @Test
    @DisplayName("7. 중위험 이체는 완료되고 보호자 알림이 남는다")
    void 시나리오7_MEDIUM_이체와_보호자_알림() throws Exception {
        linkGuardian();
        stubUtterance(transferUtterance(200_000L, "엄마"));

        final Long sessionId = startSession();
        awaitConfirmation(sessionId);

        stubUtterance(confirmUtterance());
        mockMvc.perform(voiceCommand(sessionId, UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.riskLevel").value("MEDIUM"));

        assertThat(notificationRepository.findAll()).isNotEmpty();
    }

    // ---------------------------------------------------------------- 8

    @Test
    @DisplayName("8. 고위험 이체는 실행되지 않고 차단되며 보호자 알림이 남는다")
    void 시나리오8_HIGH_차단과_보호자_알림() throws Exception {
        linkGuardian();
        fundPrimaryAccount(1_500_000L);
        stubUtterance(transferUtterance(700_000L, "엄마"));

        final Long sessionId = startSession();
        awaitConfirmation(sessionId);

        stubUtterance(confirmUtterance());
        mockMvc.perform(voiceCommand(sessionId, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FDS_4031"));

        assertThat(transferRepository.findAll()).singleElement()
                .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TransferStatus.BLOCKED));
        assertThat(notificationRepository.findAll()).isNotEmpty();
        assertThat(currentBalance()).isEqualTo(1_500_000L);
    }

    // ------------------------------------------------------------- 8-1

    /**
     * 등록 → 감지 → 발송이 한 줄로 이어지는지 본다.
     *
     * <p>구간별로는 다 통과하는데 이어 붙이면 끊기는 일이 실제로 있었다. 알림 코드는 다 있는데
     * 보호자를 등록할 방법이 없어서, 조회 결과가 늘 비어 문자가 한 통도 나가지 않았다.
     *
     * <p>그래서 "알림이 남았다"까지만 보지 않고, <b>SMS 게이트웨이에 실제로 전달된 수신번호가
     * 등록한 번호와 같은지</b>까지 확인한다. 운영에서는 이 자리에 솔라피 구현이 붙으므로,
     * 여기까지 도달한 번호가 곧 문자를 받는 번호다.
     */
    @Test
    @DisplayName("8-1. 등록한 보호자 번호로 경고 문자가 나간다")
    void 시나리오8_1_등록한_보호자_번호로_경고_문자가_나간다() throws Exception {
        // given — 로그인한 사용자가 보호자 번호를 등록한다 (실제 API)
        linkGuardian();
        fundPrimaryAccount(1_500_000L);
        stubUtterance(transferUtterance(700_000L, "엄마"));

        // when — 고위험 이체가 감지된다
        final Long sessionId = startSession();
        awaitConfirmation(sessionId);
        stubUtterance(confirmUtterance());
        mockMvc.perform(voiceCommand(sessionId, UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());

        // then — SMS 게이트웨이가 등록한 그 번호로 호출된다
        final ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        then(smsNotificationSender).should().send(
                any(Long.class), phoneCaptor.capture(), any(String.class), messageCaptor.capture());

        // 게이트웨이에는 암호문이 전달된다. 복호화해야 등록 번호와 대조할 수 있다
        assertThat(sensitiveDataCrypto.decrypt(phoneCaptor.getValue()))
                .isEqualTo(GUARDIAN_PHONE);
        assertThat(messageCaptor.getValue()).isNotBlank();
        // 문자에 계좌번호·전화번호가 섞여 나가면 잠금화면에 그대로 노출된다
        assertThat(messageCaptor.getValue()).doesNotContain(GUARDIAN_PHONE);
    }

    // ---------------------------------------------------------------- 9

    @Test
    @DisplayName("9. FDS 평가가 실패하면 이체를 실행하지 않는다")
    void 시나리오9_FDS_장애_시_이체_미실행() throws Exception {
        stubUtterance(transferUtterance(50_000L, "엄마"));
        final Long sessionId = startSession();
        awaitConfirmation(sessionId);

        stubUtterance(confirmUtterance());
        breakFdsClient();
        mockMvc.perform(voiceCommand(sessionId, UUID.randomUUID().toString()))
                .andExpect(status().is5xxServerError());

        assertThat(currentBalance()).isEqualTo(PRIMARY_BALANCE);
        assertThat(transferRepository.findAll())
                .noneMatch(t -> t.getStatus() == TransferStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- 10

    @Test
    @DisplayName("10. 같은 멱등성 키로 동시에 요청해도 이체는 1건만 생성된다")
    void 시나리오10_멱등성_동시_요청() throws Exception {
        makeRecipientFamiliar();
        makeProfileEstablished();
        stubUtterance(transferUtterance(50_000L, "엄마"));
        final Long sessionId = startSession(trustDevice());
        awaitConfirmation(sessionId);
        stubUtterance(confirmUtterance());

        final String idempotencyKey = UUID.randomUUID().toString();
        final ConcurrentRequestResult result = requestConcurrently(
                () -> mockMvc.perform(voiceCommand(sessionId, idempotencyKey)).andReturn()
        );

        result.assertOnlyExpectedStatuses();
        assertThat(transferRepository.findAll()).hasSize(1);
        assertThat(currentBalance()).isEqualTo(PRIMARY_BALANCE - 50_000L);
    }

    // ---------------------------------------------------------------- 11

    @Test
    @DisplayName("11. 다른 사용자의 세션과 계좌에는 접근할 수 없다")
    void 시나리오11_다른_사용자_접근_거부() throws Exception {
        stubUtterance(transferUtterance(50_000L, "엄마"));
        final Long sessionId = startSession();

        final User stranger = userRepository.save(User.builder()
                .name("남의사람")
                .phone(sensitiveDataCrypto.encrypt("01099998888"))
                .phoneHash(sensitiveDataCrypto.hash("01099998888"))
                .birthDate(LocalDate.of(1980, 1, 1))
                .userType(UserType.SENIOR)
                .build());

        mockMvc.perform(multipart("/api/voice/sessions/{id}/commands", sessionId)
                        .file(audioFile())
                        .header("X-Dev-User-Id", stranger.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/transactions")
                        .queryParam("accountId", account.getId().toString())
                        .header("X-Dev-User-Id", stranger.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_4040"));
    }

    // ---------------------------------------------------------------- 12

    @Test
    @DisplayName("12. 응답에 계좌번호·전화번호·토큰 원문이 노출되지 않는다")
    void 시나리오12_민감정보_미노출() throws Exception {
        makeRecipientFamiliar();
        makeProfileEstablished();
        stubUtterance(transferUtterance(50_000L, "엄마"));
        final Long sessionId = startSession();

        final String confirmBody = mockMvc.perform(voiceCommand(sessionId, null))
                .andReturn().getResponse().getContentAsString();
        final String balanceBody = mockMvc.perform(
                        get("/api/accounts/balance").header("X-Dev-User-Id", user.getId()))
                .andReturn().getResponse().getContentAsString();
        final String accountsBody = mockMvc.perform(
                        get("/api/accounts").header("X-Dev-User-Id", user.getId()))
                .andReturn().getResponse().getContentAsString();

        for (final String body : List.of(confirmBody, balanceBody, accountsBody)) {
            assertThat(body)
                    .doesNotContain("110123456789")
                    .doesNotContain("01012345678")
                    .doesNotContain("mock-access-token")
                    .doesNotContain("mock-refresh-token");
        }
    }

    // ---------------------------------------------------------------- helpers

    private Long startSession() throws Exception {
        return startSession(null);
    }

    private Long startSession(final String deviceUuid) throws Exception {
        final String body = mockMvc.perform(post("/api/voice/sessions")
                        .header("X-Dev-User-Id", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionStartBody(deviceUuid)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return voiceSessionRepository.findAll().stream()
                .map(VoiceSession::getId)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("세션 생성 실패: " + body));
    }

    private void awaitConfirmation(final Long sessionId) throws Exception {
        final MvcResult result = mockMvc.perform(voiceCommand(sessionId, null)).andReturn();
        final String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus())
                .withFailMessage("확인 대기 실패: %s", body)
                .isEqualTo(200);
        assertThat(body).contains("AWAITING_CONFIRMATION");
        final Matcher matcher = Pattern.compile("\"confirmationId\":\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find())
                .withFailMessage("확인 응답에 confirmationId 가 없습니다: %s", body)
                .isTrue();
        confirmationId = matcher.group(1);
    }

    private MockMultipartHttpServletRequestBuilder voiceCommand(
            final Long sessionId,
            final String idempotencyKey
    ) {
        final MockMultipartHttpServletRequestBuilder builder =
                multipart("/api/voice/sessions/{id}/commands", sessionId);
        builder.file(audioFile()).header("X-Dev-User-Id", user.getId());
        if (idempotencyKey != null && confirmationId != null) {
            builder.file(new MockMultipartFile(
                    "confirmationId", "", "text/plain",
                    confirmationId.getBytes(StandardCharsets.UTF_8)
            ));
        }
        if (idempotencyKey != null) {
            builder.file(new MockMultipartFile(
                    "idempotencyKey",
                    "",
                    "text/plain",
                    idempotencyKey.getBytes(StandardCharsets.UTF_8)
            ));
        }
        return builder;
    }

    private MockMultipartFile audioFile() {
        return new MockMultipartFile("audio", "voice.wav", "audio/wav", wavBytes());
    }

    /**
     * 1초짜리 최소 WAV. {@code AudioDurationValidator}가 헤더에서 재생 시간을 읽어내므로
     * 임의 바이트로는 업로드 단계에서 걸린다.
     */
    private byte[] wavBytes() {
        final int sampleRate = 8_000;
        final int byteRate = sampleRate;
        final int dataSize = byteRate;
        final ByteBuffer buffer = ByteBuffer
                .allocate(44 + dataSize)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) 1);
        buffer.putShort((short) 8);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(dataSize);
        buffer.put(new byte[dataSize]);
        return buffer.array();
    }

    private void stubUtterance(final VoiceAnalysisResponse response) {
        given(voiceAnalysisClient.analyze(any(VoiceAnalysisRequest.class)))
                .willAnswer(invocation -> {
                    final VoiceAnalysisRequest request = invocation.getArgument(0);
                    return VoiceAnalysisResponse.of(
                            request.requestId(),
                            request.voiceSessionId(),
                            response.transcript(),
                            response.sttConfidence(),
                            response.intent(),
                            response.intentConfidence(),
                            response.entities(),
                            response.entityConfidences(),
                            response.detectedMissingEntities(),
                            response.processingMs()
                    );
                });
    }

    private VoiceAnalysisResponse transferUtterance(final Long amount, final String recipientName) {
        final List<VoiceSlot> missing = new java.util.ArrayList<>();
        if (amount == null) {
            missing.add(VoiceSlot.AMOUNT);
        }
        if (recipientName == null) {
            missing.add(VoiceSlot.RECIPIENT);
        }
        return VoiceAnalysisResponse.of(
                "e2e", 0L, "엄마한테 보내줘", HIGH_CONFIDENCE,
                VoiceIntent.TRANSFER, HIGH_CONFIDENCE,
                VoiceEntities.transfer(amount, recipientName, null),
                VoiceEntityConfidences.transfer(
                        amount == null ? null : HIGH_CONFIDENCE,
                        recipientName == null ? null : HIGH_CONFIDENCE,
                        null
                ),
                List.copyOf(missing),
                100
        );
    }

    private VoiceAnalysisResponse confirmUtterance() {
        return simpleUtterance("응 맞아", VoiceIntent.CONFIRM);
    }

    private VoiceAnalysisResponse cancelUtterance() {
        return simpleUtterance("아니 취소할게", VoiceIntent.CANCEL);
    }

    private VoiceAnalysisResponse simpleUtterance(final String text, final VoiceIntent intent) {
        return VoiceAnalysisResponse.of(
                "e2e", 0L, text, HIGH_CONFIDENCE, intent, HIGH_CONFIDENCE,
                VoiceEntities.transfer(null, null, null),
                VoiceEntityConfidences.transfer(null, null, null),
                List.of(), 80
        );
    }

    /** 시나리오마다 기기 신뢰 상태가 이어지지 않도록 비운다. */
    private void deleteAllDevices() {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("delete from Device").executeUpdate());
    }

    private String sessionStartBody(final String deviceUuid) {
        if (deviceUuid == null) {
            return "{}";
        }
        return "{\"deviceUuid\":\"%s\"}".formatted(deviceUuid);
    }

    /**
     * 미등록 기기에서의 이체는 FDS가 위험 신호로 보아 MEDIUM이 된다.
     * LOW 시나리오에서는 신뢰 기기를 등록하고 그 식별자로 세션을 연다.
     *
     * <p>세션에 기기를 리플렉션으로 심지 않는 이유는, 실제 API 경로로 LOW가 나오는지를
     * 확인하는 것이 이 시나리오의 목적이기 때문이다. 기기 등록 경로가 없던 시절에는
     * 이 테스트만 통과하고 실서비스에서는 LOW가 나올 수 없었다.
     */
    private String trustDevice() {
        final String deviceUuid = UUID.randomUUID().toString();
        transactionTemplate.executeWithoutResult(status ->
                deviceRegistrationService.registerTrusted(
                        user.getId(),
                        deviceUuid,
                        "Galaxy E2E",
                        "Android 14"
                ));
        return deviceUuid;
    }

    /** 첫 거래 상대는 FDS가 MEDIUM으로 본다. LOW 시나리오에서는 거래 이력을 만들어 둔다. */
    private void makeRecipientFamiliar() {
        recipient.recordTransfer(LocalDateTime.now().minusDays(3));
        recipient.recordTransfer(LocalDateTime.now().minusDays(2));
        recipientRepository.save(recipient);
    }

    /** 30일 프로필이 비면 cold start 로 MEDIUM 이 된다. */
    private void makeProfileEstablished() {
        final UserTransferProfile profile = UserTransferProfile.builder().user(user).build();
        profile.refresh(50_000L, 80_000L, new BigDecimal("10000.00"), null, 12, 3);
        profileRepository.save(profile);
    }

    /**
     * 보호자 등록은 실제 API로 한다.
     *
     * <p>링크를 리포지토리에 직접 심으면 등록 경로가 끊겨도 알림 시나리오는 통과한다.
     * 실제로 한동안 등록 API가 없는 채로 알림 코드만 남아 있었고, 그때 이 테스트는 초록이었다.
     * 등록부터 문자 발송까지가 한 줄로 이어져 있는지 보려면 입구도 API로 지나야 한다.
     */
    private void linkGuardian() throws Exception {
        mockMvc.perform(post("/api/v1/guardian-links")
                        .header("X-Dev-User-Id", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "guardianName": "김보호",
                                  "guardianPhone": "%s",
                                  "relation": "자녀"
                                }
                                """.formatted(GUARDIAN_PHONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    /**
     * Mock 오픈뱅킹은 스프링 싱글턴이라 잔액이 테스트 간 이어진다.
     * 앞선 시나리오의 이체가 다음 시나리오의 기대값을 흔들지 않도록 되돌린다.
     */
    /** HIGH 판정은 70만원 이상이라 기본 잔액으로는 잔액 부족에 먼저 걸린다. */
    @SuppressWarnings("unchecked")
    private void fundPrimaryAccount(final long amount) {
        final Map<String, AtomicLong> balances = (Map<String, AtomicLong>)
                ReflectionTestUtils.getField(mockOpenBankingClient, "balances");
        balances.get(PRIMARY_FINTECH_NUM).set(amount);
    }

    @SuppressWarnings("unchecked")
    private void resetMockBalances() {
        final Map<String, AtomicLong> balances = (Map<String, AtomicLong>)
                ReflectionTestUtils.getField(mockOpenBankingClient, "balances");
        if (balances != null) {
            balances.get(PRIMARY_FINTECH_NUM).set(PRIMARY_BALANCE);
        }
    }

    private void expireSession(final Long sessionId) {
        final VoiceSession session = voiceSessionRepository.findById(sessionId).orElseThrow();
        ReflectionTestUtils.setField(session, "expiresAt", LocalDateTime.now().minusMinutes(1));
        voiceSessionRepository.save(session);
    }

    /** FDS 통신 자체를 실패시킨다. 평가가 없으면 이체가 나가면 안 된다. */
    private void breakFdsClient() {
        willThrow(new BusinessException(ErrorCode.ASSESSMENT_FAILED))
                .given(fdsAssessmentClient).assess(any());
    }

    private long currentBalance() throws Exception {
        final String body = mockMvc.perform(
                        get("/api/accounts/balance").header("X-Dev-User-Id", user.getId()))
                .andReturn().getResponse().getContentAsString();
        final java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"balanceAmount\":(\\d+)").matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("잔액을 읽지 못했습니다: " + body);
        }
        return Long.parseLong(matcher.group(1));
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
