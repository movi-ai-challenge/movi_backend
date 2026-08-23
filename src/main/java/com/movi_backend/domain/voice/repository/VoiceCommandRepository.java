package com.movi_backend.domain.voice.repository;

import com.movi_backend.domain.voice.entity.VoiceCommand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceCommandRepository extends JpaRepository<VoiceCommand, Long> {
}
