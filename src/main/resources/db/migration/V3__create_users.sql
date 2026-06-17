-- =============================================================================
-- V3__create_users.sql
-- Users — core identity table with wallet state embedded.
--
-- Relationships:
--   Self: none (users don't reference each other directly)
--   Parent of:
--     online_users       (1-to-1,  FK: online_users.user_id → users.id)
--     sessions           (1-to-many, FK: sessions.user1_id, user2_id → users.id)
--     reports            (1-to-many, FK: reports.reporter_id, reported_id → users.id)
--     bans               (1-to-many, FK: bans.user_id → users.id)
--     coin_transactions  (1-to-many, FK: coin_transactions.user_id → users.id)
--     ad_views           (1-to-many, FK: ad_views.user_id → users.id)
--     purchases          (1-to-many, FK: purchases.user_id → users.id)
--
-- Design notes:
--   - device_id_hash is SHA-256 of raw deviceId:appId — raw ID never stored
--   - UNIQUE(device_id_hash, app_id) ensures one account per device per app
--   - Soft delete: is_deleted=TRUE keeps row for admin history; new install = new UUID
--   - Wallet fields are all NOT NULL with DEFAULT 0 — no nullable integers
--   - active_*_filter columns are NULL when filter is global (no filter)
-- =============================================================================

CREATE TYPE gender_type AS ENUM ('MALE', 'FEMALE', 'OTHER');

CREATE TABLE users (
    -- -------------------------------------------------------------------------
    -- Identity
    -- -------------------------------------------------------------------------
                       id               UUID          NOT NULL DEFAULT gen_random_uuid(),
                       app_id           VARCHAR(50)   NOT NULL,
                       device_id_hash   VARCHAR(64)   NOT NULL,         -- SHA-256(rawDeviceId + ":" + appId)
                       username         VARCHAR(50)   NOT NULL,          -- auto-assigned: "User_4821"

    -- -------------------------------------------------------------------------
    -- Profile
    -- -------------------------------------------------------------------------
                       gender           gender_type,                     -- NULL until onboarding completed
                       country_code     VARCHAR(5),                      -- ISO 3166-1 alpha-2: "IN", "US"

    -- -------------------------------------------------------------------------
    -- Coin Wallet
    -- -------------------------------------------------------------------------
                       coin_balance            INTEGER NOT NULL DEFAULT 0  CHECK (coin_balance >= 0),

    -- -------------------------------------------------------------------------
    -- Match Credits
    -- -------------------------------------------------------------------------
                       match_credits           INTEGER NOT NULL DEFAULT 0  CHECK (match_credits >= 0),

    -- -------------------------------------------------------------------------
    -- Filter Credits
    -- -------------------------------------------------------------------------
                       filter_credits_gender   INTEGER NOT NULL DEFAULT 0  CHECK (filter_credits_gender >= 0),
                       filter_credits_country  INTEGER NOT NULL DEFAULT 0  CHECK (filter_credits_country >= 0),

    -- -------------------------------------------------------------------------
    -- Active Filter Selection
    -- NULL = global (no filter active for that dimension)
    -- -------------------------------------------------------------------------
                       active_gender_filter    gender_type,               -- NULL | MALE | FEMALE
                       active_country_filter   VARCHAR(5),                -- NULL | "IN" | "US" etc.

    -- -------------------------------------------------------------------------
    -- Ban State
    -- -------------------------------------------------------------------------
                       is_banned        BOOLEAN       NOT NULL DEFAULT FALSE,
                       ban_reason       TEXT,
                       ban_expires_at   TIMESTAMP WITH TIME ZONE,         -- NULL = permanent ban

    -- -------------------------------------------------------------------------
    -- Soft Delete
    -- -------------------------------------------------------------------------
                       is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
                       deleted_at       TIMESTAMP WITH TIME ZONE,

    -- -------------------------------------------------------------------------
    -- Audit
    -- -------------------------------------------------------------------------
                       created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                       updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                       last_seen_at     TIMESTAMP WITH TIME ZONE,

    -- -------------------------------------------------------------------------
    -- Constraints
    -- -------------------------------------------------------------------------
                       CONSTRAINT pk_users
                           PRIMARY KEY (id),

                       CONSTRAINT uq_users_device_app
                           UNIQUE (device_id_hash, app_id),              -- one account per device per app

                       CONSTRAINT uq_users_username_app
                           UNIQUE (username, app_id),                    -- usernames unique within an app

    -- Filter can only be set if corresponding credits > 0
    -- (enforced in application layer too, but DB is the backstop)
                       CONSTRAINT chk_users_gender_filter
                           CHECK (
                               active_gender_filter IS NULL
                                   OR filter_credits_gender > 0
                               ),

                       CONSTRAINT chk_users_country_filter
                           CHECK (
                               active_country_filter IS NULL
                                   OR filter_credits_country > 0
                               ),

    -- Banned user must have a reason
                       CONSTRAINT chk_users_ban_reason
                           CHECK (
                               is_banned = FALSE
                                   OR ban_reason IS NOT NULL
                               ),

    -- Deleted user must have deletion timestamp
                       CONSTRAINT chk_users_deleted_at
                           CHECK (
                               is_deleted = FALSE
                                   OR deleted_at IS NOT NULL
                               )
);

-- =============================================================================
-- Indexes
-- =============================================================================

-- Primary lookup: init endpoint calls this on every app launch
CREATE UNIQUE INDEX idx_users_identity
    ON users (device_id_hash, app_id)
    WHERE is_deleted = FALSE;               -- partial: only active accounts

-- Admin: list all users per app
CREATE INDEX idx_users_app_id
    ON users (app_id, created_at DESC);

-- Matching engine: filter by country (for country-filter matching in Phase 2)
CREATE INDEX idx_users_country
    ON users (app_id, country_code)
    WHERE is_deleted = FALSE AND is_banned = FALSE;

-- Admin: find all banned users quickly
CREATE INDEX idx_users_banned
    ON users (app_id, is_banned, ban_expires_at)
    WHERE is_banned = TRUE;

-- Admin: users with match credits (for engagement analytics)
CREATE INDEX idx_users_match_credits
    ON users (app_id, match_credits)
    WHERE match_credits > 0 AND is_deleted = FALSE;

-- =============================================================================
-- Comments
-- =============================================================================
COMMENT ON TABLE  users                        IS 'Core user identity and wallet. One row per device per app. Soft-deleted on account removal.';
COMMENT ON COLUMN users.id                     IS 'Stable UUID. Returned to Android on /init. Persists across reinstalls.';
COMMENT ON COLUMN users.app_id                 IS 'Which app this user belongs to. Must match app_registry.app_id.';
COMMENT ON COLUMN users.device_id_hash         IS 'SHA-256 of (rawAndroidId + ":" + appId). Raw device ID never stored.';
COMMENT ON COLUMN users.username               IS 'Auto-assigned on creation. Format: User_XXXX. Unique within app.';
COMMENT ON COLUMN users.coin_balance           IS 'Raw coins. Spent to buy match credits or filter credits. Never goes below 0.';
COMMENT ON COLUMN users.match_credits          IS 'One consumed per accepted match (both sides). NOT consumed on queue entry.';
COMMENT ON COLUMN users.filter_credits_gender  IS 'One consumed per accepted match when active_gender_filter is set.';
COMMENT ON COLUMN users.filter_credits_country IS 'One consumed per accepted match when active_country_filter is set.';
COMMENT ON COLUMN users.active_gender_filter   IS 'NULL = global. MALE or FEMALE = filtered. Auto-cleared when credits hit 0.';
COMMENT ON COLUMN users.active_country_filter  IS 'NULL = global. ISO country code = filtered. Auto-cleared when credits hit 0.';
COMMENT ON COLUMN users.is_deleted             IS 'TRUE after user deletes account. Row kept for admin audit. Next install creates new UUID.';