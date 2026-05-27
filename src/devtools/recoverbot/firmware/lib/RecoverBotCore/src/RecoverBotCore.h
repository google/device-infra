#ifndef THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_LIB_RECOVERBOTCORE_RECOVERBOTCORE_H_
#define THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_LIB_RECOVERBOTCORE_RECOVERBOTCORE_H_

#include <stddef.h>
#include <stdint.h>

namespace recoverbot {

constexpr uint16_t kPowerServoPress = 360;
constexpr uint16_t kPowerServoRelease = 300;
constexpr uint16_t kVolumeUpServoPress = 360;
constexpr uint16_t kVolumeUpServoRelease = 300;
constexpr uint16_t kVolumeDownServoPress = 360;
constexpr uint16_t kVolumeDownServoRelease = 300;

struct ServoTicks {
  uint16_t press;
  uint16_t release;
};

bool isTickSafe(uint16_t tick, uint16_t minTick, uint16_t maxTick);
ServoTicks defaultTicksForChannel(uint8_t channel);
void buildCalKey(char* out, size_t cap, uint8_t boardAddr, uint8_t channel);
uint32_t packCalibration(uint16_t pressTick, uint16_t releaseTick);
bool unpackCalibration(uint32_t packed, uint16_t minTick, uint16_t maxTick,
                       ServoTicks* out);
bool shouldShowLineOnScreen(const char* line);

}  // namespace recoverbot

#endif  // THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_LIB_RECOVERBOTCORE_RECOVERBOTCORE_H_
