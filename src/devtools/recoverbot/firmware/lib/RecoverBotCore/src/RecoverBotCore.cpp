#include "RecoverBotCore.h"

#include <stdio.h>

namespace recoverbot {

bool isTickSafe(uint16_t tick, uint16_t minTick, uint16_t maxTick) {
  return tick >= minTick && tick <= maxTick;
}

ServoTicks defaultTicksForChannel(uint8_t channel) {
  switch (channel % 4) {
    case 0:
      return {kPowerServoPress, kPowerServoRelease};
    case 1:
      return {kVolumeUpServoPress, kVolumeUpServoRelease};
    case 2:
      return {kVolumeDownServoPress, kVolumeDownServoRelease};
    default:
      return {kPowerServoPress, kPowerServoRelease};
  }
}

void buildCalKey(char* out, size_t cap, uint8_t boardAddr, uint8_t channel) {
  if (!out || cap == 0) return;
  snprintf(out, cap, "c_%02X_%02u", boardAddr, channel);
}

uint32_t packCalibration(uint16_t pressTick, uint16_t releaseTick) {
  return (static_cast<uint32_t>(pressTick) << 16) |
         static_cast<uint32_t>(releaseTick);
}

bool unpackCalibration(uint32_t packed, uint16_t minTick, uint16_t maxTick,
                       ServoTicks* out) {
  if (packed == 0 || !out) return false;

  ServoTicks ticks = {
      static_cast<uint16_t>(packed >> 16),
      static_cast<uint16_t>(packed & 0xFFFF),
  };

  if (!isTickSafe(ticks.press, minTick, maxTick) ||
      !isTickSafe(ticks.release, minTick, maxTick)) {
    return false;
  }

  *out = ticks;
  return true;
}

bool shouldShowLineOnScreen(const char* line) {
  if (!line || line[0] == '\0') return false;

  bool onlySeparators = true;
  for (const char* p = line; *p; ++p) {
    if (*p != '=' && *p != '-' && *p != ' ') {
      onlySeparators = false;
      break;
    }
  }
  return !onlySeparators;
}

}  // namespace recoverbot
