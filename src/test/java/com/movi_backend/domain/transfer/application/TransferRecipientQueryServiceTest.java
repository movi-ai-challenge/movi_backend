package com.movi_backend.domain.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.movi_backend.domain.transfer.dto.response.RecipientListResponse;
import com.movi_backend.domain.transfer.entity.TransferRecipient;
import com.movi_backend.domain.transfer.repository.TransferRecipientRepository;
import com.movi_backend.global.security.SensitiveDataCrypto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferRecipientQueryServiceTest {

    private static final Long USER_ID = 3L;

    @Mock
    private TransferRecipientRepository transferRecipientRepository;

    @Mock
    private SensitiveDataCrypto sensitiveDataCrypto;

    @Mock
    private TransferRecipient recipient;

    @InjectMocks
    private TransferRecipientQueryService transferRecipientQueryService;

    @Test
    @DisplayName("수취인 목록의 계좌번호는 뒤 네 자리만 남긴다")
    void 수취인_목록의_계좌번호는_뒤_네_자리만_남긴다() {
        // given
        given(transferRecipientRepository.findAllByUserIdAndAddressBookTrueOrderByNicknameAsc(USER_ID))
                .willReturn(List.of(recipient));
        given(recipient.getId()).willReturn(8L);
        given(recipient.getNickname()).willReturn("엄마");
        given(recipient.getHolderName()).willReturn("김영희");
        given(recipient.getBankCode()).willReturn("088");
        given(recipient.getAccountNum()).willReturn("encrypted");
        given(sensitiveDataCrypto.decrypt("encrypted")).willReturn("110123456789");

        // when
        final RecipientListResponse response = transferRecipientQueryService.findAll(USER_ID);

        // then
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.recipients().getFirst().maskedAccountNumber()).isEqualTo("***6789");
        assertThat(response.recipients().getFirst().nickname()).isEqualTo("엄마");
        assertThat(response.toVoiceMessage()).isEqualTo("저장된 받는 분이 1명 있어요.");
    }

    @Test
    @DisplayName("계좌번호를 복호화하지 못해도 목록은 보여 주고 계좌번호만 가린다")
    void 계좌번호를_복호화하지_못해도_목록은_보여_주고_계좌번호만_가린다() {
        // given
        given(transferRecipientRepository.findAllByUserIdAndAddressBookTrueOrderByNicknameAsc(USER_ID))
                .willReturn(List.of(recipient));
        given(recipient.getAccountNum()).willReturn("broken");
        willThrow(new IllegalStateException("복호화 실패"))
                .given(sensitiveDataCrypto).decrypt("broken");

        // when
        final RecipientListResponse response = transferRecipientQueryService.findAll(USER_ID);

        // then
        assertThat(response.recipients().getFirst().maskedAccountNumber()).isEqualTo("***");
    }

    @Test
    @DisplayName("저장된 수취인이 없으면 빈 목록과 안내 문구를 돌려준다")
    void 저장된_수취인이_없으면_빈_목록과_안내_문구를_돌려준다() {
        // given
        given(transferRecipientRepository.findAllByUserIdAndAddressBookTrueOrderByNicknameAsc(USER_ID))
                .willReturn(List.of());

        // when
        final RecipientListResponse response = transferRecipientQueryService.findAll(USER_ID);

        // then
        assertThat(response.totalCount()).isZero();
        assertThat(response.toVoiceMessage()).isEqualTo("저장된 받는 분이 없어요.");
    }
}
