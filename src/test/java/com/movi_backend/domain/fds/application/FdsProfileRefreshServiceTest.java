package com.movi_backend.domain.fds.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.fds.entity.UserTransferProfile;
import com.movi_backend.domain.fds.repository.UserTransferProfileRepository;
import com.movi_backend.domain.transfer.entity.Transfer;
import com.movi_backend.domain.transfer.repository.TransferRepository;
import com.movi_backend.domain.transfer.type.TransferStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FdsProfileRefreshServiceTest {

    @Mock private TransferRepository transferRepository;
    @Mock private UserTransferProfileRepository profileRepository;

    private FdsProfileRefreshService service;
    private List<UserTransferProfile> savedProfiles;

    @BeforeEach
    void setUp() {
        service = new FdsProfileRefreshService(transferRepository, profileRepository);
        savedProfiles = new ArrayList<>();
        given(profileRepository.saveAll(any())).willAnswer(invocation -> {
            final Iterable<UserTransferProfile> profiles = invocation.getArgument(0);
            profiles.forEach(savedProfiles::add);
            return savedProfiles;
        });
    }

    @Test
    @DisplayName("최근 30일 완료 이체를 사용자별 FDS 프로필로 집계한다")
    void 최근_30일_완료_이체를_사용자별_FDS_프로필로_집계한다() {
        final LocalDateTime endAt = LocalDateTime.of(2026, 8, 24, 3, 0);
        final User user = user(3L);
        final List<Transfer> transfers = List.of(
                transfer(user, 10_000L, endAt.minusDays(1).withHour(9), "088", "account-a"),
                transfer(user, 20_000L, endAt.minusDays(2).withHour(9), "088", "account-a"),
                transfer(user, 30_000L, endAt.minusDays(3).withHour(18), "020", "account-b"),
                transfer(user, 50_000L, endAt.minusDays(4).withHour(12), "004", "account-c")
        );
        given(transferRepository
                .findAllByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        TransferStatus.COMPLETED,
                        endAt.minusDays(30),
                        endAt
                )).willReturn(transfers);
        given(profileRepository.findAll()).willReturn(List.of());

        service.refreshAt(endAt);

        assertThat(savedProfiles).hasSize(1);
        final UserTransferProfile profile = savedProfiles.getFirst();
        assertThat(profile.getAvgAmount()).isEqualTo(27_500L);
        assertThat(profile.getMaxAmount()).isEqualTo(50_000L);
        assertThat(profile.getStddevAmount()).isEqualByComparingTo("14790.20");
        assertThat(profile.getTransferCount30d()).isEqualTo(4);
        assertThat(profile.getDistinctRecipients30d()).isEqualTo(3);
        assertThat(profile.getCommonHours()).isEqualTo("[9, 12, 18]");
    }

    @Test
    @DisplayName("최근 30일 이체가 없는 기존 프로필은 초기 상태로 갱신한다")
    void 최근_30일_이체가_없는_기존_프로필은_초기_상태로_갱신한다() {
        final LocalDateTime endAt = LocalDateTime.of(2026, 8, 24, 3, 0);
        final UserTransferProfile staleProfile = UserTransferProfile.builder()
                .user(org.mockito.Mockito.mock(User.class))
                .build();
        ReflectionTestUtils.setField(staleProfile, "userId", 7L);
        staleProfile.refresh(30_000L, 50_000L, new BigDecimal("10000.00"), "[9]", 5, 2);
        given(transferRepository
                .findAllByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                        TransferStatus.COMPLETED,
                        endAt.minusDays(30),
                        endAt
                )).willReturn(List.of());
        given(profileRepository.findAll()).willReturn(List.of(staleProfile));

        service.refreshAt(endAt);

        assertThat(savedProfiles).containsExactly(staleProfile);
        assertThat(staleProfile.getAvgAmount()).isZero();
        assertThat(staleProfile.getMaxAmount()).isZero();
        assertThat(staleProfile.getStddevAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(staleProfile.getTransferCount30d()).isZero();
        assertThat(staleProfile.getDistinctRecipients30d()).isZero();
        assertThat(staleProfile.getCommonHours()).isEqualTo("[]");
    }

    private User user(final Long userId) {
        final User user = org.mockito.Mockito.mock(User.class);
        given(user.getId()).willReturn(userId);
        return user;
    }

    private Transfer transfer(
            final User user,
            final long amount,
            final LocalDateTime completedAt,
            final String bankCode,
            final String accountNumber
    ) {
        final Transfer transfer = org.mockito.Mockito.mock(Transfer.class);
        given(transfer.getUser()).willReturn(user);
        given(transfer.getAmount()).willReturn(amount);
        given(transfer.getCompletedAt()).willReturn(completedAt);
        given(transfer.getToBankCode()).willReturn(bankCode);
        given(transfer.getToAccountNum()).willReturn(accountNumber);
        return transfer;
    }
}
