package com.hafiz.disputeresolver.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveDefenceRequest(@NotBlank String defenceText) {
}
