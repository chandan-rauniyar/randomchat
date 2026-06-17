-- =============================================================================
-- V6__create_reports.sql
-- Reports — user abuse reports submitted during or after a call.
--
-- Relationships:
--   Many-to-One with users (reporter): reports.reporter_id → users.id
--   Many-to-One with users (reported): reports.reported_id → users.id
--   Many-to-One with sessions: reports.session_id → sessions.id
--
-- FK behavior:
--   reporter_id: ON DELETE SET NULL — reporter deleted, report kept for moderation
--   reported_id: ON DELETE SET NULL — reported user deleted, report still reviewable
--   session_id:  ON DELETE SET NULL — session cleaned up, report still exists
--
-- Report lifecycle:
--   PENDING → REVIEWED (admin looked at it) → ACTIONED (ban issued) | DISMISSED
-- =============================================================================

CREATE TYPE report_reason AS ENUM (
    'NUDITY',           -- explicit content shown on camera
    'HARASSMENT',       -- verbal or visual harassment
    'SPAM',             -- repeating join/leave, advertising
    'UNDERAGE',         -- appears to be a minor
    'HATE_SPEECH',      -- discriminatory language/gestures
    'VIOLENCE',         -- threatening behaviour
    'OTHER'             -- catch-all with description field
);

CREATE TYPE report_status AS ENUM (
    'PENDING',          -- not yet reviewed by admin
    'REVIEWED',         -- admin has seen it, no action yet
    'ACTIONED',         -- ban or warning issued
    'DISMISSED'         -- admin determined no violation
);

CREATE TABLE reports (
    -- -------------------------------------------------------------------------
    -- Identity
    -- -------------------------------------------------------------------------
                         id               UUID          NOT NULL DEFAULT gen_random_uuid(),
                         app_id           VARCHAR(50)   NOT NULL,

    -- -------------------------------------------------------------------------
    -- Participants
    -- -------------------------------------------------------------------------
                         reporter_id      UUID,                               -- who filed the report
                         reported_id      UUID,                               -- who is being reported
                         session_id       UUID,                               -- which call this happened in

    -- -------------------------------------------------------------------------
    -- Report Content
    -- -------------------------------------------------------------------------
                         reason           report_reason NOT NULL,
                         description      TEXT,                               -- free text, optional

    -- -------------------------------------------------------------------------
    -- Moderation State
    -- -------------------------------------------------------------------------
                         status           report_status NOT NULL DEFAULT 'PENDING',
                         reviewed_by      VARCHAR(100),                       -- admin username who reviewed
                         reviewed_at      TIMESTAMP WITH TIME ZONE,
                         admin_notes      TEXT,                               -- internal admin notes

    -- -------------------------------------------------------------------------
    -- Audit
    -- -------------------------------------------------------------------------
                         created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- -------------------------------------------------------------------------
    -- Constraints
    -- -------------------------------------------------------------------------
                         CONSTRAINT pk_reports
                             PRIMARY KEY (id),

                         CONSTRAINT fk_reports_reporter
                             FOREIGN KEY (reporter_id)
                                 REFERENCES users (id)
                                 ON DELETE SET NULL,

                         CONSTRAINT fk_reports_reported
                             FOREIGN KEY (reported_id)
                                 REFERENCES users (id)
                                 ON DELETE SET NULL,

                         CONSTRAINT fk_reports_session
                             FOREIGN KEY (session_id)
                                 REFERENCES sessions (id)
                                 ON DELETE SET NULL,

    -- Cannot report yourself
                         CONSTRAINT chk_reports_not_self
                             CHECK (reporter_id <> reported_id),

    -- reviewed_at must be set if status is not PENDING
                         CONSTRAINT chk_reports_reviewed_at
                             CHECK (
                                 status = 'PENDING'
                                     OR reviewed_at IS NOT NULL
                                 )
);

-- =============================================================================
-- Indexes
-- =============================================================================

-- Admin moderation queue: pending reports, oldest first
CREATE INDEX idx_reports_pending
    ON reports (app_id, status, created_at ASC)
    WHERE status = 'PENDING';

-- Admin: all reports against a specific user
CREATE INDEX idx_reports_reported_user
    ON reports (reported_id, created_at DESC)
    WHERE reported_id IS NOT NULL;

-- Admin: reports filed by a specific user (detect false reporters)
CREATE INDEX idx_reports_reporter
    ON reports (reporter_id, created_at DESC)
    WHERE reporter_id IS NOT NULL;

-- Admin: all reports linked to a session
CREATE INDEX idx_reports_session
    ON reports (session_id)
    WHERE session_id IS NOT NULL;

-- Auto-ban trigger query: count pending/actioned reports per reported user
CREATE INDEX idx_reports_auto_ban_check
    ON reports (reported_id, app_id, status)
    WHERE reported_id IS NOT NULL AND status IN ('PENDING', 'ACTIONED');

-- =============================================================================
-- Comments
-- =============================================================================
COMMENT ON TABLE  reports             IS 'User-submitted abuse reports. One report per incident. Multiple reports on same user accumulate for auto-ban logic.';
COMMENT ON COLUMN reports.reporter_id IS 'FK to users.id. SET NULL if reporter deletes account. Report still valid for moderation.';
COMMENT ON COLUMN reports.reported_id IS 'FK to users.id. SET NULL if reported user deletes account.';
COMMENT ON COLUMN reports.session_id  IS 'FK to sessions.id. Links report to the specific call where violation occurred.';
COMMENT ON COLUMN reports.status      IS 'PENDING = needs admin review. ACTIONED = ban issued. DISMISSED = no violation found.';