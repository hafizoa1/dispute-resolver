package com.hafiz.disputeresolver.controller;

import java.util.UUID;

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

    @GetMapping
    public ReviewResponse getReview(@PathVariable UUID id) {
        return reviewService.getReview(id);
    }
}
