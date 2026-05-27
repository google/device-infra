#ifndef THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_APP_STATE_H_
#define THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_APP_STATE_H_

#include <stddef.h>
#include <stdint.h>

class Adafruit_PWMServoDriver;
class Preferences;

constexpr uint32_t FORCE_MAX_PRESS_MS = 100000UL;
constexpr int MAX_CHANNELS = 16;
constexpr int MAX_BOARDS = 8;

struct ServoState {
  bool active = false;
  uint32_t releaseTime = 0;
};

struct CalEntry {
  bool has = false;
  uint16_t pressTick = 0;
  uint16_t releaseTick = 0;
};

extern uint32_t g_rawUnlockWindowMs;
extern uint16_t g_rawTickMin;
extern uint16_t g_rawTickMax;
extern bool g_rawEnabled;
extern uint32_t g_rawExpire;

extern uint8_t boardAddresses[MAX_BOARDS];
extern uint8_t numBoards;
extern Adafruit_PWMServoDriver* pwmBoards[MAX_BOARDS];
extern ServoState servoStates[MAX_BOARDS][MAX_CHANNELS];
extern uint16_t lastTicks[MAX_BOARDS][MAX_CHANNELS];
extern CalEntry g_cal[MAX_BOARDS][MAX_CHANNELS];
extern Preferences prefs;

void logMsg(const char* format, ...);
void markEvent();

int getBoardIndex(uint8_t boardAddr);
bool isTickSafe(uint16_t tick);

void saveGlobalConfig();
void resetGlobalConfig();

#endif  // THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_APP_STATE_H_
