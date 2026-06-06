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
    [FakeAdbServer.kt](file:///google3/java/com/google/devtools/omnilab/device/android/adb/lib/testing/FakeAdbServer.kt)
    to handle basic ADB client requests (e.g., `track-devices`, simple `shell:`
    commands).
*   **Flaw - Protocol Complexity**: Implementing the adb server daemon TCP
    socket protocol is highly complex and proprietary.
*   **Flaw - Limited Command Support**: It only parses basic commands (like
    `features` or simple shell stubs) and lacks support for complex commands
    such as `install` (which relies on `exec:` streaming protocol), `pull`, or
    `push`.
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

Once a mock binary is executed, its command parameters and active state memory
undergo the following sequence matching flow on the USMF engine:

*   **Sequential Matching**: The engine matches the incoming arguments and state
    variables sequentially against each configured `UsmfRule` in the order they
    were registered.
*   **First-Match-Win**: The evaluation halts immediately at the first matching
    rule. That rule then executes its associated behavior:
    1.  **State Mutations**: Mutates the shared state repository atomically
        (e.g., updates keys).
    2.  **Execution Sleep Latency / Output / Side Effects**: Executes simulated
        latency delays, writes to stdout/stderr streams, performs host
        side-effects (e.g. file mutations), and returns the exit code.
*   **Fallback Behavior**: If no rules match the command (or if zero rules are
    configured), the mock binary silently returns a successful exit code of `0`
    with empty stdout/stderr outputs.

--------------------------------------------------------------------------------

## 5. Declarative Mock Rules Schema (`mock_rules.json`)

The behavior of the mock binary is governed by rules defined in
`rules/mock_rules.json`.

*   **`UsmfRule`**: Holds a list of `conditions` (implicitly combined using
    **AND** logic) and a single `behavior`.
*   **`RuleCondition`**: Abstract conditional block discriminated by its
    `"type"` property:
    *   **`CommandCondition`** (`"type": "command"`): Filters CLI actions.
        *   `"match_type"`: Must be one of `"exact"`, `"prefix"`, or `"regex"`.
        *   `"expected"`: List of string parameters literally matched (for
            `"exact"` and `"prefix"`).
        *   `"regex"`: Python RE2 regular expression pattern matching the
            command arguments (for `"regex"`).
    *   **`BinaryStateCondition`** (`"type": "state"`): Filters based on active
        state variables (see
        [Section 7 (State Repository)](#7-stateful-command-execution-state-repository)).
*   **`CommandBehavior`**: Declares response outputs and state transitions:
    *   `"stdout"` / `"stderr"` / `"exit_code"` / `"sleep_ms"`.
    *   `"state_mutations"`: List of state mutations to execute upon rule
        activation (see
        [Section 7 (State Repository)](#7-stateful-command-execution-state-repository)).
    *   `"side_effects"`: List of file and directory mutations performed on the
        host environment:
        *   `"type"`: `"file"`.
        *   `"op"`: `"write_file"` or `"create_dir"`.
        *   `"target_path"`: Destination file/folder path.
        *   `"content"`: File content string.

--------------------------------------------------------------------------------

## 6. Java SDK Programmatic DSL Manual

Test authors configure, deploy, and verify mock binaries programmatically in
Java.

### Example: Static Interception (Mocking `adb devices`)

```java
// 1. Configure and deploy a mock ADB binary.
//    When "devices" is prefix-matched, return a pre-defined list of attached devices.
UsmfBinary mockAdb =
    UsmfBinary.builder("adb", tmpFolder.getRoot().toPath(), "adb_sandbox")
        .addRule(
            UsmfRule.builder()
                .addCondition(CommandCondition.prefixMatch("devices"))
                .setBehavior(
                    CommandBehavior.stdout(
                            """
                            List of devices attached
                            emulator-5554\tdevice\tproduct:sdk_gphone64_x86_64
                            """)
                        .build())
                .build())
        .buildAndDeploy();

// 2. Point target tool/runner to use the mock binary path.
flags.set("adb", mockAdb.getPath());

// 3. Perform assertions on the tool execution results ...

// 4. Assert the command invocation execution history post-run.
List<CommandInvocation> invocations = mockAdb.readCommandInvocations();
assertThat(invocations.stream().anyMatch(
    inv -> inv.getArgs().equals(ImmutableList.of("devices", "-l"))
)).isTrue();
```

--------------------------------------------------------------------------------

## 7. Stateful Command Execution & State Repository

USMF supports stateful CLI mockups. Rather than returning purely static
responses, the mock binary reads and mutates a shared key-value state repository
(`states.json`) internally scoped to the sandbox. This allows simulated commands
to store environment/device states and make subsequent command behaviors
dependent on those transitions.

### 7.1. Evaluating State Conditions

Rules can check state repository values using `BinaryStateCondition` (supporting
comparison operators: `"eq"`, `"gt"`, `"gte"`, `"lt"`, `"lte"`, or
`"contains"`). A rule can be configured to match only when a specific key in the
state repository meets these criteria.

### 7.2. Mutating States

When a rule is matched, the mock binary executes `state_mutations` atomically to
modify keys in the state repository.

To prevent race conditions with concurrent commands under simulated hardware
latency, all state mutations are performed *before* writing stdout/stderr
outputs, execution sleep latency (`sleep_ms`), or executing host side-effects
(`side_effects`).

The supported mutation operators are:

*   **`set`**: Replaces the key's value with the new value.
*   **`plus`**: Increments/decrements a numeric key's value by the given amount.
*   **`add_to_list`**: Appends the value to the end of a state list.
*   **`add_to_set`**: Appends the value to a state list only if it does not
    already exist (ensures uniqueness).

--------------------------------------------------------------------------------

## 8. Dynamic Parameter Interpolation & Formatter Templates

USMF supports regex named capture groups and active state variables to
dynamically format outputs, state mutations, and host side effects.

### 8.1. Interpolation Syntax (`${prefix:key[:'format']}`)

Any attribute inside `CommandBehavior` (stdout, stderr, state mutations, and
side effects) supports text substitution placeholders matching:

*   `${@C:key}`: Variable from regex named capture groups.
*   `${@S:key}`: Variable from shared state variables.
*   `${prefix:key:'format_string'}`: Formats the value using standard C-style
    print formats.

#### Available Variables (Interpolation Context)

The interpolation engine resolves placeholder keys (`${prefix:key}`) against:

1.  **Regex Capture Groups (`@C:`)**: Named capture groups defined in the rule's
    active command regex pattern (e.g., `(?P<pkg>\S+)` makes `${@C:pkg}`
    available).
2.  **Shared State Memory (`@S:`)**: Persistent state keys stored in
    `states.json` (e.g., if the state has `"ratio": 3.14`, then `${@S:ratio}`
    resolves to `3.14`).

The type prefix (`@C:` or `@S:`) is strictly mandatory. Placeholders without a
valid prefix are not resolved and will remain unmodified.

#### Key System Attributes

*   **Inline Formatting Templates**: Formatting formats (e.g., `%.2f` for float
    precision or `%d` for integers) are specified directly within the tag, and
    must be enclosed in single quotes `'` or double quotes `"`.
*   **Collection Rendering (Auto-Detection)**: If the resolved key value is a
    collection (like a list/set/tuple), the template is evaluated iteratively
    for each item and concatenated. If it is a primitive single value, it is
    formatted exactly once.
*   **Nested Resolution**: Placeholders can be nested (up to 5 layers deep),
    solving dynamic key lookups at runtime (e.g.,
    `${@S:installed_packages_${@C:device_id}}` dynamically resolves the device
    serial first before querying the package list).
*   **Safety Fallback**: Any unresolved variable placeholder safely evaluates to
    an empty string `""` at runtime.

--------------------------------------------------------------------------------

## 9. End-to-End Stateful Interception Showcase

The following example demonstrates a stateful mock `adb` binary. When installing
an APK, it extracts the target `device_id` and `apk_name` from the command line
and writes them to the state repository. When listing packages, it extracts the
`device_id`, queries the repository for the corresponding installed packages,
formats the list, and outputs the results to stdout:

```java
// Configure and deploy mock ADB with stateful rules to track installed packages per device:
UsmfBinary mockAdb =
    UsmfBinary.builder("adb", tmpFolder.getRoot().toPath(), "adb_sandbox")
        .addRule(
            UsmfRule.builder()
                .addCondition(
                    CommandCondition.regexMatch(
                        ".*(?:-s\\s+(?P<device_id>\\S+))?.*install.*\\s+(?:.*/)?(?P<apk_name>[a-zA-Z0-9_]+)\\.apk"))
                .setBehavior(
                    CommandBehavior.stdout("Success\n")
                        .addStateMutation(
                            // Regex capture and state variable interpolation
                            BinaryStateMutation.key("installed_packages_${@C:device_id}")
                                .addToSet("com.foo.${@C:apk_name}"))
                        .build())
                .build())
        .addRule(
            UsmfRule.builder()
                .addCondition(
                    CommandCondition.regexMatch(
                        ".*(?:-s\\s+(?P<device_id>\\S+))?.*shell.*pm\\s+list\\s+packages"))
                .setBehavior(
                    // Collection elements loop formatting rendering
                    CommandBehavior.stdout("${@S:installed_packages_${@C:device_id}:'package:%s\n'}")
                        .build())
                .build())
        .buildAndDeploy();
```

--------------------------------------------------------------------------------

## 10. Sandbox Directory Layout Structure

To support mocking multiple binaries simultaneously and allow those mock
binaries to be invoked concurrently by multiple threads or guest subprocesses
without resource contention, USMF implements a flat, self-contained workspace
topology:

```
<parent_dir>/<sandbox_dir>/      (Unique per-mock sandbox path)
├── <mock_binary>                (Mock command execution shell wrapper proxy, e.g., "adb")
├── bin/
│   └── usmf_stub.py             (The core mock rule execution stub)
├── rules/
│   └── mock_rules.json          (Expected expectation declarations matching JSON)
├── logs/
│   └── history_*.json           (Chronological transaction history trace logs)
└── states/
    └── states.json              (Active shared-memory key-value state database file)
```

--------------------------------------------------------------------------------

## 11. Execution Audit Logs (`history_<uuid>.json`)

Every execution run logs a transaction trace file containing call parameters,
timestamps, status updates, and runtime diagnostics:

```json
{
  "args": ["shell", "getprop", "sys.boot_completed"],
  "status": "FINISHED",
  "start_time_ms": 1718000000000,
  "result": {
    "exit_code": 0,
    "stdout": "1\n",
    "stderr": "",
    "end_time_ms": 1718000000500
  }
}
```

## 12. Future Roadmap: Out-of-the-Box Mock Binaries

Rather than requiring test authors to manually draft raw tool matching rules
from scratch, a dedicated library of **Out-of-the-Box Mock Binaries** will be
packaged to generate custom `UsmfBinary` instances (pre-populated with rich
rules to emulate real command interfaces, such as a fully-featured mock `adb`
binary):

```java
UsmfBinary mockAdb = AdbMock.builder(parentDir, "adb_sandbox")
    .setDeviceSerial("emulator-5554")
    .setApiVersion(34)
    .buildAndDeploy();
```

This populates the sandbox with nested rules and states (emulating behaviors
such as boot completion check-ins, package listings, and device serial queries)
to mimic real CLI commands without physical infrastructure nodes.

--------------------------------------------------------------------------------

## 13. Implementation Details

This section outlines internal implementation designs, evaluations of alternate
paradigms, and structural justifications.

### 13.1. Redirection Architecture: Thin Wrapper + Python Stub

USMF deploys a two-stage hijacking mechanism:

1.  **Shell Wrapper**: A thin, platform-specific shell script representing the
    mock binary name is placed in `/bin`. This shell wrapper exports the paths
    of the rules file, state file, and logs path locally to the subprocess, then
    delegating implementation to `usmf_stub.py`.
2.  **Generic python stub (`usmf_stub.py`)**: Intercepts the call, loads the
    JSON config, evaluates matching conditions concurrently, formats output
    fields, performs file writes (state/side effect), and atomically outputs
    results. Using Python ensures zero compilation requirements and instant
    execution across any Linux/Mac test agent.

#### Drawbacks of Alternatives:

*   **Compiled Binaries (C++/Go/Rust)**: Cross-compiling and maintaining
    architecture-specific binaries (e.g., x86_64 vs. arm64 for Linux/macOS) in
    the repository introduces significant build system complexity and packaging
    overhead.
*   **Java-based Wrapper**: Spawning a JVM instance for every hijacked CLI
    command adds hundreds of milliseconds of startup latency. This is
    unacceptable, as host-level tests often execute CLI tools hundreds of times
    in loops.

### 13.2. Rationale for File-Based IPC

To transit states and collect records between the Java JVM runner and
subprocesses (like TF/Mobly), USMF uses **File-Based Communication** rather than
Socket/HTTP/gRPC/Proto:

*   **Port-Allocation Conflicts**: On parallel TAP nodes, TCP/HTTP servers hit
    port collisions. Files target isolated locations natively, preventing
    collisions.
*   **Dependency-Free Integration**: Redirection binaries (like TF or wrapper
    commands) can be of any language. Reading/writing files via JSON is natively
    available in shell, Python, Go, and C++ out-of-the-box without pulling heavy
    gRPC client runtimes.

### 13.3. Rationale for JSON rules and State Files

Rather than using binary Protocol Buffers (Protos) or TextProto, USMF uses JSON
for rules and state tracking due to the following reasons:

*   **Protobuf Dependency Issues**: To keep USMF as a zero-dependency utility
    across diverse host environments, we avoid dependencies on Python Protobuf
    runtimes. Using Protobuf would require compiling Python Proto classes and
    importing the `protobuf` library, which is highly prone to version conflicts
    with the test runner's Python environment and requires complex environment
    setup on the test agent. The JSON parser is built natively into Python.
*   **Simplicity and Readability**: JSON is human-readable and writable
    natively. Defining rules in JSON format (`mock_rules.json`) allows
    developers to manually inspect sandbox directories, write manual rules, or
    inspect the stub behavior immediately without needing Protobuf toolchains or
    schemas.
