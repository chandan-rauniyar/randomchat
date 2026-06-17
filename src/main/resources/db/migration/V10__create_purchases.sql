-- =============================================================================
-- V10__create_purchases.sql
-- Purchases — In-App Purchase history from Google Play.
--
-- Relationships:
--   Many-to-One with users: purchases.user_id → users.id
--   One-to-One with coin_transactions: purchases.coin_transaction_id → coin_transactions.id
--
-- FK behavior:
--   user_id: ON DELETE SET NULL — purchase history kept for admin/refund audit
--   coin_transaction_id: ON DELETE SET NULL — keeps purchase even if tx cleaned up
--
-- Flow:
--   1. Android completes purchase via Google Play Billing
--   2. Android sends purchase_token to backend POST /api/wallet/purchase
--   3. Backend verifies token with Google Play Developer API
--   4. On valid: INSERT purchases row + INSERT coin_transactions + UPDATE users.coin_balance
--      All in one DB transaction.
--   5. Backend acknowledges purchase to Google Play (must happen within 3 days)
--
-- The purchase_token UNIQUE constraint prevents double-crediting the same purchase
-- if the Android app retries (network error on response).
-- =============================================================================

CREATE TYPE purchase_status AS ENUM (
    'PENDING',          -- received from Android, verification in progress
    'VERIFIED',         -- Google Play confirmed — coins credited
    'FAILED',           -- verification failed — no coins credited
    'REFUNDED',         -- Google Play refunded — coins deducted
    'DUPLICATE'         -- purchase_token already exists — ignored
);

CREATE TABLE purchases (
    -- -------------------------------------------------------------------------
    -- Identity
    -- -------------------------------------------------------------------------
                           id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
                           app_id               VARCHAR(50)   NOT NULL,
                           user_id              UUID,                               -- NULL if user hard-deleted

    -- -------------------------------------------------------------------------
    -- Google Play data
    -- -------------------------------------------------------------------------
                           product_id           VARCHAR(100)  NOT NULL,            -- Play Store product ID e.g. "coins_100"
                           purchase_token       VARCHAR(500)  NOT NULL,            -- unique token from Google Play
                           order_id             VARCHAR(200),                      -- Google Play order ID (GPA.xxxx)
                           package_name         VARCHAR(200),                      -- Android package name (for GP verification)

    -- -------------------------------------------------------------------------
    -- What was granted
    -- -------------------------------------------------------------------------
                           coins_granted        INTEGER       NOT NULL DEFAULT 0   CHECK (coins_granted >= 0),
                           amount_usd           DECIMAL(10,2) NOT NULL DEFAULT 0   CHECK (amount_usd >= 0),

    -- -------------------------------------------------------------------------
    -- Verification
    -- -------------------------------------------------------------------------
                           status               purchase_status NOT NULL DEFAULT 'PENDING',
                           verified_at          TIMESTAMP WITH TIME ZONE,
                           failure_reason       TEXT,                              -- if status = FAILED

    -- -------------------------------------------------------------------------
    -- Link to wallet transaction
    -- -------------------------------------------------------------------------
                           coin_transaction_id  UUID,

    -- -------------------------------------------------------------------------
    -- Audit
    -- -------------------------------------------------------------------------
                           purchased_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                           created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- -------------------------------------------------------------------------
    -- Constraints
    -- -------------------------------------------------------------------------
                           CONSTRAINT pk_purchases
                               PRIMARY KEY (id),

                           CONSTRAINT fk_purchases_user
                               FOREIGN KEY (user_id)
                                   REFERENCES users (id)
                                   ON DELETE SET NULL,

                           CONSTRAINT fk_purchases_coin_transaction
                               FOREIGN KEY (coin_transaction_id)
                                   REFERENCES coin_transactions (id)
                                   ON DELETE SET NULL,

    -- THE most important constraint: no double-crediting
                           CONSTRAINT uq_purchases_token
                               UNIQUE (purchase_token),

    -- verified_at must be set for VERIFIED purchases
                           CONSTRAINT chk_purchases_verified_at
                               CHECK (
                                   status NOT IN ('VERIFIED', 'REFUNDED')
                                       OR verified_at IS NOT NULL
                                   )
);

-- =============================================================================
-- Indexes
-- =============================================================================

-- Deduplication check: backend checks this before crediting any purchase
CREATE UNIQUE INDEX idx_purchases_token
    ON purchases (purchase_token);                          -- already unique, explicit index for clarity

-- Admin: purchase history per user
CREATE INDEX idx_purchases_user_date
    ON purchases (user_id, purchased_at DESC)
    WHERE user_id IS NOT NULL;

-- Admin: all purchases per app (revenue dashboard)
CREATE INDEX idx_purchases_app_date
    ON purchases (app_id, purchased_at DESC);

-- Admin: pending verifications (monitoring job)
CREATE INDEX idx_purchases_pending
    ON purchases (app_id, created_at ASC)
    WHERE status = 'PENDING';

-- Admin: revenue by product
CREATE INDEX idx_purchases_product
    ON purchases (app_id, product_id, purchased_at DESC)
    WHERE status = 'VERIFIED';

-- =============================================================================
-- Comments
-- =============================================================================
COMMENT ON TABLE  purchases                      IS 'In-App Purchase history from Google Play. Immutable after verification. Kept for admin audit even after user deletion.';
COMMENT ON COLUMN purchases.user_id              IS 'FK to users.id SET NULL on delete. Purchase record kept for revenue audit.';
COMMENT ON COLUMN purchases.purchase_token       IS 'Unique token from Google Play. UNIQUE constraint prevents double-crediting on Android retry.';
COMMENT ON COLUMN purchases.order_id             IS 'Google Play order ID (GPA.xxxx). Used for refund tracking and support queries.';
COMMENT ON COLUMN purchases.status               IS 'PENDING until Google Play API verifies. DUPLICATE if token already processed. REFUNDED if Google issued refund.';
COMMENT ON COLUMN purchases.coin_transaction_id  IS 'FK to the coin_transactions row that credited coins. NULL for FAILED or PENDING purchases.';