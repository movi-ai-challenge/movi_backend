package com.movi_backend.domain.voice.repository;

import com.movi_backend.domain.voice.entity.VoiceSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceSessionRepository extends JpaRepository<VoiceSession, Long> {
}
