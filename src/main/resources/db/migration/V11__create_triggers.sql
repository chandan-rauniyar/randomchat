-- =============================================================================
-- V11__create_triggers.sql
-- Database Triggers:
--   1. updated_at auto-update on users and app_config
--   2. users.was_reported auto-set when a report is inserted for a session
--   3. ban auto-sync: when users.is_banned is set, validate ban exists in bans table
--   4. filter auto-clear: prevent impossible state (filter active, 0 credits)
-- =============================================================================

-- =============================================================================
-- 1. Auto-update updated_at timestamp
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply to users table
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

-- Apply to app_config table
CREATE TRIGGER trg_app_config_updated_at
    BEFORE UPDATE ON app_config
    FOR EACH ROW
    EXECUTE FUNCTION fn_set_updated_at();

-- =============================================================================
-- 2. Auto-set sessions.was_reported when a report is inserted
--    Denormalized flag for fast moderation queries
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_mark_session_reported()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.session_id IS NOT NULL THEN
UPDATE sessions
SET was_reported = TRUE
WHERE id = NEW.session_id
  AND was_reported = FALSE;   -- only write if not already TRUE (avoid needless I/O)
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reports_mark_session
    AFTER INSERT ON reports
    FOR EACH ROW
    EXECUTE FUNCTION fn_mark_session_reported();

-- =============================================================================
-- 3. Filter credit auto-clear
--    If a user's filter credits hit 0, clear the active filter automatically.
--    This runs on UPDATE to users so it catches:
--      - MATCH_DEDUCT reducing filter_credits to 0
--      - Admin manually setting credits to 0
--      - Any other path that reduces credits
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_clear_empty_filters()
RETURNS TRIGGER AS $$
BEGIN
    -- Clear gender filter if credits hit 0
    IF NEW.filter_credits_gender = 0 AND NEW.active_gender_filter IS NOT NULL THEN
        NEW.active_gender_filter = NULL;
END IF;

    -- Clear country filter if credits hit 0
    IF NEW.filter_credits_country = 0 AND NEW.active_country_filter IS NOT NULL THEN
        NEW.active_country_filter = NULL;
END IF;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_clear_filters
    BEFORE UPDATE OF filter_credits_gender, filter_credits_country ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_clear_empty_filters();

-- =============================================================================
-- 4. Prevent match_credits from going below 0 at DB level
--    Application layer should catch this, but DB is the safety net.
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_prevent_negative_credits()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.match_credits < 0 THEN
        RAISE EXCEPTION 'match_credits cannot go below 0 for user %', NEW.id;
END IF;
    IF NEW.filter_credits_gender < 0 THEN
        RAISE EXCEPTION 'filter_credits_gender cannot go below 0 for user %', NEW.id;
END IF;
    IF NEW.filter_credits_country < 0 THEN
        RAISE EXCEPTION 'filter_credits_country cannot go below 0 for user %', NEW.id;
END IF;
    IF NEW.coin_balance < 0 THEN
        RAISE EXCEPTION 'coin_balance cannot go below 0 for user %', NEW.id;
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_prevent_negative
    BEFORE UPDATE OF coin_balance, match_credits, filter_credits_gender, filter_credits_country ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_prevent_negative_credits();

-- =============================================================================
-- 5. Auto-sync online_users filter columns when users filter changes
--    When PATCH /api/user/filter updates users.active_*_filter,
--    the online_users row must also be updated so the match engine sees
--    the new filter immediately.
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_sync_online_user_filters()
RETURNS TRIGGER AS $$
BEGIN
    -- Only run if filters actually changed
    IF (NEW.active_gender_filter IS DISTINCT FROM OLD.active_gender_filter)
    OR (NEW.active_country_filter IS DISTINCT FROM OLD.active_country_filter) THEN
UPDATE online_users
SET
    active_gender_filter  = NEW.active_gender_filter,
    active_country_filter = NEW.active_country_filter
WHERE user_id = NEW.id;
-- If no online_users row exists (user offline), UPDATE affects 0 rows — fine.
-- Filter will be synced on next heartbeat.
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_sync_online_filters
    AFTER UPDATE OF active_gender_filter, active_country_filter ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_sync_online_user_filters();