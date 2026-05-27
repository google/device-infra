# RecoverBot Firmware

This project contains the firmware for the RecoverBot, targeting the M5Stack
CoreS3 device.

## Building the Project

The project uses PlatformIO for building. A helper script `build.sh` is
provided.

To build the firmware:
```bash
./build.sh
```

To clean build artifacts:
```bash
./build.sh -t clean
```

Note: To fully clean dependencies and force redownload, you can remove the
`.pio` directory:
```bash
rm -rf .pio
```

## Usage

The firmware communicates over Serial (baud rate 115200). It accepts text
commands.

On Linux, you can configure the serial port using `stty`. For example:
```bash
stty -F /dev/ttyACM0 115200 raw -echo -echoe -echok
```

### Supported Commands

- `ping`: Check if device is responsive.
- `status`: Print current status, including detected boards and active servos.
- `help`: Print help message with supported commands.
- `press_ms <boardHex> <ch> <ms>`: Press a servo on a specific channel for a
  duration in milliseconds.
- `press <boardHex> <ch>`: Press a servo (auto-release at 100s cap).
- `release <boardHex> <ch>`: Release a servo.
- `cal_set <boardHex> <ch> <pressTick> <releaseTick>`: Set calibration for a servo.
- `cal_get <boardHex> <ch>`: Get calibration for a servo.
- `cal_reset <boardHex> <ch>`: Reset calibration for a servo.
- `cal_list`: List all calibrations.
- `raw on [sec] | raw off`: Enable/disable raw mode (direct tick control).
- `raw_tick <boardHex> <ch> <tick>`: Set raw tick for a servo (requires raw
  mode ON).
- `cfg_set <win|min|max> <val>`: Set configuration values.
- `cfg_get`: Get current configuration.
- `cfg_reset`: Reset configuration to defaults.
