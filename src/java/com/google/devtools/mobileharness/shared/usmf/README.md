# Universal Subprocess Mocking Framework (USMF) for Host Verification in OmniLab / ATS

## 1. Executive Summary

The **Universal Subprocess Mocking Framework (USMF)** is an interactive,
state-aware, zero-dependency command-line hijacking framework designed for
high-fidelity system integration testing.

USMF virtualizes the physical dependencies at the process barrier—supporting
customizable exits, outputs, high-precision time latency, dynamic state machine
transitions, and dynamic host side-effects.

--------------------------------------------------------------------------------

## 2. Host Integration Testing Pain Points (Showcased via ADB / Emulators)

While USMF is completely generic and command-neutral, its initial architectural
driver is **OmniLab Android Testing (specifically ADB)**. Integration testing at
this layer highlights the classic bottlenecks of system-level E2E verification:

*   **Prolonged Setup Latency & Heavy H/W Footprint**: Booting full virtual
    device nodes (such as Android Emulators on Forge) takes minutes, demands
    multi-core host resources, and slows down check-in gates (Presubmit / TAP).
*   **Fragile Physical Connections**: Hardware bridges on host systems suffer
    from random disconnections, daemon failures, and permission suspensions,
    generating flaky test
    fake-negatives.

*   **Unreproducible Edge Cases**: It is functionally impossible to inject and
    verify precise transient hardware failures (e.g., USB peripheral failure,
    target-hang, bootloader lockups) with 100% repeatability.

*   **Post-Execution Assertion Blind Spot**: Existing in-memory trackers cannot
    capture calls from guest subprocesses, and parsing tool logs is
    prohibitively complex. This leaves engineers without a unified, structured
    way to assert on CLI invocation sequences.

--------------------------------------------------------------------------------

## 3. Evaluated Alternatives and Architectural Flaws

During architectural discovery, four alternative paradigms were evaluated and
rejected:

### Alternative A: Full Physical/Virtual Hardware Emulation

*   **Flaws**: Slow, heavy, resource-bound, inherently unstable, and difficult
    to simulate or reproduce failure scenarios. Moreover, it lacks structured,
    queryable verification APIs for CLI calls.

### Alternative B: Memory-Level Java Bypasses (e.g., `NoOpDevice`)

*   **Flaw - Intrusive Main Production Leak**: Abstracting devices in memory
    forces major Refactoring of Session Plugins, Schedulers, and Device
    Managers—frequently introducing test-only branching bugs.
*   **Flaw - Child Subprocess Escape**: Java-level bypasses only affect the
    immediate JVM runtime. When Java spawns external test runtimes (such as
    **Tradefed (TF)** or **Mobly**), these processes execute independent
    command-line calls to communicate with hardware. Without a system-level
    interceptor, these external calls immediately fail when no physical device
    exists.
*   **Flaw - Unverifiable Device Access Trace**: Bypassing terminal dispatches
    in Java prevents asserting on the precise terminal interaction logs the
    driver would have executed on physical hardware.

### Alternative C: Java-Level Execution Mocking (Intercepting on `AdbUtil`, `CommandExecutor`, or `ProcessBuilder`)

*   **Flaw - Subprocess Deflection**: Intercepting commands inside the parent
    JVM's `CommandExecutor` cannot monitor or mock dispatches initiated from
    independent child subprocess containers. TF or Mobly bypassed this layer
    entirely, making end-to-end integration tests impossible.

### Alternative D: Existing Stubbing Libraries & Method-Level Mock Utilities

*   **Evaluation**: We reviewed simplistic shell testing stub utilities and
    project-specific programming-layer mock handlers.
*   **Flaw - Purely Static Mocks**: Simplistic shell mock engines only support
    static text stubs. They lack sequence pooling, state history dependencies,
    or dynamically parameterized local directory file writes, causing downstream
    analysis pipelines to fail.

### Alternative E: Custom TCP Socket Mocking (Rewriting ADB Server)

*   **Evaluation**: An existing socket-level mock server is implemented in
    FakeAdbServer.kt to handle basic ADB client requests (e.g., track-devices,
    simple shell: commands).
