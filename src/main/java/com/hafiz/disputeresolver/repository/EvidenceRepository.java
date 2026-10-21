package com.hafiz.disputeresolver.repository;

import java.util.List;
import java.util.UUID;

import com.hafiz.disputeresolver.model.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    List<Evidence> findByDisputeIdOrderByUploadedAtAsc(UUID disputeId);
}
