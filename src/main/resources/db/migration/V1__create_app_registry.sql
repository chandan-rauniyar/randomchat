-- =============================================================================
-- V1__create_app_registry.sql
-- App Registry — must be created FIRST because all other tables reference app_id
-- logically (via CHECK or FK-like validation in application layer).
--
-- Relationship: One app_registry row → Many users, sessions, transactions
--               (app_id is not a FK in other tables — it's a VARCHAR for speed,
--                but values are validated against this table on every request
--                via the AppIdInterceptor in Spring Boot)
-- =============================================================================

CREATE TABLE app_registry (
                              id           UUID        NOT NULL DEFAULT gen_random_uuid(),
                              app_id       VARCHAR(50) NOT NULL,          -- used as FK-equivalent in all tables
                              app_name     VARCHAR(100) NOT NULL,
                              package_name VARCHAR(200),                  -- Android package, e.g. com.yourname.app1
                              is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
                              created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
                              updated_at   TIMESTAMP   NOT NULL DEFAULT NOW(),

                              CONSTRAINT pk_app_registry PRIMARY KEY (id),
                              CONSTRAINT uq_app_registry_app_id UNIQUE (app_id)
);

COMMENT ON TABLE  app_registry              IS 'Master list of all apps sharing this backend. Every app_id in the system must exist here.';
COMMENT ON COLUMN app_registry.app_id       IS 'Short identifier sent as X-App-ID header from Android. Used in every table as tenant key.';
COMMENT ON COLUMN app_registry.package_name IS 'Android package name — used for Play Store IAP receipt verification.';
COMMENT ON COLUMN app_registry.is_active    IS 'Inactive apps are rejected at the API gateway level.';

-- Seed: initial apps
INSERT INTO app_registry (app_id, app_name, package_name, is_active)
VALUES
    ('app1', 'RandomChat', 'com.yourname.randomchat', TRUE),
    ('app2', 'TalkNow',    'com.yourname.talknow',    TRUE);