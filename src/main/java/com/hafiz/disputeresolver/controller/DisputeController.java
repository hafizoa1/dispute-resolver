package com.hafiz.disputeresolver.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.hafiz.disputeresolver.dto.CreateDisputeRequest;
import com.hafiz.disputeresolver.dto.DisputeResponse;
import com.hafiz.disputeresolver.dto.UpdateDisputeRequest;
import com.hafiz.disputeresolver.model.DisputeStatus;
import com.hafiz.disputeresolver.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping
    public ResponseEntity<DisputeResponse> create(@Valid @RequestBody CreateDisputeRequest request) {
        DisputeResponse dispute = disputeService.create(request);
        return ResponseEntity.created(URI.create("/api/disputes/" + dispute.id())).body(dispute);
    }

    @GetMapping
    public List<DisputeResponse> list(@RequestParam(required = false) DisputeStatus status) {
        return disputeService.list(status);
    }

    @GetMapping("/{id}")
    public DisputeResponse getById(@PathVariable UUID id) {
        return disputeService.getById(id);
    }

    @PatchMapping("/{id}")
    public DisputeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDisputeRequest request) {
        return disputeService.update(id, request);
    }
}
