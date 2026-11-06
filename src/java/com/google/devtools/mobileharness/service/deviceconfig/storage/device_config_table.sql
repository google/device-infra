CREATE TABLE IF NOT EXISTS device_config_table (
  device_uuid VARCHAR(64) PRIMARY KEY,
  device_config VARBINARY(32768) NOT NULL -- binary of mobileharness.api.deviceconfig.DeviceConfig
) CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS lab_config_table (
  lab_host VARCHAR(64) PRIMARY KEY,
  lab_config VARBINARY(32768) NOT NULL -- binary of mobileharness.api.deviceconfig.LabConfig
) CHARACTER SET utf8mb4;

-- Table to store global config. This table would only have a single row with key "GLOBAL".
CREATE TABLE IF NOT EXISTS global_config_table (
  global_config_id VARCHAR(64) PRIMARY KEY,
  global_config VARBINARY(32768) NOT NULL -- binary of mobileharness.api.deviceconfig.GlobalConfig
) CHARACTER SET utf8mb4;
