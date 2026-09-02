# Standard Starlark rules for Mock ADB in USMF.

def _parse_adb_args(args):
    """Parses adb arguments, extracting serial and the command arguments."""
    target_serial = None
    cmd_args = []
    skip_next = False
    for i in range(len(args)):
        if skip_next:
            skip_next = False
            continue
        arg = args[i]
        if arg == "-s" and i + 1 < len(args):
            target_serial = args[i + 1]
            skip_next = True
        elif arg.startswith("-s="):
            target_serial = arg[3:]
        elif arg in ["-d", "-e", "-a"]:
            pass
        elif arg in ["-H", "-P", "-L", "-t"] and i + 1 < len(args):
            skip_next = True
        else:
            cmd_args.append(arg)

    if cmd_args and cmd_args[0] == "shell":
        # Normalize/flatten shell command arguments (e.g. `shell "pm list packages"` -> `shell`, `pm`, `list`, `packages`)
        tokens = ["shell"]
        for a in cmd_args[1:]:
            for part in a.split():
                tokens.append(part)
        cmd_args = tokens

    return target_serial, cmd_args


def _resolve_device(state, target_serial):
    """Resolves target device state from state dictionary.

    Returns (serial, device_dict, error_result).
    """
    devices = state.get("devices", {})
    if target_serial:
        if target_serial not in devices:
            return None, None, Result(stderr="error: device '%s' not found\n" % target_serial, exit_code=1)
        dev = devices[target_serial]
        if dev.get("status", "device") == "disconnected":
            return None, None, Result(stderr="error: device '%s' not found\n" % target_serial, exit_code=1)
        return target_serial, dev, None

    # No serial specified: check connected devices
    connected = [s for s, d in devices.items() if d.get("status", "device") != "disconnected"]
    if len(connected) == 0:
        return None, None, Result(stderr="error: no devices/emulators found\n", exit_code=1)
    if len(connected) > 1:
        return None, None, Result(stderr="error: more than one device/emulator\n", exit_code=1)

    serial = connected[0]
    return serial, devices[serial], None


def _check_device_online(dev, serial):
    """Checks if the device is ready for command communication."""
    status = dev.get("status", "device")
    if status == "offline":
        return Result(stderr="error: device offline\n", exit_code=1)
    elif status == "unauthorized":
        return Result(stderr="error: device unauthorized.\nThis adb server's $ADB_VENDOR_KEYS is not set\n", exit_code=1)
    elif status == "disconnected":
        return Result(stderr="error: device '%s' not found\n" % serial, exit_code=1)
    return None


