package com.hafiz.disputeresolver.controller;

import java.util.UUID;
import com.hafiz.disputeresolver.dto.ServiceAssessmentResponse;
import com.hafiz.disputeresolver.assessment.ServiceNotReceivedAssessment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.hafiz.disputeresolver.dto.ReviewResponse;
import com.hafiz.disputeresolver.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/disputes/{id}/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/service-not-received-assessment")
    public ServiceAssessmentResponse assess(@PathVariable UUID id,
            @RequestBody ServiceNotReceivedAssessment.Request request) {
        try {
            return reviewService.assessServiceNotReceived(id, request);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping
    public ReviewResponse getReview(@PathVariable UUID id) {
        return reviewService.getReview(id);
    }
}