*   **Flaw - Protocol Complexity**: Implementing the adb server daemon TCP
    socket protocol is highly complex and proprietary.
*   **Flaw - Limited Command Support**: It only parses basic commands (like
    features or simple shell stubs) and lacks support for complex commands such
    as install (which relies on exec: streaming protocol), pull, or push.
*   **Flaw - Stateless Execution**: It does not support complex, reactive state
    transitions or mutative state repositories based on incoming command
    execution sequences.
*   **Flaw - Tool Specificity**: This approach is strictly limited to ADB and
    cannot support mocking other command-line interfaces (such as AAPT or
    fastboot).

### Alternative F: Lightweight Containerized Android (e.g., Redroid, Docker-Android)

*   **Evaluation**: Running headless, lightweight Android OS instances inside
    Docker containers to act as target devices.
*   **Flaw - Sandbox Compatibility (Critical)**: `redroid` and similar
    containerized Android projects require specific kernel host modifications
    (e.g., loading Binder and Ashmem kernel modules) and Docker daemon execution
    permissions. Under Google3's strict hermetic testing environment (Borg/Forge
    sandboxes), running a Docker daemon or loading custom host kernel modules is
    strictly prohibited.
*   **Flaw - Latency & Footprint**: Although lighter than full emulators,
    booting a containerized Android system still incurs a cold-start cost
    (normally 10-30 seconds) and runtime overhead (500MB+ memory per instance),
    which conflicts with rapid pre-submit execution constraints.
*   **Flaw - Lack of Custom Fault Injection**: It is impossible to
    programmatically inject transient hardware failures (such as mid-execution
    USB connection losses or target hangs) in a predictable and repeatable
    manner.

--------------------------------------------------------------------------------

## 4. CLI Interception & Execution Model