def handle_version(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if cmd_args and cmd_args[0] in ["version", "--version"]:
        return Result(stdout="Android Debug Bridge version 1.0.41\nVersion 34.0.5-10900879\nInstalled as " + ctx.command.split(" ")[0] + "\n")
    return None


def handle_server(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if cmd_args and cmd_args[0] in ["start-server", "kill-server"]:
        return Result(stdout="")
    return None


def handle_devices(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args or cmd_args[0] != "devices":
        return None

    is_long = len(cmd_args) > 1 and "-l" in cmd_args[1:]
    devices = ctx.state.get("devices", {})
    lines = ["List of devices attached"]
    tid = 1
    for serial, dev in devices.items():
        status = dev.get("status", "device")
        if status == "disconnected":
            continue
        if is_long:
            props = dev.get("props", {})
            product = props.get("ro.product.name", "unknown")
            model = props.get("ro.product.model", "unknown").replace(" ", "_")
            device = props.get("ro.product.device", "unknown")
            lines.append("%s\t%s product:%s model:%s device:%s transport_id:%d" % (
                serial, status, product, model, device, tid
            ))
            tid += 1
        else:
            lines.append("%s\t%s" % (serial, status))
    return Result(stdout="\n".join(lines) + "\n")


def handle_get_state(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args or cmd_args[0] != "get-state":
        return None
    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    return Result(stdout="%s\n" % dev.get("status", "device"))


def handle_wait_for_device(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args or not (cmd_args[0].startswith("wait-for-") or cmd_args[0] == "wait-for-device"):
        return None
    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    wait_type = cmd_args[0]
    status = dev.get("status", "device")
    if wait_type in ["wait-for-device", "wait-for-any-device", "wait-for-local-device"]:
        if status == "device":
            return Result(stdout="")
        return Result(stderr="error: device '%s' not ready\n" % serial, exit_code=1)
    elif wait_type == "wait-for-disconnect":
        if status == "disconnected":
            return Result(stdout="")
        return Result(stderr="error: device '%s' still connected\n" % serial, exit_code=1)
    return Result(stdout="")


def handle_getprop(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args:
        return None

    # Matches `adb shell getprop [key]` or `adb getprop [key]`
    is_getprop = False
    prop_key = None
    if cmd_args[0] == "shell" and len(cmd_args) > 1 and cmd_args[1] == "getprop":
        is_getprop = True
        if len(cmd_args) > 2:
            prop_key = cmd_args[2]
    elif cmd_args[0] == "getprop":
        is_getprop = True
        if len(cmd_args) > 1:
            prop_key = cmd_args[1]

    if not is_getprop:
        return None

    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    online_err = _check_device_online(dev, serial)
    if online_err:
        return online_err

    props = dev.get("props", {})
    if prop_key:
        val = props.get(prop_key, "")
        return Result(stdout="%s\n" % val)
    else:
        lines = []
        for k in sorted(props.keys()):
            lines.append("[%s]: [%s]" % (k, props[k]))
        return Result(stdout="\n".join(lines) + ("\n" if lines else ""))


def handle_setprop(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args:
        return None

    is_setprop = False
    prop_key = None
    prop_val = ""
    if cmd_args[0] == "shell" and len(cmd_args) > 2 and cmd_args[1] == "setprop":
        is_setprop = True
        prop_key = cmd_args[2]
        if len(cmd_args) > 3:
            prop_val = " ".join(cmd_args[3:])
    elif cmd_args[0] == "setprop" and len(cmd_args) > 1:
        is_setprop = True
        prop_key = cmd_args[1]
        if len(cmd_args) > 2:
            prop_val = " ".join(cmd_args[2:])

    if not is_setprop or not prop_key:
        return None

    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    online_err = _check_device_online(dev, serial)
    if online_err:
        return online_err

    if "props" not in dev:
        dev["props"] = {}
    dev["props"][prop_key] = prop_val
    return Result(stdout="")


def handle_pm(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args:
        return None

    # Matches `adb shell pm ...` or `adb pm ...`
    actual_pm_args = []
    if cmd_args[0] == "shell" and len(cmd_args) > 1 and cmd_args[1] == "pm":
        actual_pm_args = cmd_args[2:]
    elif cmd_args[0] == "pm":
        actual_pm_args = cmd_args[1:]
    else:
        return None

    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    online_err = _check_device_online(dev, serial)
    if online_err:
        return online_err

    if not actual_pm_args:
        return Result(stdout="")

    subcmd = actual_pm_args[0]
    if subcmd == "list" and len(actual_pm_args) > 1 and actual_pm_args[1] == "packages":
        installed = dev.get("installed_packages", [])
        filter_str = None
        for a in actual_pm_args[2:]:
            if not a.startswith("-"):
                filter_str = a
                break
        if filter_str:
            installed = [p for p in installed if filter_str in p]
        lines = ["package:%s" % p for p in installed]
        return Result(stdout="\n".join(lines) + ("\n" if lines else ""))
    elif subcmd == "list" and len(actual_pm_args) > 1 and actual_pm_args[1] == "features":
        features = [
            "reqGlEsVersion=0x30002",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.faketouch",
            "android.hardware.location",
            "android.hardware.location.gps",
            "android.hardware.location.network",
            "android.hardware.microphone",
            "android.hardware.screen.landscape",
            "android.hardware.screen.portrait",
            "android.hardware.sensor.accelerometer",
            "android.hardware.sensor.compass",
            "android.hardware.touchscreen",
            "android.hardware.touchscreen.multitouch",
            "android.hardware.touchscreen.multitouch.distinct",
            "android.hardware.touchscreen.multitouch.jazzhand",
            "android.hardware.usb.accessory",
            "android.hardware.wifi",
            "android.software.backup",
            "android.software.print",
            "android.software.voice_recognizers",
            "android.software.webview",
        ]
        lines = ["feature:%s" % f for f in features]
        return Result(stdout="\n".join(lines) + "\n")
    elif subcmd == "path" and len(actual_pm_args) > 1:
        pkg = actual_pm_args[1]
        installed = dev.get("installed_packages", [])
        if pkg in installed:
            return Result(stdout="package:/data/app/%s/base.apk\n" % pkg)
        return Result(stdout="", exit_code=1)

    return Result(stdout="")


def handle_install(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args or cmd_args[0] not in ["install", "install-multiple"]:
        return None

    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    online_err = _check_device_online(dev, serial)
    if online_err:
        return online_err

    # Find the apk path in args
    apk_path = None
    for arg in cmd_args[1:]:
        if not arg.startswith("-"):
            apk_path = arg
            break

    if apk_path:
        apk_file = apk_path.split("/")[-1]
        pkg_name = apk_file.split(".apk")[0] if ".apk" in apk_file else apk_file
        if "installed_packages" not in dev:
            dev["installed_packages"] = []
        if pkg_name not in dev["installed_packages"]:
            dev["installed_packages"].append(pkg_name)

    return Result(stdout="Performing Streamed Install\nSuccess\n")


def handle_uninstall(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args or cmd_args[0] != "uninstall":
        return None

    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    online_err = _check_device_online(dev, serial)
    if online_err:
        return online_err

    pkg_name = None
    for arg in cmd_args[1:]:
        if not arg.startswith("-"):
            pkg_name = arg
            break

    if pkg_name and "installed_packages" in dev:
        if pkg_name in dev["installed_packages"]:
            dev["installed_packages"].remove(pkg_name)

    return Result(stdout="Success\n")


def handle_push_pull(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args or cmd_args[0] not in ["push", "pull"]:
        return None
    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    online_err = _check_device_online(dev, serial)
    if online_err:
        return online_err

    subcmd = cmd_args[0]
    if subcmd == "push":
        return Result(stdout="1 file pushed.\n")
    elif subcmd == "pull":
        dest_path = None
        for a in reversed(cmd_args[1:]):
            if not a.startswith("-"):
                dest_path = a
                break
        side_effects = []
        if dest_path:
            side_effects.append(WriteFile(dest_path, "mock pull content\n"))
        return Result(stdout="1 file pulled.\n", side_effects=side_effects)
    return Result(stdout="")


def handle_logcat_bugreport(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args:
        return None
    subcmd = cmd_args[0]
    if subcmd not in ["logcat", "bugreport"]:
        return None
    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    online_err = _check_device_online(dev, serial)
    if online_err:
        return online_err

    if subcmd == "logcat":
        if "-c" in cmd_args or "--clear" in cmd_args:
            return Result(stdout="")
        return Result(stdout="--------- beginning of main\n08-19 12:00:00.000  1000  1000 I MockLog: Mock logcat entry\n")
    elif subcmd == "bugreport":
        return Result(stdout="== dumpstate: 2026-08-19 12:00:00\n========================================================\n== Build: Mock Build\n========================================================\n")
    return Result(stdout="")


def handle_forward_reverse(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args or cmd_args[0] not in ["forward", "reverse", "tcpip", "connect", "disconnect"]:
        return None
    subcmd = cmd_args[0]
    if subcmd == "tcpip" and len(cmd_args) > 1:
        return Result(stdout="restarting in TCP mode port: %s\n" % cmd_args[1])
    elif subcmd == "connect" and len(cmd_args) > 1:
        target = cmd_args[1]
        devices = ctx.state.get("devices", {})
        if target not in devices:
            devices[target] = {"status": "device", "installed_packages": [], "props": {}}
            ctx.state["devices"] = devices
        return Result(stdout="connected to %s\n" % target)
    elif subcmd == "disconnect" and len(cmd_args) > 1:
        target = cmd_args[1]
        devices = ctx.state.get("devices", {})
        if target in devices:
            devices[target]["status"] = "disconnected"
        return Result(stdout="disconnected %s\n" % target)
    return Result(stdout="")


def handle_reconnect(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args or cmd_args[0] != "reconnect":
        return None
    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    dev["status"] = "device"
    return Result(stdout="reconnecting %s [ok]\n" % serial)


def handle_root(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args or cmd_args[0] not in ["root", "unroot", "remount", "reboot"]:
        return None
    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    online_err = _check_device_online(dev, serial)
    if online_err:
        return online_err

    subcmd = cmd_args[0]
    if subcmd == "root":
        return Result(stdout="restarting adbd as root\n")
    elif subcmd == "unroot":
        return Result(stdout="restarting adbd as non root\n")
    elif subcmd == "remount":
        return Result(stdout="remount succeeded\n")
    elif subcmd == "reboot":
        return Result(stdout="")
    return Result(stdout="")


def handle_generic_shell(ctx):
    target_serial, cmd_args = _parse_adb_args(ctx.args)
    if not cmd_args or cmd_args[0] != "shell":
        return None
    serial, dev, err = _resolve_device(ctx.state, target_serial)
    if err:
        return err
    online_err = _check_device_online(dev, serial)
    if online_err:
        return online_err

    if len(cmd_args) == 1:
        return Result(stdout="")

    subcmd = cmd_args[1]

    # Dumpsys battery check
    if len(cmd_args) >= 3 and subcmd == "dumpsys" and cmd_args[2] == "battery":
        return Result(stdout="""Current Battery Service state:
  AC powered: false
  USB powered: true
  Wireless powered: false
  Max charging current: 500000
  Max charging voltage: 5000000
  Charge counter: 5000000
  status: 2
  health: 2
  present: true
  level: 100
  scale: 100
  voltage: 4200
  temperature: 250
  technology: Li-ion
""")

    # Which binary check (e.g. `adb shell which pm`)
    if subcmd == "which" and len(cmd_args) > 2:
        bin_name = cmd_args[2]
        return Result(stdout="/system/bin/%s\n" % bin_name)

    # id / whoami
    if subcmd in ["id", "whoami"]:
        return Result(stdout="uid=0(root) gid=0(root) groups=0(root)\n")

    # Echo check
    if subcmd == "echo":
        return Result(stdout=" ".join(cmd_args[2:]) + "\n")

    # wm size / wm density
    if subcmd == "wm" and len(cmd_args) > 2:
        if cmd_args[2] == "size":
            return Result(stdout="Physical size: 1080x2400\n")
        elif cmd_args[2] == "density":
            return Result(stdout="Physical density: 420\n")

    # cat files
    if subcmd == "cat" and len(cmd_args) > 2:
        file_path = cmd_args[2]
        if file_path == "/proc/meminfo":
            total_mem = dev.get("props", {}).get("mock.meminfo.mem_total_kb", "8000000")
            return Result(stdout="""MemTotal:        %s kB
MemFree:         4000000 kB
MemAvailable:    6000000 kB
Buffers:          200000 kB
Cached:          2000000 kB
SwapCached:            0 kB
Active:          2500000 kB
Inactive:        1000000 kB
""" % total_mem)
        elif file_path == "/proc/cpuinfo":
            return Result(stdout="""processor\t: 0
BogoMIPS\t: 38.40
Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp
CPU implementer\t: 0x41
CPU architecture: 8
CPU variant\t: 0x1
CPU part\t: 0xd03
CPU revision\t: 4

processor\t: 1
BogoMIPS\t: 38.40
Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp
CPU implementer\t: 0x41
CPU architecture: 8
CPU variant\t: 0x1
CPU part\t: 0xd03
CPU revision\t: 4

Hardware\t: Google Panther
""")
        elif file_path == "/sys/class/net/wlan0/address":
            return Result(stdout="02:00:00:00:00:00\n")
        elif file_path == "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq":
            return Result(stdout="2850000\n")
        return Result(stdout="")

    # settings get
    if subcmd == "settings" and len(cmd_args) >= 4 and cmd_args[2] == "get":
        if "bluetooth_address" in cmd_args:
            return Result(stdout="02:00:00:00:00:00\n")
        return Result(stdout="null\n")

    # Uptime / uname
    if subcmd == "uptime":
        return Result(stdout=" 12:00:00 up 1 day,  2:30,  0 users,  load average: 0.10, 0.08, 0.05\n")
    if subcmd == "uname":
        if len(cmd_args) > 2 and cmd_args[2] == "-m":
            return Result(stdout="aarch64\n")
        return Result(stdout="Linux localhost 6.1.0 #1 SMP PREEMPT aarch64 Android\n")

    return Result(stdout="")


usmf_rules = [
    handle_version,
    handle_server,
    handle_devices,
    handle_get_state,
    handle_wait_for_device,
    handle_getprop,
    handle_setprop,
    handle_pm,
    handle_install,
    handle_uninstall,
    handle_push_pull,
    handle_logcat_bugreport,
    handle_forward_reverse,
    handle_reconnect,
    handle_root,
    handle_generic_shell,
]
