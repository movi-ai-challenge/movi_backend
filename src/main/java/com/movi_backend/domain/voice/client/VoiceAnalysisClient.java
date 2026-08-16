package com.movi_backend.domain.voice.client;

import com.movi_backend.domain.voice.client.dto.VoiceAnalysisRequest;
import com.movi_backend.domain.voice.client.dto.VoiceAnalysisResponse;

public interface VoiceAnalysisClient {

    VoiceAnalysisResponse analyze(VoiceAnalysisRequest request);
}
