package com.movi_backend.domain.transfer.application.model;

public sealed interface TransferValidationResult
        permits TransferClarification, ValidatedTransferCommand {
}
