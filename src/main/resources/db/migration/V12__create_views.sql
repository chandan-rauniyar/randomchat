-- =============================================================================
-- V12__create_views.sql
-- Database Views — pre-built queries for admin dashboard and common lookups.
-- These are read-only views; all writes go through the actual tables.
--
-- Views created:
--   v_user_wallet          → user + live wallet state (for admin user lookup)
--   v_daily_ad_summary     → admin dashboard: ad revenue per app per day
--   v_daily_coin_economy   → admin dashboard: full economy report per day
--   v_active_bans          → all currently active (non-expired) bans
--   v_pending_reports      → moderation queue
--   v_user_session_counts  → total calls per user (engagement metric)
-- =============================================================================

-- =============================================================================
-- v_user_wallet — full user profile + wallet in one query
-- Used by: admin user search, /api/user/init response building
-- =============================================================================
CREATE OR REPLACE VIEW v_user_wallet AS
SELECT
    u.id,
    u.app_id,
    u.username,
    u.gender,
    u.country_code,
    u.coin_balance,
    u.match_credits,
    u.filter_credits_gender,
    u.filter_credits_country,
    u.active_gender_filter,
    u.active_country_filter,
    u.is_banned,
    u.ban_reason,
    u.ban_expires_at,
    u.is_deleted,
    u.created_at,
    u.last_seen_at,
    -- Is user currently online?
    CASE WHEN ou.user_id IS NOT NULL THEN TRUE ELSE FALSE END AS is_online,
    ou.is_in_queue,
    ou.is_in_call,
    ou.last_heartbeat
FROM users u
         LEFT JOIN online_users ou ON ou.user_id = u.id
WHERE u.is_deleted = FALSE;

COMMENT ON VIEW v_user_wallet IS 'Full user profile + wallet + live presence state. Joins users and online_users. Use for admin user lookup and init response.';

-- =============================================================================
-- v_daily_ad_summary — admin ad revenue dashboard
-- Used by: admin analytics, daily revenue report
-- =============================================================================
CREATE OR REPLACE VIEW v_daily_ad_summary AS
SELECT
    app_id,
    view_date,
    COUNT(*)                                    AS total_ad_views,
    COUNT(*) FILTER (WHERE verified = TRUE)     AS verified_views,
    COUNT(*) FILTER (WHERE is_first_of_day = TRUE) AS first_of_day_views,
    SUM(coins_rewarded)                         AS total_coins_rewarded,
    COUNT(DISTINCT user_id)                     AS unique_users
FROM ad_views
WHERE ad_type = 'REWARDED'
GROUP BY app_id, view_date
ORDER BY view_date DESC, app_id;

COMMENT ON VIEW v_daily_ad_summary IS 'Admin: daily ad view aggregates per app. Shows verified views, coins rewarded, unique users.';

-- =============================================================================
-- v_daily_coin_economy — full economy snapshot per day
-- Used by: admin financial dashboard
-- =============================================================================
CREATE OR REPLACE VIEW v_daily_coin_economy AS
SELECT
    app_id,
    DATE(created_at)                            AS economy_date,
    transaction_type,
    COUNT(*)                                    AS transaction_count,
    SUM(coin_amount) FILTER (WHERE coin_amount > 0) AS coins_credited,
    SUM(ABS(coin_amount)) FILTER (WHERE coin_amount < 0) AS coins_debited,
    SUM(match_credits_delta) FILTER (WHERE match_credits_delta > 0) AS match_credits_sold,
    SUM(ABS(match_credits_delta)) FILTER (WHERE match_credits_delta < 0) AS match_credits_used,
    SUM(gender_filter_delta) FILTER (WHERE gender_filter_delta > 0) AS gender_filter_sold,
    SUM(country_filter_delta) FILTER (WHERE country_filter_delta > 0) AS country_filter_sold,
    COUNT(DISTINCT user_id)                     AS unique_users
