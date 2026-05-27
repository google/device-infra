#ifndef THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_CALIBRATION_STORE_H_
#define THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_CALIBRATION_STORE_H_

#include <stdint.h>

void loadOneCal(uint8_t boardAddr, uint8_t channel);
void loadAllCalForDetectedBoards();
void saveOneCal(uint8_t boardAddr, uint8_t channel, uint16_t pressTick,
                uint16_t releaseTick);
void deleteOneCal(uint8_t boardAddr, uint8_t channel);
void getPressAndReleaseTicks(uint8_t boardAddr, uint8_t channel,
                             uint16_t& pressTick, uint16_t& releaseTick);

#endif  // THIRD_PARTY_DEVICEINFRA_SRC_DEVTOOLS_RECOVERBOT_FIRMWARE_CALIBRATION_STORE_H_