USMF intercepts CLI binary dispatches—either by prepending a custom mockup proxy
directory to the system `PATH` or by explicitly configuring target binary path
flags (e.g., passing OmniLab's `--adb` flag)—silently redirecting target
commands to the mockup interpreter.

Once a mock binary is executed, its command parameters and env settings are
delegated to a thin wrapper proxy that invokes the `usmf_stub` binary under the
hood. The `usmf_stub` executes the Starlark-native routing pipeline:

*   **Lock & Load**: Go reads the static Starlark rules configuration, acquires
    an exclusive lock on `state/state.json.lock`, and reads the
    `state/state.json` state database.
*   **Context Preparation**: Initializes the Starlark VM, loading standard
    libraries and creating the execution context `ctx`.
*   **Match-and-Route**: The stub iterates through the registered rule handler
    functions in the global `usmf_rules` list (declared in the user-provided
    rules script) sequentially.
*   **Result Resolution**: If a rule function returns non-`None` (a `Result`
    struct), the engine treats it as matched. Any state modifications performed
    on `ctx.state` are finalized, whereas if it returns `None`, the
    modifications are discarded and rolled back. Upon match, Go commits the
    finalized
    `ctx.state` to JSON, saves it, and releases the sandbox flock database lock.
    It then executes the physical effects:
    *   **Delay (Sleep)**: If `sleep_ms > 0` is specified, the process sleeps first.
    *   **Side-Effects & Output**: After the sleep delay completes, physical
        host filesystem side effects (if any) are written, and the simulated
        `stdout`
        and `stderr` contents are written to the host stream before termination.
*   **Fallback Behavior**: If all rule functions return `None` (or if
    `usmf_rules` is empty), the mock binary silently returns a successful exit
    code of `0` with empty stdout/stderr outputs.

--------------------------------------------------------------------------------

## 5. Starlark Rules Scripting Schema

Rules are configured in Python-like syntax. The Starlark script must declare a
global list named `usmf_rules` containing one or more rule runner functions:

```python
usmf_rules = [
    handle_devices,
    handle_install,
    handle_uninstall,
    handle_getprop,
]
```

### 5.1. The Context Object (`ctx`)

Each rule function receives a single parameter representing the execution
context: `def rule_handler(ctx)`

*   `ctx.command`: The complete joined command line string.
*   `ctx.args`: A Starlark list of strings representing the CLI arguments.
*   `ctx.state`: A mutable Starlark dictionary representing the persistent state
    database.
*   `ctx.now_ms`: An integer representing the current host system time in
    milliseconds since epoch.

### 5.2. Return Outcome Contract (`Result` Type)

*   **Matched / Handled**: Returns a `Result` struct defining the simulated
    process outcomes: `Result(stdout="", stderr="", exit_code=0, sleep_ms=0,
    side_effects=[])`
    *   `side_effects` is a list of structured client-side transaction objects
        returned by the side-effect constructors (e.g., `CreateDir(...)` and
        `WriteFile(...)`).
*   **Unhandled / Skip**: Returns `None`. The routing engine skips to the next
    rule in the `usmf_rules` list.

### 5.3. Regular Expression Utility (`re_search`)

To perform pattern matching on command strings, a built-in utility
`re_search(pattern, text)` is provided:

*   **Signature**: `re_search(pattern, text)`
*   **Arguments**:
    *   `pattern`: string (strict RE2 regular expression syntax).
    *   `text`: string to search within.
*   **API Specification**:
    *   **Strict RE2 Constraint**: Regular expressions **ONLY support standard
        RE2 syntax**. Advanced syntax such as backreferences or lookaround
        assertions are completely unsupported and will result in compilation
        faults.
    *   **Return Value**:
        *   If no match is found, returns `None`.
        *   If matched, returns a `Match` object. This object is truthy.
    *   **Match Object Lookup (Safe Fallback to `None`)**:
        *   **Numeric Indexing**: `m[0]` returns the entire matched substring.
            `m[i]` (integer index `i > 0`) returns the matching substring for
            the `i`-th capture group. If `i` is out of bounds, returns `None`.
        *   **Named Group Indexing**: `m["group_name"]` returns the substring
            matched by the named capture group `(?P<group_name>...)`. If
            `"group_name"` is not found, returns `None`.

### 5.4. Structured Side-Effects APIs (`CreateDir` / `WriteFile`)

To execute dynamic host filesystem side effects safely without raw string
dictionaries, USMF exposes the following Starlark constructors:

*   `CreateDir(target_path)`: Declares a directory creation operation.
    *   `target_path`: The path of the directory to be created on the host
        filesystem.
*   `WriteFile(target_path, content)`: Declares a file writing operation.
    *   `target_path`: The path of the file to write to.
    *   `content`: The string content to write into the file.

### 5.5. Random Number Generator Utility (`rand`)

To perform stochastic simulations (such as network packet loss or execution
jitter), a built-in utility `rand()` is provided:

*   **Signature**: `rand()`
*   **Return Value**: Returns a pseudo-random float value in the range `[0.0,
    1.0)`.

--------------------------------------------------------------------------------

## 6. Java SDK Programmatic DSL Manual

> **Tip**: For Android testing in Mobile Harness, you typically do not need to
> write low-level Starlark rules manually with `UsmfBinary`. USMF includes
> out-of-the-box high-level abstractions
> ([`MockAdbController` & `MockAndroidDevice`](#out-of-the-box-android-testing-utilities))
> preloaded with standard ADB behavior. The examples in this chapter illustrate
> raw `UsmfBinary` usage; see
> [Chapter 10](#out-of-the-box-android-testing-utilities) for the recommended
> Android testing utilities.

Test hosts configure, deploy, and verify mock binaries programmatically in Java.

### Example: Stateful ADB Mocking

```java
// 1. Configure and deploy a mock ADB binary using an inline Starlark rules script.
UsmfBinary mockAdb =
    UsmfBinary.builder("adb", tmpFolder.getRoot().toPath(), "adb_sandbox")
        .setRules(
            """
            def handle_devices(ctx):
                if "devices" not in ctx.command:
                    return None
                return Result(stdout="List of devices attached\\nemulator-5554\\tdevice\\n")

            usmf_rules = [handle_devices]
            """)
        .buildAndDeploy();

// 2. Point target tool/runner to use the mock binary path.
flags.set("adb", mockAdb.getPath());

// 3. Perform assertions on the execution state history post-run...
JsonObject state = mockAdb.readState();
assertThat(state.getAsJsonObject("devices")
               .getAsJsonObject("emulator-5554")
               .getAsJsonArray("installed_packages"))
    .contains("com.google.android.youtube");
```

### Example: JUnit Lifecycle Management using `UsmfEnvironment`

```java
public class LabServerIntegrationTest {
  @Rule
  public final UsmfEnvironment usmfEnvironment = new UsmfEnvironment();

  @Test
  public void testAdbVersion() throws Exception {
    // Deploy a mock ADB binary by loading rules from a .star file path.
    UsmfBinary mockAdb =
        usmfEnvironment
            .createBinary("adb")
            .setRules(Path.of("path/to/my_rules.star"))
            .buildAndDeploy();
  }
}
```

--------------------------------------------------------------------------------

## 7. End-to-End Stateful Interception Showcase

> **Note**: The `adb_rules.star` script below is a minimal, illustrative
> showcase designed to demonstrate raw Starlark rule scripting and state machine
> mechanics. For actual Android real device testing in Mobile Harness, it is
> strongly recommended to use the out-of-the-box
> [`MockAdbController`](#out-of-the-box-android-testing-utilities) instead (see
> [Chapter 10](#out-of-the-box-android-testing-utilities)).

The following example demonstrates a fully stateful mock `adb` binary
configuration script managing device serialization, getprop properties, package
installs, and uninstalls:

```python
# adb_rules.star

DEFAULT_DEVICE = "emulator-5554"

def get_device_serial(command):
    m = re_search(r"-s\s+(?P<serial>\S+)", command)
    return m["serial"] if m else DEFAULT_DEVICE

def get_device_state(state, serial):
    if "devices" not in state:
        state["devices"] = {}
    if serial not in state["devices"]:
        state["devices"][serial] = {
            "status": "device",
            "installed_packages": [],
            "props": {
                "sys.boot_completed": "1",
                "ro.build.version.sdk": "34"
            }
        }
    return state["devices"][serial]

def handle_devices(ctx):
    if "devices" not in ctx.command:
        return None
    devices_dict = ctx.state.get("devices", {})
    if not devices_dict:
        get_device_state(ctx.state, DEFAULT_DEVICE)
        devices_dict = ctx.state["devices"]
    lines = ["List of devices attached"]
    for serial, info in devices_dict.items():
        lines.append("%s\t%s" % (serial, info.get("status", "device")))
    return Result(stdout="\n".join(lines) + "\n")

def handle_install(ctx):
    m = re_search(r"\binstall\s+(?P<apk_path>\S+)", ctx.command)
    if not m:
        return None
    serial = get_device_serial(ctx.command)
    dev_state = get_device_state(ctx.state, serial)
    apk = m["apk_path"].split("/")[-1]
    if apk not in dev_state["installed_packages"]:
        dev_state["installed_packages"].append(apk)
    return Result(stdout="Success\n")

def handle_uninstall(ctx):
    m = re_search(r"\buninstall\s+(?P<pkg>\S+)", ctx.command)
    if not m:
        return None
    serial = get_device_serial(ctx.command)
    dev_state = get_device_state(ctx.state, serial)
    pkg = m["pkg"]
    if pkg in dev_state["installed_packages"]:
        dev_state["installed_packages"].remove(pkg)
    return Result(stdout="Success\n")

def handle_getprop(ctx):
    m = re_search(r"\bshell\s+getprop\s+(?P<prop>\S+)", ctx.command)
    if not m:
        return None
    serial = get_device_serial(ctx.command)
    dev_state = get_device_state(ctx.state, serial)
    prop = m["prop"]
    val = dev_state.get("props", {}).get(prop, "")
    return Result(stdout="%s\n" % val)

usmf_rules = [
    handle_devices,
    handle_install,
    handle_uninstall,
    handle_getprop,
]
```

--------------------------------------------------------------------------------

## 8. Sandbox Directory Layout Structure

To prevent resource collisions across concurrent runs, USMF uses isolated
sandbox topologies:

```
<parent_dir>/<sandbox_dir>/      (Unique per-mock sandbox path)
├── bin/
│   ├── <mock_binary>            (Mock command execution shell wrapper proxy, e.g., "adb")
│   └── usmf_stub                (The Go-compiled mock runner bootloader)
├── rules/
│   └── rules.star               (The Starlark rule functions config module)
├── logs/
│   └── history_*.json           (Chronological transaction history logs)
└── state/
    └── state.json               (Active file-based key-value state database file)
```

--------------------------------------------------------------------------------

## 9. Execution Audit Logs (`history_<uuid>.json`)

Every execution log records metadata detailing inputs, outputs, resolved rules,
and diagnostic traces:

```json
{
  "args": ["shell", "getprop", "sys.boot_completed"],
  "status": "FINISHED",
  "start_time_ms": 1718000000000,
  "rule_name": "handle_getprop",
  "result": {
    "exit_code": 0,
    "stdout": "1\n",
    "stderr": "",
    "end_time_ms": 1718000000002
  }
}
```

--------------------------------------------------------------------------------

<a id="out-of-the-box-android-testing-utilities"></a>

## 10. Out-of-the-Box Android Testing Utilities (`MockAdbController` & `MockAndroidDevice`)

While USMF is a generic framework capable of mocking any CLI tool via Starlark
rules, the vast majority of Mobile Harness and ATS integration tests interact
with Android devices via `adb`.

To eliminate repetitive rule scripting, USMF provides a high-level,
batteries-included Android testing suite under package
`com.google.devtools.mobileharness.shared.usmf.builtin.adb`:

*   **`MockAdbController`**: High-level test controller managing multi-device
    topologies, runtime connection lifecycles (online/offline/disconnected),
    system property mutations (`getprop`/`setprop`), and package installations.
*   **`MockAndroidDevice`**: Immutable model representing an Android device,
    including serial number, `DeviceStatus`, system properties map, and
    installed packages set, with out-of-the-box hardware presets (`pixel7`,
    `defaultDevice`).
*   **`mock_adb_rules.star`**: Standard embedded Starlark rule suite supporting
    over 20 common `adb` subcommands and options out-of-the-box.

### 10.1. Standard Starlark Rule Suite (`mock_adb_rules.star`)

USMF bundles `mock_adb_rules.star` directly into the Java classpath, providing
instant, zero-configuration support for the following ADB command categories:

| Command Category        | Supported Commands &     | Simulated Behavior      |
:                         : Arguments                :                         :
| :---------------------- | :----------------------- | :---------------------- |
| **Daemon & Version**    | `adb version`, `adb      | Returns Android Debug   |
:                         : --version`,              : Bridge version strings; :
:                         : `start-server`,          : no-op server life       :
:                         : `kill-server`            : cycle.                  :
| **Device Discovery**    | `adb devices`, `adb      | Formats attached        |
:                         : devices -l`, `adb        : devices dynamically     :
:                         : get-state`               : based on active state;  :
:                         :                          : filters out             :
:                         :                          : disconnected devices;   :
:                         :                          : populates               :
:                         :                          : model/product/transport :
:                         :                          : ID for `-l`.            :
| **Synchronization**     | `adb wait-for-device`,   | Validates               |
:                         : `adb                     : online/disconnected     :
:                         : wait-for-disconnect`,    : state; returns exit     :
:                         : `wait-for-local-device`, : code `0` when satisfied :
:                         : etc.                     : or error code `1` if    :
:                         :                          : condition is not met.   :
| **System Properties**   | `adb shell getprop       | Queries single property |
:                         : [key]`, `adb getprop     : or outputs full         :
:                         : [key]`, `adb shell       : formatted `[key]\:      :
:                         : setprop <key> <val>`     : [val]` list; updates    :
:                         :                          : state database          :
:                         :                          : dynamically upon        :
:                         :                          : `setprop`.              :
| **Package Management**  | `adb shell pm list       | Dynamically tracks      |
:                         : packages [-f]`, `adb     : installed packages in   :
:                         : shell pm path <pkg>`,    : state; simulates        :
:                         : `adb install [-r]        : streaming APK install   :
:                         : <path>`, `adb uninstall  : and uninstall           :
:                         : <pkg>`                   : sequences.              :
| **Filesystem & IO**     | `adb push <local>        | Simulates file          |
:                         : <remote>`, `adb pull     : transfer; writes mock   :
:                         : <remote> <local>`        : file content to host    :
:                         :                          : path on `pull` via USMF :
:                         :                          : `WriteFile`             :
:                         :                          : side-effect.            :
| **Diagnostics & Logs**  | `adb logcat`, `adb       | Returns realistic       |
:                         : bugreport`               : logcat buffers and      :
:                         :                          : dumpstate headers.      :
| **Port & Network**      | `adb forward`, `adb      | Manages port forwarding |
:                         : reverse`, `adb tcpip     : registrations and TCP   :
:                         : <port>`, `adb connect    : network device          :
:                         : <host>`, `adb disconnect : connectivity.           :
:                         : <host>`                  :                         :
| **Privileges & System** | `adb root`, `adb         | Handles adbd privilege  |
:                         : unroot`, `adb remount`,  : switching and device    :
:                         : `adb reboot`, `adb       : reconnection back to    :
:                         : reconnect`               : `device` state.         :
| **Generic Shell**       | `adb shell <cmd>`        | Returns realistic       |
:                         : (`dumpsys battery`,      : outputs for common      :
:                         : `which`, `id`, `whoami`, : diagnostic shell        :
:                         : `echo`, `wm size`, `wm   : queries.                :
:                         : density`, `uptime`,      :                         :
:                         : `uname`)                 :                         :

### 10.2. Android Device Modeling with `MockAndroidDevice`

`MockAndroidDevice` represents an individual device instance in the mock sandbox
state.

#### Device Status Lifecycle

A device's connection and authorization state is controlled via `DeviceStatus`:

*   `DeviceStatus.DEVICE` (`"device"`): Device is online, authorized, and
    accepting commands.
*   `DeviceStatus.OFFLINE` (`"offline"`): Device is detected by `adb devices`
    but commands fail with `error: device offline`.
*   `DeviceStatus.UNAUTHORIZED` (`"unauthorized"`): Device is unauthorized;
    commands fail with `error: device unauthorized`.
*   `DeviceStatus.DISCONNECTED` (`"disconnected"`): Device is completely
    detached from the host; omitted from `adb devices` and target calls fail
    with `error: device '<serial>' not found`.

#### Built-in Device Presets

USMF provides production-realistic presets preloaded with typical Android 14
(SDK 34) userdebug system properties and core packages:

```java
// Generic Android 14 emulator (sdk_gphone64_x86_64)
MockAndroidDevice emulator = MockAndroidDevice.defaultDevice("emulator-5554");

// Google Pixel 7 (panther, Android 14, build UQ1A.240105.004)
MockAndroidDevice pixel7 = MockAndroidDevice.pixel7("emulator-5554");
```

#### Custom Device Configuration

Custom devices can be constructed fluently using `MockAndroidDevice.builder`:

```java
MockAndroidDevice customDevice =
    MockAndroidDevice.builder("device-custom-01")
        .setStatus(DeviceStatus.DEVICE)
        .setProperty("ro.product.model", "Custom Testbed")
        .setProperty("ro.product.brand", "google")
        .setProperty("ro.build.version.sdk", "35")
        .setProperty("sys.boot_completed", "1")
        .addPackage("com.google.android.apps.messaging")
        .addPackage("com.google.android.youtube")
        .build();
```

### 10.3. High-Level Controller Management with `MockAdbController`

`MockAdbController` deploys the mock `adb` binary, preloads the Starlark rule
engine, and exposes rich programmatic APIs to manipulate the sandbox at runtime.

#### Deployment and Setup

```java
@Rule public final UsmfEnvironment usmfEnvironment = new UsmfEnvironment();
@Rule public final SetFlags flags = new SetFlags();

@Before
public void setUp() throws Exception {
  // 1. Build and deploy controller with target devices:
  MockAdbController adbController =
      MockAdbController.builder(usmfEnvironment)
          .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
          .addDevice(MockAndroidDevice.defaultDevice("emulator-5556"))
          .buildAndDeploy();

  // 2. Redirect Mobile Harness / Tradefed / host tools to the mock ADB binary:
  flags.set("adb", adbController.getAdbPath());
}
```

#### Runtime Topology & Connection Mutations

Simulate hardware unplugs, reconnects, or flakiness without touching physical
USB hubs:

```java
// Simulate pulling the USB cable:
adbController.disconnectDevice("emulator-5554");

// Simulate plugging the device back in:
adbController.reconnectDevice("emulator-5554");

// Toggle device online / offline:
adbController.setDeviceOnline("emulator-5554", false);

// Explicitly set authorization state:
adbController.setDeviceStatus("emulator-5554", DeviceStatus.UNAUTHORIZED);

// Dynamically add a new device discovered mid-test:
adbController.addDevice(MockAndroidDevice.defaultDevice("emulator-5558"));

// Remove a device permanently:
adbController.removeDevice("emulator-5558");
```

#### Runtime Property Mutations

```java
// Override or add system properties:
adbController.setDeviceProperty("emulator-5554", "ro.product.model", "Lab Pixel");
```

#### State & Invocation Audit Inspection

```java
// Query all configured devices in the active state database:
ImmutableMap<String, MockAndroidDevice> devices = adbController.getAllDevices();

// Query a single device:
Optional<MockAndroidDevice> dev = adbController.getDevice("emulator-5554");

// Read raw low-level JSON state database if needed:
JsonObject state = adbController.getUsmfBinary().readState();

// Audit all chronological command invocations executed against this mock adb:
ImmutableList<CommandInvocation> history = adbController.readCommandInvocations();
```

### 10.4. Complete End-to-End Integration Test Example

The following example demonstrates verifying a Mobile Harness multi-device
failover scenario using `MockAdbController` and `AndroidAdbInternalUtil`:

```java
@RunWith(JUnit4.class)
public final class MultiDeviceFailoverIntegrationTest {

  @Rule public final UsmfEnvironment usmfEnvironment = new UsmfEnvironment();
  @Rule public final SetFlags flags = new SetFlags();

  private AndroidAdbInternalUtil adbUtil;

  @Before
  public void setUp() {
    AdbInitializer.resetForTest();
    adbUtil = new AndroidAdbInternalUtil();
  }

  @Test
  public void testDeviceDisconnectionAndDynamicRecovery() throws Exception {
    // 1. Deploy mock ADB with two devices:
    MockAdbController controller =
        MockAdbController.builder(usmfEnvironment)
            .addDevice(MockAndroidDevice.pixel7("emulator-5554"))
            .addDevice(MockAndroidDevice.defaultDevice("emulator-5556"))
            .buildAndDeploy();

    flags.set("adb", controller.getAdbPath());

    // 2. Verify initial multi-device discovery:
    Map<String, DeviceState> devices = adbUtil.getDeviceSerialsAsMap();
    assertThat(devices)
        .containsExactly(
            "emulator-5554", DeviceState.DEVICE,
            "emulator-5556", DeviceState.DEVICE);

    // 3. Inject transient hardware disconnection:
    controller.disconnectDevice("emulator-5554");
    assertThat(adbUtil.getDeviceSerialsAsMap())
        .containsExactly("emulator-5556", DeviceState.DEVICE);

    // 4. Reconnect device and verify recovery:
    controller.reconnectDevice("emulator-5554");
    assertThat(adbUtil.getDeviceSerialsAsMap())
        .containsExactly(
            "emulator-5554", DeviceState.DEVICE,
            "emulator-5556", DeviceState.DEVICE);

    // 5. Assert on captured command invocations:
    ImmutableList<CommandInvocation> invocations = controller.readCommandInvocations();
    assertThat(invocations.stream().map(CommandInvocation::getCommand))
        .contains("devices");
  }
}
```

--------------------------------------------------------------------------------

## 11. Implementation Details

### 11.1. Double-Layer Interception: Wrapper + Go Stub

USMF relies on a shell wrapper redirection directing dispatches to the
Go-compiled `usmf_stub`. Since Go has a minor scheduler start cost (~7ms), it
maintains a low-latency process footprint suitable for bulk unit intercept
testing.

### 11.2. Rationale for File-Based IPC and JSON Database

To cross process sandboxes safely on parallel Forge/TAP nodes without port
conflicts or heavy dependencies (like gRPC runtimes), USMF uses standard
file-based synchronization locking `state.json` under `Flock`.
