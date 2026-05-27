#include <unity.h>

#include <RecoverBotCore.h>

void test_tick_safety_is_inclusive() {
  TEST_ASSERT_FALSE(recoverbot::isTickSafe(249, 250, 450));
  TEST_ASSERT_TRUE(recoverbot::isTickSafe(250, 250, 450));
  TEST_ASSERT_TRUE(recoverbot::isTickSafe(450, 250, 450));
  TEST_ASSERT_FALSE(recoverbot::isTickSafe(451, 250, 450));
}

void test_default_ticks_follow_channel_groups() {
  recoverbot::ServoTicks power = recoverbot::defaultTicksForChannel(0);
  TEST_ASSERT_EQUAL_UINT16(360, power.press);
  TEST_ASSERT_EQUAL_UINT16(300, power.release);

  recoverbot::ServoTicks volumeUp = recoverbot::defaultTicksForChannel(5);
  TEST_ASSERT_EQUAL_UINT16(360, volumeUp.press);
  TEST_ASSERT_EQUAL_UINT16(300, volumeUp.release);

  recoverbot::ServoTicks fallback = recoverbot::defaultTicksForChannel(3);
  TEST_ASSERT_EQUAL_UINT16(360, fallback.press);
  TEST_ASSERT_EQUAL_UINT16(300, fallback.release);
}

void test_calibration_keys_are_stable() {
  char key[16];
  recoverbot::buildCalKey(key, sizeof(key), 0x40, 0);
  TEST_ASSERT_EQUAL_STRING("c_40_00", key);

  recoverbot::buildCalKey(key, sizeof(key), 0x47, 15);
  TEST_ASSERT_EQUAL_STRING("c_47_15", key);
}

void test_calibration_pack_round_trips_safe_ticks() {
  uint32_t packed = recoverbot::packCalibration(360, 300);
  recoverbot::ServoTicks ticks = {};

  TEST_ASSERT_TRUE(recoverbot::unpackCalibration(packed, 250, 450, &ticks));
  TEST_ASSERT_EQUAL_UINT16(360, ticks.press);
  TEST_ASSERT_EQUAL_UINT16(300, ticks.release);
}

void test_calibration_unpack_rejects_empty_and_unsafe_ticks() {
  recoverbot::ServoTicks ticks = {};

  TEST_ASSERT_FALSE(recoverbot::unpackCalibration(0, 250, 450, &ticks));
  TEST_ASSERT_FALSE(recoverbot::unpackCalibration(
      recoverbot::packCalibration(200, 300), 250, 450, &ticks));
  TEST_ASSERT_FALSE(recoverbot::unpackCalibration(
      recoverbot::packCalibration(360, 500), 250, 450, &ticks));
}

void test_screen_log_filters_empty_and_separator_lines() {
  TEST_ASSERT_FALSE(recoverbot::shouldShowLineOnScreen(nullptr));
  TEST_ASSERT_FALSE(recoverbot::shouldShowLineOnScreen(""));
  TEST_ASSERT_FALSE(recoverbot::shouldShowLineOnScreen("==== ----"));
  TEST_ASSERT_TRUE(recoverbot::shouldShowLineOnScreen("OK,READY"));
}

int main(int argc, char** argv) {
  UNITY_BEGIN();
  RUN_TEST(test_tick_safety_is_inclusive);
  RUN_TEST(test_default_ticks_follow_channel_groups);
  RUN_TEST(test_calibration_keys_are_stable);
  RUN_TEST(test_calibration_pack_round_trips_safe_ticks);
  RUN_TEST(test_calibration_unpack_rejects_empty_and_unsafe_ticks);
  RUN_TEST(test_screen_log_filters_empty_and_separator_lines);
  return UNITY_END();
}
