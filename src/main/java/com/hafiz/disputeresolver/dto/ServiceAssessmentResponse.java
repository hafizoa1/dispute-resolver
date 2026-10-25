package com.hafiz.disputeresolver.dto;

import com.hafiz.disputeresolver.assessment.ServiceNotReceivedAssessment;

public record ServiceAssessmentResponse(ReviewResponse review,
        ServiceNotReceivedAssessment.Result assessment) { }
