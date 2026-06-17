-- =============================================================================
-- V2__create_app_config.sql
-- App Configuration — per-app key/value pricing and feature settings.
--
-- Relationship: Many-to-One with app_registry (many config rows per app_id)
--               No hard FK constraint on app_id for performance, but values
--               must match app_registry.app_id (enforced in application layer).
--
-- Why no FK on app_id?
--   Dropping an app from app_registry should not cascade-delete its config.
--   Admin may deactivate an app but keep its history and config intact.
-- =============================================================================

CREATE TABLE app_config (
                            id           UUID         NOT NULL DEFAULT gen_random_uuid(),
                            app_id       VARCHAR(50)  NOT NULL,
                            config_key   VARCHAR(100) NOT NULL,
                            config_value VARCHAR(255) NOT NULL,
                            description  TEXT,                          -- human-readable explanation for admin
                            updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
                            updated_by   VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',

                            CONSTRAINT pk_app_config             PRIMARY KEY (id),
                            CONSTRAINT uq_app_config_key         UNIQUE (app_id, config_key)
);

CREATE INDEX idx_app_config_app_id ON app_config (app_id);

COMMENT ON TABLE  app_config              IS 'Per-app runtime configuration. Change prices and limits without redeploying the backend.';
COMMENT ON COLUMN app_config.config_key   IS 'Key name used in application code to look up the value.';
COMMENT ON COLUMN app_config.config_value IS 'Always stored as VARCHAR; application code casts to correct type (Integer, Boolean, etc.).';

-- =============================================================================
-- Seed: default pricing for app1
-- =============================================================================
INSERT INTO app_config (app_id, config_key, config_value, description) VALUES
                                                                           ('app1', 'signup_bonus_coins',        '10',  'Coins given to every new user on first install'),
                                                                           ('app1', 'price_match_pack',          '10',  'Coins required to buy one match pack'),
                                                                           ('app1', 'match_credits_per_pack',    '20',  'Match credits granted per pack purchase'),
                                                                           ('app1', 'price_gender_filter_pack',  '10',  'Coins required to buy gender filter pack'),
                                                                           ('app1', 'gender_filter_per_pack',    '10',  'Gender filter credits granted per pack'),
                                                                           ('app1', 'price_country_filter_pack', '10',  'Coins required to buy country filter pack'),
                                                                           ('app1', 'country_filter_per_pack',   '10',  'Country filter credits granted per pack'),
                                                                           ('app1', 'price_bundle_filter_pack',  '18',  'Coins required to buy gender+country bundle (discount vs buying separately)'),
                                                                           ('app1', 'bundle_gender_credits',     '10',  'Gender filter credits in bundle pack'),
                                                                           ('app1', 'bundle_country_credits',    '10',  'Country filter credits in bundle pack'),
                                                                           ('app1', 'ad_coins_reward',           '3',   'Coins given for watching one rewarded ad'),
                                                                           ('app1', 'ad_first_daily_bonus',      '5',   'Coins given for FIRST rewarded ad of the day (replaces standard reward)'),
                                                                           ('app1', 'ad_daily_cap',              '10',  'Maximum rewarded ads a user can watch per day'),
                                                                           ('app1', 'queue_timeout_seconds',     '300', 'Seconds before user is removed from match queue with no match found'),
                                                                           ('app1', 'match_accept_timeout_sec',  '10',  'Seconds both users have to accept/skip a match');

-- =============================================================================
-- Seed: default pricing for app2 (identical to app1 by default, can diverge)
-- =============================================================================
INSERT INTO app_config (app_id, config_key, config_value, description) VALUES
                                                                           ('app2', 'signup_bonus_coins',        '10',  'Coins given to every new user on first install'),
                                                                           ('app2', 'price_match_pack',          '10',  'Coins required to buy one match pack'),
                                                                           ('app2', 'match_credits_per_pack',    '20',  'Match credits granted per pack purchase'),
                                                                           ('app2', 'price_gender_filter_pack',  '10',  'Coins required to buy gender filter pack'),
                                                                           ('app2', 'gender_filter_per_pack',    '10',  'Gender filter credits granted per pack'),
                                                                           ('app2', 'price_country_filter_pack', '10',  'Coins required to buy country filter pack'),
                                                                           ('app2', 'country_filter_per_pack',   '10',  'Country filter credits granted per pack'),
                                                                           ('app2', 'price_bundle_filter_pack',  '18',  'Coins required to buy gender+country bundle'),
                                                                           ('app2', 'bundle_gender_credits',     '10',  'Gender filter credits in bundle pack'),
                                                                           ('app2', 'bundle_country_credits',    '10',  'Country filter credits in bundle pack'),
                                                                           ('app2', 'ad_coins_reward',           '3',   'Coins given for watching one rewarded ad'),
                                                                           ('app2', 'ad_first_daily_bonus',      '5',   'Coins given for FIRST rewarded ad of the day'),
                                                                           ('app2', 'ad_daily_cap',              '10',  'Maximum rewarded ads a user can watch per day'),
                                                                           ('app2', 'queue_timeout_seconds',     '300', 'Seconds before user is removed from match queue'),
                                                                           ('app2', 'match_accept_timeout_sec',  '10',  'Seconds both users have to accept/skip a match');