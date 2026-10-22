package com.hafiz.disputeresolver.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record DefencePreviewRequest(@NotNull List<UUID> evidenceIds) {
}
