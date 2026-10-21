package com.hafiz.disputeresolver.moniepoint;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

// Read-side only - distinct from MoniepointPurchaseEventRepository, which owns
// the write-path idempotent upsert and is left untouched.
public interface MoniepointPurchaseEventQueryRepository extends JpaRepository<MoniepointPurchaseEventRecord, UUID> {

    List<MoniepointPurchaseEventRecord> findByBusinessIdAndTransactionReferenceOrderByReceivedAtAsc(
            long businessId, String transactionReference);
}