FROM coin_transactions
GROUP BY app_id, DATE(created_at), transaction_type
ORDER BY economy_date DESC, app_id, transaction_type;

COMMENT ON VIEW v_daily_coin_economy IS 'Admin: full economy breakdown per day per transaction type. Shows every flow of coins and credits.';

-- =============================================================================
-- v_active_bans — all currently active bans
-- Used by: admin ban management page, batch ban-expiry checker
-- =============================================================================
CREATE OR REPLACE VIEW v_active_bans AS
SELECT
    b.id            AS ban_id,
    b.app_id,
    b.user_id,
    u.username,
    b.ban_type,
    b.ban_source,
    b.reason,
    b.banned_by,
    b.banned_at,
    b.expires_at,
    CASE
        WHEN b.ban_type = 'PERMANENT' THEN NULL
        ELSE EXTRACT(EPOCH FROM (b.expires_at - NOW())) / 3600
        END             AS hours_remaining
FROM bans b
         JOIN users u ON u.id = b.user_id
WHERE b.is_lifted = FALSE
  AND (b.ban_type = 'PERMANENT' OR b.expires_at > NOW())
ORDER BY b.banned_at DESC;

COMMENT ON VIEW v_active_bans IS 'Admin: all currently active (not lifted, not expired) bans. Includes hours_remaining for temporary bans.';

-- =============================================================================
-- v_pending_reports — moderation queue
-- Used by: admin moderation dashboard
-- =============================================================================
CREATE OR REPLACE VIEW v_pending_reports AS
SELECT
    r.id                AS report_id,
    r.app_id,
    r.created_at        AS reported_at,
    reporter.username   AS reporter_username,
    reported.username   AS reported_username,
    r.reported_id,
    r.reason,
    r.description,
    r.session_id,
    s.started_at        AS session_started_at,
    s.duration_sec      AS session_duration_sec,
    -- How many total reports against this user?
    COUNT(r2.id) OVER (PARTITION BY r.reported_id) AS total_reports_against_user
FROM reports r
         LEFT JOIN users reporter ON reporter.id = r.reporter_id
         LEFT JOIN users reported ON reported.id = r.reported_id
         LEFT JOIN sessions s     ON s.id = r.session_id
         LEFT JOIN reports r2     ON r2.reported_id = r.reported_id AND r2.status IN ('PENDING', 'ACTIONED')
WHERE r.status = 'PENDING'
ORDER BY
    total_reports_against_user DESC,    -- most reported users first
    r.created_at ASC;                   -- oldest reports first within same priority

COMMENT ON VIEW v_pending_reports IS 'Admin moderation queue. Pending reports sorted by: most-reported users first, then oldest report first. Includes total_reports_against_user for auto-ban decision support.';

-- =============================================================================
-- v_user_session_counts — engagement metrics per user
-- Used by: admin analytics, identifying power users
-- =============================================================================
CREATE OR REPLACE VIEW v_user_session_counts AS
SELECT
    u.id                AS user_id,
    u.app_id,
    u.username,
    u.created_at,
    COUNT(s.id)                                                 AS total_sessions,
    COUNT(s.id) FILTER (WHERE s.started_at >= NOW() - INTERVAL '7 days')  AS sessions_last_7d,
    COUNT(s.id) FILTER (WHERE s.started_at >= NOW() - INTERVAL '30 days') AS sessions_last_30d,
    AVG(s.duration_sec)                                         AS avg_duration_sec,
    MAX(s.started_at)                                           AS last_session_at,
    SUM(s.duration_sec)                                         AS total_duration_sec
FROM users u
         LEFT JOIN sessions s ON (s.user1_id = u.id OR s.user2_id = u.id)
WHERE u.is_deleted = FALSE
GROUP BY u.id, u.app_id, u.username, u.created_at;

COMMENT ON VIEW v_user_session_counts IS 'Admin engagement: total calls and duration per user. Used to identify power users and track retention.';