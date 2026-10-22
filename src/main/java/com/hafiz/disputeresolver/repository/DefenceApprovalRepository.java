package com.hafiz.disputeresolver.repository;

import java.util.UUID;

import com.hafiz.disputeresolver.model.DefenceApproval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefenceApprovalRepository extends JpaRepository<DefenceApproval, UUID> {
}
