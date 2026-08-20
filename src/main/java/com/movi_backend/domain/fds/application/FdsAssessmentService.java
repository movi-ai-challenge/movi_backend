package com.movi_backend.domain.fds.application;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.fds.dto.FdsEvaluationCommand;
import com.movi_backend.domain.fds.dto.request.FraudPredictRequest;
import com.movi_backend.domain.fds.dto.response.FraudPredictResponse;
import com.movi_backend.domain.fds.entity.FdsAssessment;
import com.movi_backend.domain.fds.entity.UserTransferProfile;
import com.movi_backend.domain.fds.infrastructure.FdsClient;
import com.movi_backend.domain.fds.repository.FdsAssessmentRepository;
import com.movi_backend.domain.fds.repository.UserTransferProfileRepository;
import com.movi_backend.domain.fds.validator.FdsResponseValidator;
import com.movi_backend.domain.transfer.entity.Transfer;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이체 1건의 위험도 평가와 결과 저장.
 *
 * <p>평가 결과는 이체 성사 여부를 가르는 근거이므로 <b>판정 직후에 반드시 저장한다.</b>
 * 저장하지 않으면 나중에 "왜 이 이체가 막혔는지" 설명할 수 없다.
 *
 * <p>평가에 실패하면 예외를 던진다. 호출부는 이 예외를 잡아 이체를 통과시켜서는 안 된다.
 */
@Service
@RequiredArgsConstructor
public class FdsAssessmentService {

    private static final String REQUEST_ID_PREFIX = "fds-transfer-";
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");
    private static final String NULL_LITERAL = "null";

    private final FdsClient fdsClient;
    private final FdsResponseValidator fdsResponseValidator;
    private final FdsAssessmentRepository fdsAssessmentRepository;
    private final UserTransferProfileRepository userTransferProfileRepository;
    private final EntityManager entityManager;

    /**
     * 위험도를 평가하고 결과를 저장한다.
     *
     * @throws com.movi_backend.global.error.BusinessException 평가 실패·응답 검증 실패
     */
    @Transactional
    public FdsAssessment evaluate(final FdsEvaluationCommand command) {
        final FraudPredictRequest request = toPredictRequest(command);
        final FraudPredictResponse response = fdsClient.predict(request);
        fdsResponseValidator.validate(request, response);

        final FdsAssessment assessment = FdsAssessment.builder()
                .transfer(entityManager.getReference(Transfer.class, command.transferId()))
                .user(entityManager.getReference(User.class, command.userId()))
                .modelVersion(response.modelVersion())
                .anomalyScore(response.anomalyScore())
                .riskLevel(response.riskLevel())
                .decision(response.decision())
                .features(toFeaturesJson(request, response))
                .latencyMs(response.latencyMs())
                .build();
        return fdsAssessmentRepository.save(assessment);
    }

    private FraudPredictRequest toPredictRequest(final FdsEvaluationCommand command) {
        return new FraudPredictRequest(
                buildRequestId(command.transferId()),
                command.transferId(),
                command.userId(),
                command.amount(),
                command.balanceBefore(),
                command.requestedAt(),
                toRecipientFeature(command.recipientTransferCount()),
                toProfileFeature(command.userId()),
                FraudPredictRequest.ContextFeature.of(command.trustedDevice(), command.sttConfidence())
        );
    }

    /** 이체당 한 번만 평가하지만, 재평가가 생겨도 요청을 구분할 수 있게 난수를 덧붙인다. */
    private String buildRequestId(final Long transferId) {
        return REQUEST_ID_PREFIX + transferId + "-" + UUID.randomUUID();
    }

    private FraudPredictRequest.RecipientFeature toRecipientFeature(final Integer transferCount) {
        if (transferCount == null) {
            return FraudPredictRequest.RecipientFeature.unknown();
        }
        return FraudPredictRequest.RecipientFeature.of(transferCount);
    }

