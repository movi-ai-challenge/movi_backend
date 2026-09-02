package com.movi_backend.global.seed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.movi_backend.domain.auth.entity.User;
import com.movi_backend.domain.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 시드가 실패해도 서비스가 뜨는지 본다.
 *
 * <p>운영에서 시더가 UNIQUE 제약에 걸려 {@link org.springframework.boot.ApplicationRunner}
 * 예외를 던졌고, Spring Boot 가 컨텍스트를 닫아 프로세스가 종료됐다. 컨테이너 재시작 정책과
 * 맞물려 971번 재기동하며 502 를 냈다.
 *
 * <p>같은 상황을 다루는 {@code DemoDataSeederIntegrationTest} 의 시나리오는 <b>가드가
 * 건너뛰어 예외가 나지 않는다</b>. 그래서 예외를 삼키는 처리가 사라져도 그 테스트는 통과한다.
 * 여기서는 저장 자체를 실패시켜 <b>예외가 실제로 발생하는 경로</b>를 고정한다.
 *
 * <p>실패 원인은 계정 중복만이 아니다. 시드는 사용자·계좌·기기·거래를 차례로 만들고 각각
 * 제약이 걸려 있어, 가드로 전부 막을 수 없다. 막지 못한 실패가 기동을 무너뜨리지 않아야 한다.
 */
@SpringBootTest(properties = {
        "movi.jwt.secret=test-jwt-signing-key-must-be-at-least-32-bytes",
        "movi.crypto.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "movi.crypto.hash-key=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "movi.seed.enabled=true"
})
@ActiveProfiles("test")
class DemoDataSeederFailureTest {

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("시드 저장이 실패해도 예외가 밖으로 나가지 않는다")
    void 시드_실패가_기동을_무너뜨리지_않는다() {
        // given — 가드는 통과시키고 저장에서 제약 위반이 나게 한다.
        given(userRepository.findByPhoneHash(any())).willReturn(Optional.empty());
        willThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_users_phone_hash'"))
                .given(userRepository).save(any(User.class));

        // when & then — 예외가 나가면 운영에서 컨텍스트가 닫히고 프로세스가 죽는다
        assertThatCode(() -> demoDataSeeder.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("예상하지 못한 예외가 나도 기동을 막지 않는다")
    void 예상하지_못한_예외도_삼킨다() {
        // given — 제약 위반만 막으면 다음 실패에서 같은 장애가 되풀이된다.
        given(userRepository.findByPhoneHash(any())).willReturn(Optional.empty());
        willThrow(new IllegalStateException("설정이 잘못됐다"))
                .given(userRepository).save(any(User.class));

        // when & then
        assertThatCode(() -> demoDataSeeder.run(null)).doesNotThrowAnyException();
    }
}
