package com.movi_backend.domain.voice.application.model;

import com.movi_backend.global.error.BusinessException;
import com.movi_backend.global.error.ErrorCode;
import java.time.LocalDate;

/**
 * 음성 거래내역 조회 기간.
 *
 * <p><b>백엔드는 자연어 기간을 직접 해석하지 않는다.</b> "이번 달", "지난주" 같은 표현을 날짜로
 * 바꾸는 일은 AI Voice API가 맡고, 백엔드는 넘어온 {@code startDate}/{@code endDate}를
 * 신뢰 경계 밖 입력으로 보고 재검증한다. AI가 기간을 놓쳐도 조회가 실패하지 않도록
 * 기본 기간으로 채운다 — 화면을 보지 않는 사용자에게 "기간을 다시 말해 달라"는 재질문은
 * 비용이 크기 때문이다.
 */
public record VoiceHistoryPeriod(LocalDate startDate, LocalDate endDate) {

    /** 기간을 못 받았을 때 훑어 볼 최근 일수 */
    public static final int DEFAULT_DAYS = 7;

    /**
     * AI가 추출한 기간을 검증해 조회 기간을 만든다.
     *
     * <p>한쪽만 채워져 오는 경우가 잦다. "8월 1일부터"는 종료일이 없고 "어제까지"는 시작일이
     * 없다. 빠진 쪽을 기본 규칙으로 채우고, 뒤집힌 기간과 미래 날짜만 거부한다.
     */
    public static VoiceHistoryPeriod resolve(
            final LocalDate startDate,
            final LocalDate endDate,
            final LocalDate today
    ) {
        validateNotFuture(startDate, today);
        validateNotFuture(endDate, today);

        if (startDate == null && endDate == null) {
            return new VoiceHistoryPeriod(today.minusDays(DEFAULT_DAYS - 1L), today);
        }
        if (endDate == null) {
            return new VoiceHistoryPeriod(startDate, today);
        }
        if (startDate == null) {
            return new VoiceHistoryPeriod(endDate.minusDays(DEFAULT_DAYS - 1L), endDate);
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.HISTORY_PERIOD_INVALID, "조회 기간 역전");
        }
        return new VoiceHistoryPeriod(startDate, endDate);
    }

    private static void validateNotFuture(final LocalDate date, final LocalDate today) {
        if (date == null) {
            return;
        }
        if (date.isAfter(today)) {
            throw new BusinessException(ErrorCode.HISTORY_PERIOD_INVALID, "미래 날짜 조회");
        }
    }

    /** 조회 기간을 음성으로 읽어 줄 문구. 오늘까지면 "…부터 오늘까지"로 짧게 읽는다. */
    public String toVoicePhrase(final LocalDate today) {
        if (this.endDate.isEqual(today)) {
            return "%s부터 오늘까지".formatted(formatDate(this.startDate));
        }
        return "%s부터 %s까지".formatted(formatDate(this.startDate), formatDate(this.endDate));
    }

    private static String formatDate(final LocalDate date) {
        return "%d월 %d일".formatted(date.getMonthValue(), date.getDayOfMonth());
    }
}
