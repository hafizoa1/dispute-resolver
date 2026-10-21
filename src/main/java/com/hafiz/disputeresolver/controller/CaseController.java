package com.hafiz.disputeresolver.controller;

import java.util.UUID;

import com.hafiz.disputeresolver.dto.CaseResponse;
import com.hafiz.disputeresolver.service.CaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/disputes/{id}/case")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @GetMapping
    public CaseResponse getCase(@PathVariable UUID id) {
        return caseService.getCase(id);
    }
}
