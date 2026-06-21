package com.chandan.randomchat.repository;

import com.chandan.randomchat.model.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    /**
     * IDEMPOTENCY CHECK — called before processing any purchase.
     * If token exists → return DUPLICATE, do NOT credit again.
     * Uses uq_purchases_token unique index.
     */
    Optional<Purchase> findByPurchaseToken(String purchaseToken);

    /** User's purchase history. */
    List<Purchase> findByUserIdOrderByPurchasedAtDesc(UUID userId);
}