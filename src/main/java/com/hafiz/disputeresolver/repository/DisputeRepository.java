package com.hafiz.disputeresolver.repository;

import java.util.List;
import java.util.UUID;

import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.model.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    List<Dispute> findAllByOrderByCreatedAtDesc();

    List<Dispute> findByStatusOrderByCreatedAtDesc(DisputeStatus status);
}
