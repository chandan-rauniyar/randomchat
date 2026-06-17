-- =============================================================================
-- V13__create_admin_users.sql
-- Admin Users — backend admin accounts for the moderation/analytics dashboard.
--
-- Relationships:
--   No FK to app_registry — admin users are super-tenant (can access all apps)
--   Referenced by (soft): bans.banned_by, reports.reviewed_by (stored as username string)
--
-- Passwords: stored as BCrypt hash (never plaintext)
-- This is separate from the main users table — admins are NOT app users.
-- =============================================================================

CREATE TYPE admin_role AS ENUM (
    'SUPER_ADMIN',      -- full access: all apps, all actions, can manage other admins
    'MODERATOR',        -- can review reports, issue bans, view user data
    'ANALYST'           -- read-only access: can view all analytics and history
);

CREATE TABLE admin_users (
                             id               UUID         NOT NULL DEFAULT gen_random_uuid(),
                             username         VARCHAR(50)  NOT NULL,
                             password_hash    VARCHAR(255) NOT NULL,                 -- BCrypt hash
                             email            VARCHAR(200),
                             role             admin_role   NOT NULL DEFAULT 'MODERATOR',

    -- Which apps this admin can access (NULL = all apps)
                             allowed_app_ids  TEXT[],                               -- NULL means access to ALL apps

                             is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
                             created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                             updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                             last_login_at    TIMESTAMP WITH TIME ZONE,

                             CONSTRAINT pk_admin_users
                                 PRIMARY KEY (id),

                             CONSTRAINT uq_admin_username
                                 UNIQUE (username),

                             CONSTRAINT uq_admin_email
                                 UNIQUE (email)
);

CREATE TRIGGER trg_admin_users_updated_at
    BEFORE UPDATE ON admin_users
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

-- =============================================================================
-- Admin Activity Log — track all admin actions for accountability
-- =============================================================================

CREATE TABLE admin_activity_log (
                                    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
                                    admin_id         UUID         NOT NULL,
                                    admin_username   VARCHAR(50)  NOT NULL,                -- denormalized for history
                                    app_id           VARCHAR(50),                          -- NULL = cross-app action
                                    action_type      VARCHAR(100) NOT NULL,
    -- Examples: BAN_USER, LIFT_BAN, GRANT_COINS, DEDUCT_COINS,
    --           REVIEW_REPORT, DISMISS_REPORT, VIEW_USER, EXPORT_DATA
                                    target_type      VARCHAR(50),                         -- 'USER', 'REPORT', 'SESSION', etc.
                                    target_id        UUID,                                -- ID of the affected entity
                                    details          JSONB,                               -- action-specific data (before/after values)
                                    ip_address       INET,
                                    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

                                    CONSTRAINT pk_admin_activity_log
                                        PRIMARY KEY (id),

                                    CONSTRAINT fk_admin_activity_admin
                                        FOREIGN KEY (admin_id)
                                            REFERENCES admin_users (id)
                                            ON DELETE RESTRICT                               -- cannot delete admin with activity log
);

CREATE INDEX idx_admin_log_admin_date
    ON admin_activity_log (admin_id, created_at DESC);

CREATE INDEX idx_admin_log_target
    ON admin_activity_log (target_type, target_id, created_at DESC)
    WHERE target_id IS NOT NULL;

CREATE INDEX idx_admin_log_app_date
    ON admin_activity_log (app_id, created_at DESC)
    WHERE app_id IS NOT NULL;

COMMENT ON TABLE  admin_users                     IS 'Backend admin accounts. Completely separate from app users table. Passwords stored as BCrypt hash.';
COMMENT ON COLUMN admin_users.allowed_app_ids     IS 'PostgreSQL TEXT array. NULL = access to all apps. Set to ARRAY[''app1''] to restrict to one app.';
COMMENT ON TABLE  admin_activity_log              IS 'Immutable audit log of all admin actions. Used for accountability and compliance.';
COMMENT ON COLUMN admin_activity_log.details      IS 'JSONB of action details. E.g. for BAN_USER: {userId, reason, duration, coinBalanceBefore}.';

-- =============================================================================
-- Seed: default super admin (password: change_me_immediately)
-- BCrypt hash of "change_me_immediately" with strength 12
-- IMPORTANT: Change this password immediately after first login
-- =============================================================================
INSERT INTO admin_users (username, password_hash, email, role, allowed_app_ids)
VALUES (
           'superadmin',
           '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCfkW/4h5sJ5Kz3pJZeVcue',
           'admin@yourdomain.com',
           'SUPER_ADMIN',
           NULL   -- access to all apps
       );