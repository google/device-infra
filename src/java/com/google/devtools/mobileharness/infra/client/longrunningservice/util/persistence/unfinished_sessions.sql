CREATE TABLE IF NOT EXISTS unfinished_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  session_data MEDIUMBLOB NOT NULL -- binary of mobileharness.infra.client.longrunningservice.SessionPersistenceData
) CHARACTER SET utf8mb4;

-- Safely migrate existing VARBINARY columns to MEDIUMBLOB in older installations.
-- Modifying a column to its current type is a seamless no-op in MySQL.
ALTER TABLE unfinished_sessions MODIFY COLUMN session_data MEDIUMBLOB NOT NULL;

