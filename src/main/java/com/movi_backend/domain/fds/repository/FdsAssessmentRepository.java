package com.movi_backend.domain.fds.repository;

import com.movi_backend.domain.fds.entity.FdsAssessment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FdsAssessmentRepository extends JpaRepository<FdsAssessment, Long> {

    Optional<FdsAssessment> findByTransferId(Long transferId);
}
