-- =============================================================================
-- V5__create_sessions.sql
-- Sessions — records every accepted video call between two users.
--
-- Relationships:
--   Many-to-One with users (user1): sessions.user1_id → users.id
--   Many-to-One with users (user2): sessions.user2_id → users.id
--   One-to-Many with reports: reports.session_id → sessions.id
--
--   Effectively a Many-to-Many join between users (via two FKs),
--   plus additional call metadata.
--
-- FK behavior:
--   ON DELETE SET NULL — if a user hard-deletes, their sessions are kept
--   for analytics but user_id is set to NULL. Admin can still see the
--   session happened, just not who the user was.
--
-- Why are filter columns stored here?
--   Admin analytics: "how many matches used female filter this week?"
--   "which country filter is most popular?"
--   We record the filter state AT MATCH TIME, not the current user state.
-- =============================================================================

CREATE TYPE session_end_reason AS ENUM (
    'HANGUP',           -- one or both users tapped hang up
    'SKIP',             -- one user skipped during the match (shouldn't reach session, but safety)
    'DISCONNECTED',     -- WebRTC connection dropped (network issue)
    'TIMEOUT',          -- call lasted too long (safety limit if ever added)
    'ERROR'             -- backend or WebRTC error
);

CREATE TABLE sessions (
    -- -------------------------------------------------------------------------
    -- Identity
    -- -------------------------------------------------------------------------
                          id               UUID         NOT NULL DEFAULT gen_random_uuid(),
                          app_id           VARCHAR(50)  NOT NULL,            -- which app initiated the match

    -- -------------------------------------------------------------------------
    -- Participants (Many-to-One from each user's perspective)
    -- -------------------------------------------------------------------------
                          user1_id         UUID,                             -- NULL if user hard-deleted
                          user2_id         UUID,                             -- NULL if user hard-deleted

    -- -------------------------------------------------------------------------
    -- Filter state at match time (snapshot — not linked to live filter values)
    -- -------------------------------------------------------------------------
                          user1_gender_filter   gender_type,                 -- what gender filter user1 had active
                          user1_country_filter  VARCHAR(5),                  -- what country filter user1 had active
                          user2_gender_filter   gender_type,
                          user2_country_filter  VARCHAR(5),

    -- -------------------------------------------------------------------------
    -- Timing
    -- -------------------------------------------------------------------------
                          started_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                          ended_at         TIMESTAMP WITH TIME ZONE,
                          duration_sec     INTEGER      CHECK (duration_sec >= 0),

    -- -------------------------------------------------------------------------
    -- Outcome
    -- -------------------------------------------------------------------------
                          end_reason       session_end_reason,
                          was_reported     BOOLEAN      NOT NULL DEFAULT FALSE,

    -- -------------------------------------------------------------------------
    -- Audit
    -- -------------------------------------------------------------------------
                          created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- -------------------------------------------------------------------------
    -- Constraints
    -- -------------------------------------------------------------------------
                          CONSTRAINT pk_sessions
                              PRIMARY KEY (id),

                          CONSTRAINT fk_sessions_user1
                              FOREIGN KEY (user1_id)
                                  REFERENCES users (id)
                                  ON DELETE SET NULL,

                          CONSTRAINT fk_sessions_user2
                              FOREIGN KEY (user2_id)
                                  REFERENCES users (id)
                                  ON DELETE SET NULL,

    -- Users cannot match with themselves
                          CONSTRAINT chk_sessions_different_users
                              CHECK (user1_id <> user2_id),

    -- ended_at must be after started_at
                          CONSTRAINT chk_sessions_timing
                              CHECK (
                                  ended_at IS NULL
                                      OR ended_at >= started_at
                                  )
);

-- =============================================================================
-- Indexes
-- =============================================================================

-- User history page: "show me my recent calls" — most common query
CREATE INDEX idx_sessions_user1_date
    ON sessions (user1_id, started_at DESC)
    WHERE user1_id IS NOT NULL;

CREATE INDEX idx_sessions_user2_date
    ON sessions (user2_id, started_at DESC)
    WHERE user2_id IS NOT NULL;

-- Admin: all sessions per app, chronological
CREATE INDEX idx_sessions_app_date
    ON sessions (app_id, started_at DESC);

-- Admin: reported sessions — for moderation queue
CREATE INDEX idx_sessions_reported
    ON sessions (app_id, was_reported, started_at DESC)
    WHERE was_reported = TRUE;

-- Admin analytics: filter usage patterns
CREATE INDEX idx_sessions_gender_filter
    ON sessions (app_id, user1_gender_filter, user2_gender_filter, started_at DESC)
    WHERE user1_gender_filter IS NOT NULL OR user2_gender_filter IS NOT NULL;

CREATE INDEX idx_sessions_country_filter
    ON sessions (app_id, user1_country_filter, user2_country_filter, started_at DESC)
    WHERE user1_country_filter IS NOT NULL OR user2_country_filter IS NOT NULL;

-- =============================================================================
-- Comments
-- =============================================================================
COMMENT ON TABLE  sessions                        IS 'One row per accepted match. Created when both users accept. Updated on hang up.';
COMMENT ON COLUMN sessions.user1_id              IS 'FK to users.id. SET NULL on user hard-delete. NULL = user deleted their account.';
COMMENT ON COLUMN sessions.user2_id              IS 'FK to users.id. SET NULL on user hard-delete.';
COMMENT ON COLUMN sessions.user1_gender_filter   IS 'Snapshot of user1 active_gender_filter at time of match. NULL = was using global filter.';
COMMENT ON COLUMN sessions.duration_sec          IS 'Calculated on session end: EXTRACT(EPOCH FROM ended_at - started_at). NULL if call still active.';
COMMENT ON COLUMN sessions.was_reported          IS 'Set TRUE if any report references this session. Denormalized for fast moderation queries.';