-- =============================================================================
-- V8__create_coin_transactions.sql
-- Coin Transactions — MAIN ADMIN HISTORY TABLE.
-- Complete audit trail of every coin and credit movement.
--
-- Relationships:
--   Many-to-One with users: coin_transactions.user_id → users.id
--   Soft reference to sessions: reference_id can hold session UUID
--
-- FK behavior:
--   ON DELETE SET NULL — if user deleted, transaction history stays for admin.
--   Never delete transaction rows — this is the financial audit log.
--
-- Design:
--   Every field that can change (coin_balance, match_credits, filter_credits)
--   has a "before" value and "after" snapshot stored in every row.
--   This means admin can reconstruct the exact wallet state at any point in time
--   by reading any single row — no need to replay history.
--
--   "amount" and "credits_delta_*" use signed integers:
--     Positive = credit (coins/credits added)
--     Negative = debit  (coins/credits removed)
-- =============================================================================

CREATE TYPE transaction_type AS ENUM (
    -- Coin inflows
    'SIGNUP_BONUS',             -- new user: +10 coins
    'AD_REWARD',                -- watched ad: +3 coins
    'AD_REWARD_DAILY_BONUS',    -- first ad of day: +5 coins
    'PURCHASE',                 -- IAP coin pack: +N coins
    'ADMIN_GRANT',              -- admin manually gave coins

    -- Coin outflows
    'ADMIN_DEDUCT',             -- admin manually removed coins

    -- Credit purchases (coins → credits, same transaction debits coins + adds credits)
    'MATCH_CREDIT_PURCHASE',         -- -10 coins, +20 match_credits
    'GENDER_FILTER_PURCHASE',        -- -10 coins, +10 filter_credits_gender
    'COUNTRY_FILTER_PURCHASE',       -- -10 coins, +10 filter_credits_country
    'BUNDLE_FILTER_PURCHASE',        -- -18 coins, +10 gender + 10 country credits

    -- Credit consumption (no coin movement — credits go to 0 through use)
    'MATCH_DEDUCT',                  -- accepted match: -1 match_credits
    'GENDER_FILTER_DEDUCT',          -- accepted match with gender filter: -1 filter_credits_gender
    'COUNTRY_FILTER_DEDUCT'          -- accepted match with country filter: -1 filter_credits_country
);

