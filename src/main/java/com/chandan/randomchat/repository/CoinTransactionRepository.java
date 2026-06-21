package com.chandan.randomchat.repository;

import com.chandan.randomchat.model.CoinTransaction;
import com.chandan.randomchat.model.enums.TransactionType;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, UUID> {

    /** User's own transaction history — paginated, newest first. */
    Page<CoinTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Admin: economy report — transactions by type in date range. */
    @Query("""
        SELECT ct.transactionType, COUNT(ct), SUM(ct.coinAmount)
        FROM CoinTransaction ct
        WHERE ct.appId = :appId
          AND ct.createdAt >= :from
          AND ct.createdAt < :to
        GROUP BY ct.transactionType
        """)
    List<Object[]> getDailyEconomySummary(
            @Param("appId") String appId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
