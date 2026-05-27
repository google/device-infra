#ifndef THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_SERVO_CONTROLLER_H_
#define THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_SERVO_CONTROLLER_H_

#include <stdint.h>

void applyTicks(int boardIndex, uint8_t channel, uint16_t ticks);
void releaseServo(uint8_t boardAddr, uint8_t channel);
void pressServo(uint8_t boardAddr, uint8_t channel, uint32_t duration);
void checkAndReleaseServos();
void setRawTicks(uint8_t boardAddr, uint8_t channel, uint16_t ticks);

#endif  // THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_SERVO_CONTROLLER_H_
