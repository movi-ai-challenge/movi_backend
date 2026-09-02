package com.movi_backend.domain.fds.repository;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FdsAssessmentRepository extends JpaRepository<FdsAssessment, Long> {

    Optional<FdsAssessment> findByTransferId(Long transferId);

    /**
     * 거래내역 한 페이지의 평가를 한 번에 읽는다.
     *
     * <p>거래마다 따로 조회하면 목록 길이만큼 질의가 나간다(N+1). 화면은 20건씩
     * 보여 주므로 한 번에 가져온다.
     */
    List<FdsAssessment> findByTransferIdIn(Collection<Long> transferIds);
}
