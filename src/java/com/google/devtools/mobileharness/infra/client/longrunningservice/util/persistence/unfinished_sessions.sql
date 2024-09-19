CREATE TABLE IF NOT EXISTS unfinished_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  session_data VARBINARY(32768) NOT NULL -- binary of mobileharness.infra.client.longrunningservice.SessionPersistenceData
) CHARACTER SET utf8mb4;
