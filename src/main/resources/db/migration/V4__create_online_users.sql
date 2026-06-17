-- =============================================================================
-- V4__create_online_users.sql
-- Online Users — real-time presence table.
--
-- Relationships:
--   One-to-One with users: each user has at most ONE online_users row.
--   (user_id is PK here = enforces the 1-to-1 constraint at DB level)
--   FK: online_users.user_id → users.id  (ON DELETE CASCADE)
--
-- Why CASCADE DELETE?
--   If a user row is ever hard-deleted (shouldn't happen — we soft delete —
--   but as a safety net), their presence row must also be removed.
--   Stale presence rows are also cleaned by the 60s scheduler job.
--
-- Phase notes:
--   Phase 1-2: This table IS the presence store. Heartbeat upserts here.
--   Phase 3+:  Redis takes over presence. This table becomes unused but kept
--              for analytics (admin can see historical presence patterns).
--              Simply stop writing to it when Redis is enabled.
--
-- The filter columns are COPIED from users on every heartbeat.
-- This allows the Phase 2 match queue to query a SINGLE table (fast)
-- without joining users for filter data.
-- =============================================================================

CREATE TABLE online_users (
    -- -------------------------------------------------------------------------
    -- Identity (1-to-1 with users)
    -- -------------------------------------------------------------------------
                              user_id              UUID         NOT NULL,
                              app_id               VARCHAR(50)  NOT NULL,

    -- -------------------------------------------------------------------------
    -- Presence
    -- -------------------------------------------------------------------------
                              last_heartbeat       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- -------------------------------------------------------------------------
    -- Profile snapshot (copied from users — denormalized for queue speed)
    -- -------------------------------------------------------------------------
                              gender               gender_type,
                              country_code         VARCHAR(5),

    -- -------------------------------------------------------------------------
    -- Active filter snapshot (copied from users on every heartbeat)
    -- Matching engine reads these — must match users.active_*_filter exactly
    -- -------------------------------------------------------------------------
                              active_gender_filter  gender_type,
                              active_country_filter VARCHAR(5),

    -- -------------------------------------------------------------------------
    -- Queue and call state (updated by Phase 2 match engine)
    -- -------------------------------------------------------------------------
                              is_in_queue          BOOLEAN      NOT NULL DEFAULT FALSE,
                              is_in_call           BOOLEAN      NOT NULL DEFAULT FALSE,
                              current_session_id   UUID,                            -- set when in call

    -- -------------------------------------------------------------------------
    -- Push notification token
    -- -------------------------------------------------------------------------
                              fcm_token            VARCHAR(255),

    -- -------------------------------------------------------------------------
    -- Constraints
    -- -------------------------------------------------------------------------
                              CONSTRAINT pk_online_users
                                  PRIMARY KEY (user_id),                            -- PK enforces 1-to-1

                              CONSTRAINT fk_online_users_user
                                  FOREIGN KEY (user_id)
                                      REFERENCES users (id)
                                      ON DELETE CASCADE,                                -- remove presence if user hard-deleted

    -- User cannot be in queue AND in a call simultaneously
                              CONSTRAINT chk_online_users_state
                                  CHECK (NOT (is_in_queue = TRUE AND is_in_call = TRUE)),

    -- session_id must be set if in call
                              CONSTRAINT chk_online_users_session
                                  CHECK (
                                      is_in_call = FALSE
                                          OR current_session_id IS NOT NULL
                                      )
);

-- =============================================================================
-- Indexes
-- =============================================================================

-- THE most critical index — used by match queue every few seconds
-- Covers: is_in_queue filter + app_id tenant + both filter dimensions
CREATE INDEX idx_online_users_queue
    ON online_users (app_id, is_in_queue, active_gender_filter, active_country_filter)
    WHERE is_in_queue = TRUE;                             -- partial: only queued users

-- Presence cleanup job: find stale rows
-- DELETE FROM online_users WHERE last_heartbeat < NOW() - INTERVAL '60 seconds'
CREATE INDEX idx_online_users_heartbeat
    ON online_users (last_heartbeat);

-- Admin: count online per app
CREATE INDEX idx_online_users_app
    ON online_users (app_id);

-- Cross-app matching: find queued users globally regardless of app
CREATE INDEX idx_online_users_global_queue
    ON online_users (is_in_queue, active_gender_filter, active_country_filter)
    WHERE is_in_queue = TRUE;

-- =============================================================================
-- Comments
-- =============================================================================
COMMENT ON TABLE  online_users                       IS '1-to-1 with users. Exists only while user is online. Row inserted on heartbeat, deleted on offline signal or 60s stale cleanup.';
COMMENT ON COLUMN online_users.user_id               IS 'PK + FK to users.id. One row per user enforced at DB level.';
COMMENT ON COLUMN online_users.last_heartbeat        IS 'Updated every 30s by Android heartbeat. User considered offline if older than 60s.';
COMMENT ON COLUMN online_users.active_gender_filter  IS 'Copied from users.active_gender_filter on every heartbeat. Used by match engine to find compatible users.';
COMMENT ON COLUMN online_users.active_country_filter IS 'Copied from users.active_country_filter on every heartbeat.';
COMMENT ON COLUMN online_users.is_in_queue           IS 'TRUE when user has entered the match queue. Set by Phase 2 match engine.';
COMMENT ON COLUMN online_users.is_in_call            IS 'TRUE during an active WebRTC call. Set by Phase 2 match engine.';
COMMENT ON COLUMN online_users.current_session_id    IS 'FK-equivalent to sessions.id. Not a hard FK to avoid circular dependency on session creation timing.';