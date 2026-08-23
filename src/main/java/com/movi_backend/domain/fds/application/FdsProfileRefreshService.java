package com.movi_backend.domain.fds.application;

import com.movi_backend.domain.fds.entity.UserTransferProfile;
import com.movi_backend.domain.fds.repository.UserTransferProfileRepository;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransferStatus;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FdsProfileRefreshService {

    private static final int PROFILE_WINDOW_DAYS = 30;
    private static final int COMMON_HOUR_LIMIT = 3;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final MathContext STANDARD_DEVIATION_CONTEXT =
            new MathContext(20, RoundingMode.HALF_UP);

    private final TransferRepository transferRepository;
    private final UserTransferProfileRepository profileRepository;

    @Transactional
    public void refresh() {
        refreshAt(LocalDateTime.now(BUSINESS_ZONE));
    }

    void refreshAt(final LocalDateTime endAt) {
        final LocalDateTime startAt = endAt.minusDays(PROFILE_WINDOW_DAYS);
        final List<Transfer> completedTransfers = transferRepository
                .findAllByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        TransferStatus.COMPLETED,
                        startAt,
                        endAt
                );
        final Map<Long, List<Transfer>> transfersByUser = completedTransfers.stream()
                .collect(Collectors.groupingBy(
                        transfer -> transfer.getUser().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        final Map<Long, UserTransferProfile> profilesByUser = profileRepository.findAll().stream()
                .collect(Collectors.toMap(
                        UserTransferProfile::getUserId,
                        Function.identity()
                ));
        final List<UserTransferProfile> refreshedProfiles = new ArrayList<>();

        profilesByUser.forEach((userId, profile) -> {
            if (!transfersByUser.containsKey(userId)) {
                profile.refresh(0L, 0L, BigDecimal.ZERO, "[]", 0, 0);
                refreshedProfiles.add(profile);
            }
        });
        transfersByUser.forEach((userId, transfers) -> {
            final UserTransferProfile profile = profilesByUser.getOrDefault(
                    userId,
                    UserTransferProfile.builder().user(transfers.getFirst().getUser()).build()
            );
            refreshProfile(profile, transfers);
            refreshedProfiles.add(profile);
        });

        if (!refreshedProfiles.isEmpty()) {
            profileRepository.saveAll(refreshedProfiles);
        }
    }

    private void refreshProfile(
            final UserTransferProfile profile,
            final List<Transfer> transfers
    ) {
        final List<Long> amounts = transfers.stream().map(Transfer::getAmount).toList();
        final long averageAmount = average(amounts);
        final long maximumAmount = amounts.stream().mapToLong(Long::longValue).max().orElse(0L);
        profile.refresh(
                averageAmount,
                maximumAmount,
                standardDeviation(amounts),
                commonHours(transfers).toString(),
                transfers.size(),
                distinctRecipientCount(transfers)
        );
    }

    private long average(final List<Long> amounts) {
        final BigDecimal sum = amounts.stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(amounts.size()), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private BigDecimal standardDeviation(final List<Long> amounts) {
        final BigDecimal count = BigDecimal.valueOf(amounts.size());
        final BigDecimal mean = amounts.stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(count, STANDARD_DEVIATION_CONTEXT);
        final BigDecimal variance = amounts.stream()
                .map(BigDecimal::valueOf)
                .map(amount -> amount.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(count, STANDARD_DEVIATION_CONTEXT);
        return variance.sqrt(STANDARD_DEVIATION_CONTEXT).setScale(2, RoundingMode.HALF_UP);
    }

    private List<Integer> commonHours(final List<Transfer> transfers) {
        final Map<Integer, Long> frequencies = transfers.stream()
                .collect(Collectors.groupingBy(
                        transfer -> transfer.getCompletedAt().getHour(),
                        Collectors.counting()
                ));
        return frequencies.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(COMMON_HOUR_LIMIT)
                .map(Map.Entry::getKey)
                .toList();
    }

    private int distinctRecipientCount(final List<Transfer> transfers) {
        return Math.toIntExact(transfers.stream()
                .map(transfer -> transfer.getToBankCode() + ":" + transfer.getToAccountNum())
                .distinct()
                .count());
    }
}
