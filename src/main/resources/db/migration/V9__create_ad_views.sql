-- =============================================================================
-- V9__create_ad_views.sql
-- Ad Views — records every rewarded ad watched by every user.
-- ADMIN PRIORITY TABLE — queried heavily for revenue analytics.
--
-- Relationships:
--   Many-to-One with users: ad_views.user_id → users.id
--   One-to-One with coin_transactions: ad_views.coin_transaction_id → coin_transactions.id
--     (every verified ad view has a matching coin_transactions row)
--
-- FK behavior:
--   user_id: ON DELETE SET NULL — ad history kept for admin even if user deleted
--
-- The coin reward granted by an ad view is recorded BOTH here (for ad analytics)
-- AND in coin_transactions (for wallet audit). They are linked by coin_transaction_id.
--
-- Daily cap enforcement:
--   Before rewarding any ad, backend does:
--   SELECT COUNT(*) FROM ad_views WHERE user_id=? AND view_date=TODAY AND verified=TRUE
--   If >= ad_daily_cap (from app_config) → deny reward
--   The idx_adviews_daily_cap index makes this sub-millisecond.
-- =============================================================================

CREATE TYPE ad_type AS ENUM (
    'REWARDED',         -- user watches full ad, gets coins
    'INTERSTITIAL'      -- full-screen ad shown between screens (no reward)
);

CREATE TABLE ad_views (
    -- -------------------------------------------------------------------------
    -- Identity
    -- -------------------------------------------------------------------------
                          id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
                          app_id               VARCHAR(50)  NOT NULL,
                          user_id              UUID,                               -- NULL if user hard-deleted

    -- -------------------------------------------------------------------------
    -- Ad metadata
    -- -------------------------------------------------------------------------
                          ad_type              ad_type      NOT NULL DEFAULT 'REWARDED',
                          ad_unit_id           VARCHAR(255),                      -- AdMob ad unit ID
                          ad_network           VARCHAR(50)  NOT NULL DEFAULT 'ADMOB',

    -- -------------------------------------------------------------------------
    -- Reward
    -- -------------------------------------------------------------------------
                          coins_rewarded       INTEGER      NOT NULL DEFAULT 0    CHECK (coins_rewarded >= 0),
                          is_first_of_day      BOOLEAN      NOT NULL DEFAULT FALSE, -- TRUE = daily bonus applied

    -- -------------------------------------------------------------------------
    -- Verification
    -- -------------------------------------------------------------------------
                          verified             BOOLEAN      NOT NULL DEFAULT FALSE,
                          verification_token   VARCHAR(500),                      -- server-side verification token from AdMob
                          verified_at          TIMESTAMP WITH TIME ZONE,

    -- -------------------------------------------------------------------------
    -- Link to wallet transaction
    -- -------------------------------------------------------------------------
                          coin_transaction_id  UUID,                              -- FK to coin_transactions.id

    -- -------------------------------------------------------------------------
    -- Timing & Daily Cap Tracking
    -- -------------------------------------------------------------------------
                          view_date            DATE         NOT NULL DEFAULT CURRENT_DATE, -- for daily cap check
                          viewed_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- -------------------------------------------------------------------------
    -- Constraints
    -- -------------------------------------------------------------------------
                          CONSTRAINT pk_ad_views
                              PRIMARY KEY (id),

                          CONSTRAINT fk_ad_views_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users (id)
                                  ON DELETE SET NULL,

                          CONSTRAINT fk_ad_views_coin_transaction
                              FOREIGN KEY (coin_transaction_id)
                                  REFERENCES coin_transactions (id)
                                  ON DELETE SET NULL,

    -- Only REWARDED ads should have coins and verification
                          CONSTRAINT chk_ad_views_reward_type
                              CHECK (
                                  ad_type = 'REWARDED'
                                      OR (coins_rewarded = 0 AND verified = FALSE)
                                  ),

    -- verified_at must be set if verified = TRUE
                          CONSTRAINT chk_ad_views_verified_at
                              CHECK (
                                  verified = FALSE
                                      OR verified_at IS NOT NULL
                                  )
);

-- =============================================================================
-- Indexes
-- =============================================================================

-- THE most critical index — daily cap check runs before EVERY ad reward
-- SELECT COUNT(*) WHERE user_id=? AND view_date=? AND verified=TRUE AND app_id=?
CREATE INDEX idx_adviews_daily_cap
    ON ad_views (user_id, app_id, view_date)
    WHERE verified = TRUE AND ad_type = 'REWARDED';

-- Admin: total ad views per app per day
CREATE INDEX idx_adviews_app_date
    ON ad_views (app_id, view_date DESC, ad_type);

-- Admin: find all ads watched by a specific user
CREATE INDEX idx_adviews_user_date
    ON ad_views (user_id, viewed_at DESC)
    WHERE user_id IS NOT NULL;

-- Admin: unverified ads (monitoring for stuck verification jobs)
CREATE INDEX idx_adviews_unverified
    ON ad_views (app_id, viewed_at DESC)
    WHERE verified = FALSE AND ad_type = 'REWARDED';

-- Admin: daily bonus tracking
CREATE INDEX idx_adviews_daily_bonus
    ON ad_views (app_id, view_date)
    WHERE is_first_of_day = TRUE;

-- =============================================================================
-- Comments
-- =============================================================================
COMMENT ON TABLE  ad_views                      IS 'ADMIN PRIORITY: Every rewarded ad watch event. Used for daily cap enforcement, revenue analytics, and fraud detection.';
COMMENT ON COLUMN ad_views.user_id              IS 'FK to users.id SET NULL on delete. Ad analytics preserved even after user deletes account.';
COMMENT ON COLUMN ad_views.view_date            IS 'DATE column (not timestamp) for efficient daily cap COUNT queries. Indexed with user_id.';
COMMENT ON COLUMN ad_views.is_first_of_day      IS 'TRUE when this was the user''s first verified ad of the day. Triggers daily bonus coins.';
COMMENT ON COLUMN ad_views.coin_transaction_id  IS 'FK to coin_transactions. Links the ad event to the wallet transaction that rewarded the coins.';
COMMENT ON COLUMN ad_views.verification_token   IS 'Token from AdMob server-side verification callback. Prevents fake ad completion claims from client.';