CREATE TABLE coin_transactions (
    -- -------------------------------------------------------------------------
    -- Identity
    -- -------------------------------------------------------------------------
                                   id                    UUID             NOT NULL DEFAULT gen_random_uuid(),
                                   app_id                VARCHAR(50)      NOT NULL,
                                   user_id               UUID,                              -- NULL if user hard-deleted

    -- -------------------------------------------------------------------------
    -- Transaction classification
    -- -------------------------------------------------------------------------
                                   transaction_type      transaction_type NOT NULL,

    -- -------------------------------------------------------------------------
    -- Coin movement (signed integer: positive=credit, negative=debit)
    -- Zero for pure credit transactions (MATCH_DEDUCT etc.)
    -- -------------------------------------------------------------------------
                                   coin_amount           INTEGER          NOT NULL DEFAULT 0,
                                   coin_balance_before   INTEGER          NOT NULL,         -- snapshot BEFORE this transaction
                                   coin_balance_after    INTEGER          NOT NULL,         -- snapshot AFTER this transaction

    -- -------------------------------------------------------------------------
    -- Match credit movement (signed)
    -- -------------------------------------------------------------------------
                                   match_credits_delta   INTEGER          NOT NULL DEFAULT 0,
                                   match_credits_before  INTEGER          NOT NULL,
                                   match_credits_after   INTEGER          NOT NULL,

    -- -------------------------------------------------------------------------
    -- Gender filter credit movement (signed)
    -- -------------------------------------------------------------------------
                                   gender_filter_delta   INTEGER          NOT NULL DEFAULT 0,
                                   gender_filter_before  INTEGER          NOT NULL,
                                   gender_filter_after   INTEGER          NOT NULL,

    -- -------------------------------------------------------------------------
    -- Country filter credit movement (signed)
    -- -------------------------------------------------------------------------
                                   country_filter_delta  INTEGER          NOT NULL DEFAULT 0,
                                   country_filter_before INTEGER          NOT NULL,
                                   country_filter_after  INTEGER          NOT NULL,

    -- -------------------------------------------------------------------------
    -- Reference (what caused this transaction)
    -- Can be: ad_view UUID, purchase token, session UUID, admin username, etc.
    -- -------------------------------------------------------------------------
                                   reference_id          VARCHAR(500),
                                   reference_type        VARCHAR(50),                       -- 'SESSION', 'AD_VIEW', 'PURCHASE', 'ADMIN'
                                   notes                 TEXT,                              -- admin notes if ADMIN_GRANT/DEDUCT

    -- -------------------------------------------------------------------------
    -- Audit
    -- -------------------------------------------------------------------------
                                   created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- -------------------------------------------------------------------------
    -- Constraints
    -- -------------------------------------------------------------------------
                                   CONSTRAINT pk_coin_transactions
                                       PRIMARY KEY (id),

                                   CONSTRAINT fk_coin_transactions_user
                                       FOREIGN KEY (user_id)
                                           REFERENCES users (id)
                                           ON DELETE SET NULL,

    -- Balance cannot go negative (sanity check — enforced in application too)
                                   CONSTRAINT chk_txn_coin_balance_after
                                       CHECK (coin_balance_after >= 0),

                                   CONSTRAINT chk_txn_match_credits_after
                                       CHECK (match_credits_after >= 0),

                                   CONSTRAINT chk_txn_gender_filter_after
                                       CHECK (gender_filter_after >= 0),

                                   CONSTRAINT chk_txn_country_filter_after
                                       CHECK (country_filter_after >= 0),

    -- Before + delta must equal after (arithmetic consistency)
                                   CONSTRAINT chk_txn_coin_arithmetic
                                       CHECK (coin_balance_before + coin_amount = coin_balance_after),

                                   CONSTRAINT chk_txn_match_arithmetic
                                       CHECK (match_credits_before + match_credits_delta = match_credits_after),

                                   CONSTRAINT chk_txn_gender_arithmetic
                                       CHECK (gender_filter_before + gender_filter_delta = gender_filter_after),

                                   CONSTRAINT chk_txn_country_arithmetic
                                       CHECK (country_filter_before + country_filter_delta = country_filter_after)
);

-- =============================================================================
-- Indexes
-- =============================================================================

-- User's own transaction history (most recent first)
CREATE INDEX idx_txn_user_date
    ON coin_transactions (user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

-- Admin: economy report by type and date
CREATE INDEX idx_txn_app_type_date
    ON coin_transactions (app_id, transaction_type, created_at DESC);

-- Admin: daily revenue/coin report
CREATE INDEX idx_txn_app_date
    ON coin_transactions (app_id, created_at DESC);

-- Admin: find all transactions linked to a session
CREATE INDEX idx_txn_reference
    ON coin_transactions (reference_id, reference_type)
    WHERE reference_id IS NOT NULL;

-- Admin: find credit purchases (revenue tracking)
CREATE INDEX idx_txn_purchases
    ON coin_transactions (app_id, transaction_type, created_at DESC)
    WHERE transaction_type IN (
        'MATCH_CREDIT_PURCHASE',
        'GENDER_FILTER_PURCHASE',
        'COUNTRY_FILTER_PURCHASE',
        'BUNDLE_FILTER_PURCHASE',
        'PURCHASE'
    );

-- =============================================================================
-- Comments
-- =============================================================================
COMMENT ON TABLE  coin_transactions                IS 'MAIN ADMIN HISTORY TABLE. Immutable audit log of every coin and credit change. Never update or delete rows.';
COMMENT ON COLUMN coin_transactions.user_id        IS 'FK to users.id SET NULL on hard delete. Transaction history preserved for admin even after user deletes account.';
COMMENT ON COLUMN coin_transactions.coin_amount    IS 'Signed: positive=credit (coins added), negative=debit (coins spent). Zero for credit-only transactions.';
COMMENT ON COLUMN coin_transactions.coin_balance_before IS 'Exact coin_balance snapshot BEFORE this transaction. Enables point-in-time wallet reconstruction.';
COMMENT ON COLUMN coin_transactions.reference_id   IS 'UUID or token identifying the cause. SESSION uuid for match deducts, ad view UUID for ad rewards, purchase token for IAP.';