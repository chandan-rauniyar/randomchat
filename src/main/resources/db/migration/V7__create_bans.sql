-- =============================================================================
-- V7__create_bans.sql
-- Bans — complete audit log of all bans issued and lifted.
--
-- Relationships:
--   Many-to-One with users: bans.user_id → users.id
--   Soft reference to reports: bans.report_id is UUID (not hard FK)
--     Why soft reference? A ban can exist without a report (admin ban).
--     And reports can be deleted without affecting ban history.
--
-- Note: The live ban state is on users.is_banned / ban_reason / ban_expires_at.
-- This table is the HISTORY/AUDIT LOG of bans.
-- Both are updated atomically in a single transaction when ban is issued.
--
-- Ban lifecycle:
--   Issue ban → INSERT here + UPDATE users.is_banned = TRUE
--   Lift ban   → UPDATE bans.lifted_at + UPDATE users.is_banned = FALSE
-- =============================================================================

CREATE TYPE ban_type AS ENUM (
    'TEMPORARY',        -- expires at ban_expires_at
    'PERMANENT'         -- never expires unless manually lifted
);

CREATE TYPE ban_source AS ENUM (
    'SYSTEM',           -- automatic ban from report threshold
    'ADMIN',            -- manually issued by admin
    'AUTO_REPORT'       -- triggered by N reports within time window
);

CREATE TABLE bans (
    -- -------------------------------------------------------------------------
    -- Identity
    -- -------------------------------------------------------------------------
                      id               UUID         NOT NULL DEFAULT gen_random_uuid(),
                      app_id           VARCHAR(50)  NOT NULL,

    -- -------------------------------------------------------------------------
    -- Who is banned
    -- -------------------------------------------------------------------------
                      user_id          UUID         NOT NULL,

    -- -------------------------------------------------------------------------
    -- Why they were banned
    -- -------------------------------------------------------------------------
                      ban_type         ban_type     NOT NULL,
                      ban_source       ban_source   NOT NULL DEFAULT 'ADMIN',
                      reason           TEXT         NOT NULL,
                      report_id        UUID,                               -- soft reference to reports.id

    -- -------------------------------------------------------------------------
    -- Who banned them
    -- -------------------------------------------------------------------------
                      banned_by        VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',  -- admin username or "SYSTEM"

    -- -------------------------------------------------------------------------
    -- Timing
    -- -------------------------------------------------------------------------
                      banned_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                      expires_at       TIMESTAMP WITH TIME ZONE,           -- NULL for PERMANENT bans

    -- -------------------------------------------------------------------------
    -- Lift (unban)
    -- -------------------------------------------------------------------------
                      is_lifted        BOOLEAN      NOT NULL DEFAULT FALSE,
                      lifted_at        TIMESTAMP WITH TIME ZONE,
                      lifted_by        VARCHAR(100),                       -- admin username who lifted
                      lift_reason      TEXT,

    -- -------------------------------------------------------------------------
    -- Constraints
    -- -------------------------------------------------------------------------
                      CONSTRAINT pk_bans
                          PRIMARY KEY (id),

                      CONSTRAINT fk_bans_user
                          FOREIGN KEY (user_id)
                              REFERENCES users (id)
                              ON DELETE CASCADE,                               -- user deleted = ban record deleted too

    -- TEMPORARY bans must have an expiry date
                      CONSTRAINT chk_bans_temporary_expiry
                          CHECK (
                              ban_type = 'PERMANENT'
                                  OR expires_at IS NOT NULL
                              ),

    -- lifted_at must be set if is_lifted = TRUE
                      CONSTRAINT chk_bans_lifted_at
                          CHECK (
                              is_lifted = FALSE
                                  OR lifted_at IS NOT NULL
                              ),

    -- expires_at must be in the future when ban is issued (app layer enforces,
    -- DB allows past for imported/test data)
                      CONSTRAINT chk_bans_expires_after_ban
                          CHECK (
                              expires_at IS NULL
                                  OR expires_at > banned_at
                              )
);

-- =============================================================================
-- Indexes
-- =============================================================================

-- Find active bans for a user (used in /init and status checks)
CREATE INDEX idx_bans_user_active
    ON bans (user_id, app_id, is_lifted, expires_at)
    WHERE is_lifted = FALSE;

-- Admin: all bans per app, most recent first
CREATE INDEX idx_bans_app_date
    ON bans (app_id, banned_at DESC);

-- Admin: find bans linked to a specific report
CREATE INDEX idx_bans_report
    ON bans (report_id)
    WHERE report_id IS NOT NULL;

-- Scheduler: find temporary bans that have expired and need auto-lift
CREATE INDEX idx_bans_expired
    ON bans (expires_at)
    WHERE ban_type = 'TEMPORARY' AND is_lifted = FALSE AND expires_at IS NOT NULL;

-- =============================================================================
-- Comments
-- =============================================================================
COMMENT ON TABLE  bans             IS 'Audit log of all bans. Live ban state is on users.is_banned. Both updated atomically.';
COMMENT ON COLUMN bans.user_id     IS 'FK to users.id ON DELETE CASCADE. If user hard-deleted, ban history also removed.';
COMMENT ON COLUMN bans.report_id   IS 'Soft reference to reports.id. NOT a hard FK — ban can exist without a report (admin ban).';
COMMENT ON COLUMN bans.banned_by   IS 'SYSTEM for auto-bans, admin username for manual bans.';
COMMENT ON COLUMN bans.expires_at  IS 'NULL for permanent bans. Scheduler job auto-lifts when this timestamp is passed.';
COMMENT ON COLUMN bans.is_lifted   IS 'TRUE when ban has been manually or automatically lifted.';