    /**
     * 사용자 행동 프로필을 피처로 바꾼다.
     *
     * <p>프로필이 없거나 30일 이체 이력이 없으면 cold start다. 이때 평균·표준편차를 0으로 넘기면
     * 모델이 모든 금액을 극단적 이상치로 읽으므로 {@code null}로 비운다.
     */
    private FraudPredictRequest.ProfileFeature toProfileFeature(final Long userId) {
        final Optional<UserTransferProfile> profile = userTransferProfileRepository.findById(userId);
        if (profile.isEmpty() || profile.get().isColdStart()) {
            return FraudPredictRequest.ProfileFeature.emptyHistory();
        }
        final UserTransferProfile found = profile.get();
        return FraudPredictRequest.ProfileFeature.of(
                found.getAvgAmount(),
                found.getMaxAmount(),
                found.getStddevAmount(),
                found.getTransferCount30d(),
                found.getDistinctRecipients30d(),
                parseCommonHours(found.getCommonHours())
        );
    }

    /**
     * {@code [9,12,18]} 형태의 저장값에서 시간대를 뽑는다.
     *
     * <p>배치가 써 넣는 고정 형태라 숫자만 훑는 것으로 충분하다. 값이 깨져 있어도 평가 자체를
     * 막지 않는다. 시간대 피처 하나 때문에 이체가 멈추는 편이 더 나쁘다.
     */
    private List<Integer> parseCommonHours(final String commonHoursJson) {
        if (commonHoursJson == null || commonHoursJson.isBlank()) {
            return List.of();
        }
        final List<Integer> hours = new ArrayList<>();
        final Matcher matcher = INTEGER_PATTERN.matcher(commonHoursJson);
        while (matcher.find()) {
            hours.add(Integer.valueOf(matcher.group()));
        }
        return List.copyOf(hours);
    }

    /**
     * 모델 입력 스냅샷을 남긴다. 모델을 교체한 뒤 과거 거래를 재평가할 때 필요하다.
     *
     * <p>여기 담기는 값은 금액·시각·통계뿐이다. 계좌번호·전화번호·수취인 이름은 애초에 FDS 요청에
     * 포함하지 않으므로 스냅샷에도 들어갈 일이 없다.
     */
    private String toFeaturesJson(
            final FraudPredictRequest request,
            final FraudPredictResponse response
    ) {
        final FraudPredictRequest.ProfileFeature profile = request.profile();
        final FraudPredictRequest.RecipientFeature recipient = request.recipient();
        return ("{\"transferId\":%d,\"amount\":%d,\"balanceBefore\":%d,\"requestedAt\":\"%s\","
                + "\"recipient\":{\"transferCount\":%d,\"firstTime\":%b},"
                + "\"profile\":{\"coldStart\":%b,\"averageAmount30d\":%s,\"maximumAmount30d\":%s,"
                + "\"stddevAmount30d\":%s,\"transferCount30d\":%d,\"distinctRecipients30d\":%d,"
                + "\"commonHours\":%s},"
                + "\"context\":{\"trustedDevice\":%b,\"sttConfidence\":%s},"
                + "\"policyVersion\":\"%s\",\"finalRiskScore\":%s,\"reasonCodes\":%s}").formatted(
                request.transferId(),
                request.amount(),
                request.balanceBefore(),
                request.requestedAt(),
                recipient.transferCount(),
                recipient.firstTime(),
                profile.coldStart(),
                numberOrNull(profile.averageAmount30d()),
                numberOrNull(profile.maximumAmount30d()),
                numberOrNull(profile.stddevAmount30d()),
                profile.transferCount30d(),
                profile.distinctRecipients30d(),
                toNumberArray(profile.commonHours()),
                request.context().trustedDevice(),
                request.context().sttConfidence(),
                escape(response.policyVersion()),
                numberOrNull(response.scores().finalRiskScore()),
                toStringArray(response.reasonCodes())
        );
    }

    private String numberOrNull(final Number value) {
        if (value == null) {
            return NULL_LITERAL;
        }
        return value.toString();
    }

    private String toNumberArray(final List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    /** reason code는 AI 파트가 정의한다. 모르는 값이 와도 스냅샷 JSON이 깨지지 않게 escape 한다. */
    private String toStringArray(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
                .map(value -> "\"" + escape(value) + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String escape(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
