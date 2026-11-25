-- ============================================================
--  Fougnie Hub — schema v27
--  Grocery Accountant phone app: optional sign-in and sync.
--
--  * family_app_tokens: one row per signed-in phone. The phone keeps a
--    random key; only its SHA-256 hash is stored here. Signing out on
--    the phone (or deleting the row) ends that phone's access.
--  * Shopping lists and items get updated_at, so a sync can tell when
--    the website changed something after the phone last synced (the
--    website's version wins in that case).
--
--  Import via phpMyAdmin (database mikfou9_ei) after schema_v26.
--  Needs MySQL 5.6.5+ / MariaDB 10+ (two auto-updating timestamps).
--  "duplicate column" errors on re-run are safe to ignore.
-- ============================================================

CREATE TABLE IF NOT EXISTS family_app_tokens (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  user_id       INT          NOT NULL,
  token_hash    CHAR(64)     NOT NULL,
  device        VARCHAR(80)  NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at  TIMESTAMP    NULL,
  UNIQUE KEY uq_token (token_hash),
  KEY idx_user (user_id),
  CONSTRAINT fk_apptok_user FOREIGN KEY (user_id) REFERENCES family_users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

ALTER TABLE family_shopping_lists ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE family_shopping_items ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
