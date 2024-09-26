CREATE TABLE IF NOT EXISTS test_allocations (
  test_id VARCHAR(64) PRIMARY KEY,
  test_allocation VARBINARY(32768) NOT NULL -- binary of mobileharness.service.moss.AllocationProto
) CHARACTER SET utf8mb4;
