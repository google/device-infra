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
        [Section 7 (State Repository and AST Expressions)](#7-stateful-command-execution-state-repository-and-ast-expressions)).
*   **`CommandBehavior`**: Declares response outputs and state transitions:
    *   `"stdout"` / `"stderr"` / `"exit_code"` / `"sleep_ms"`.
    *   `"state_mutations"`: List of state mutations to execute upon rule
        activation (see
        [Section 8 (State Mutations and Operators)](#8-state-mutations-and-operators)).
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

### Example: Command Management using `UsmfEnvironment` JUnit Rule

For complex integration test suites, manual sandbox directory management and
artifact archiving can be error-prone. USMF provides the `UsmfEnvironment`
`@Rule` (JUnit 4) to automatically orchestrate the lifecycle of `UsmfBinary`
instances. It automatically detects sandboxing output directories (such as
`TEST_UNDECLARED_OUTPUTS_DIR` on TAP) and aggregates tool execution transaction
histories into a single `summary.json` file in test sponge outputs upon test
completion.

```java
public class LabServerIntegrationTest {
  // 1. Declare the environment watcher rule.
  @Rule
  public final UsmfEnvironment usmfEnvironment = new UsmfEnvironment();

  @Test
  public void testAdbVersion() throws Exception {
    // 2. Spawn a pre-configured UsmfBinary builder managed by the environment.
    UsmfBinary mockAdb =
        usmfEnvironment
            .createBinary("adb")
            .addRule(
                UsmfRule.builder()
                    .addCondition(CommandCondition.exactMatch("version"))
                    .setBehavior(
                        CommandBehavior.stdout("Android Debug Bridge version 1.0.41\n").build())
                    .build())
            .buildAndDeploy();

    // 3. Command execution takes place ...
    String adbPath = mockAdb.getPath();

    // 4. Upon test completion, UsmfEnvironment automatically cleans up files
    //    and leaves aggregated execution traces in Sandbox artifacts.
  }
}
```

--------------------------------------------------------------------------------

## 7. Stateful Command Execution, State Repository and AST Expressions

USMF supports stateful CLI mockups. Rather than returning purely static
responses, simulated commands read and mutate a shared JSON state repository
(`states/states.json`) internally scoped to the sandbox.

Instead of simple regex string replacements, USMF utilizes a robust **Expression
Evaluator** governed by an **Abstract Syntax Tree (AST)**. This allows complex,
nested condition testing and state mutations.

### 7.1. Context Scopes

USMF evaluation evaluates expressions against three decoupled, typed context
data environments (scopes):

#### 1. State Context (rooted as `#S`, mutable)

*   **Description**: Scoped state variables saved and reloaded dynamically
    across subprocess mockup runs in `states/states.json` inside the sandbox
    workspace.
*   **Supported Types**: Structured nested tree values (`Map<String, Object>`),
    primitive booleans (`Boolean`), numeric integers and doubles (`Number`),
    strings (`String`), or collection sequences (`List`/`Set`).
*   **Example**: `{"stats": {"device-abc": 10}}`. Target path
    `#S['stats']['device-abc']` evaluates to number `10`.

#### 2. Captures Context (rooted as `#C`, read-only)

*   **Description**: Read-only variables populated dynamically at command match
    runtime using named capture groups from matching command regex rules.
*   **Supported Types**: Flat string key-value attributes (`Map<String,
    String>`).
*   **Example**: Command `install app.apk` matching `.*install\s+(?P<apk>\S+)`
    extracts `#C['apk']` evaluating to `"app.apk"`.

#### 3. Variables Context (rooted as `#V`, read-only)

*   **Description**: Global, read-only translation mapping variables injected
    statically during deploy initialization (acting as key-value translation
    constants).
*   **Supported Types**: Static configurations mapping structures (`Map<String,
    Map<String, String>>`) or flat lists.
*   **Example**: Map settings like `{"pkg_map": {"foo.apk": "com.foo.app"}}`.
    Target path `#V['pkg_map']['foo.apk']` evaluates to `"com.foo.app"`.
*   **Java API Configuration**: Variables in the `#V` context are injected
    statically upon mockup deployment via the Java client API
    `UsmfBinary.Builder.setVariables(JsonObject)` (see the code example in
    Section 9).

### 7.2. Concept: ContextNodes in USMF Scopes

A **`ContextNode`** represents any evaluated variable, value, or nested
container in the USMF execution scopes. The ContextNode structure maps
dynamically to three concrete derived entities depending on their namespace
prefix root:

*   **`StateNode`**: Represents a mutable variable or cell inside the `State
    Context`. **Specifically, only StateNodes support read/write mutations
    (e.g., `set`, `plus`, `add_to_list`)**.
*   **`CaptureNode`**: Represents a read-only variable inside the `Captures
    Context`.
*   **`VariableNode`**: Represents a read-only variable inside the `Variables
    Context`.

### 7.3. Grammar Definition of Expressions

USMF supports evaluation of expressions (`u-node-exp`) and template string
substitutions (`u-string`):

##### 1. u-node-exp (USMF Node Expression)

An expression resolving to a dynamic `ContextNode` object representing a lookup
path (where only `StateNode` is mutable): `u-node-exp := ContextVariable |
u-node-exp[u-exp]`

*   **ContextVariable** → `#S` (State reservoir), `#C` (Command captures), or
    `#V` (Global configuration variables).
*   **IndexAccess** → The `[u-exp]` construct extracts child members. It allows
    infinite multi-dimensional nesting (e.g., `#S['stats'][#C['device_id']]`) or
    array index lookup (e.g., `#S['stats_list']['0']`). For sequence data
    containers (lists/tuples), USMF dynamically converts the index integer
    parameter representation.

##### 2. u-exp (USMF General Expression)

The general expression representing a retrieval value or index: `u-exp :=
StringLiteral | u-node-exp + ["?" + StringLiteral] + [":" + StringLiteral]`

*   **StringLiteral** → Raw constant string enclosed in single or double quotes
    (e.g., `'device-123'` or numeric index `'0'`). **Constant literals without
    quotes are syntax errors.**

##### 3. u-string (USMF String Interpolation)

A text template that evaluates variables and outputs them as a flattened string:
`u-string := StringLiteral | u-string + "${" + u-exp + "}" + u-string`

*   The brace operator `${}` acts as a string fold operator, stringifying the
    evaluated `u-exp` and concatenating the parts.

### 7.4. Operational Resolution Rules

USMF evaluation evaluates expressions based on their destination type
requirements:

#### 7.4.1. Resolving Expressions to Value Strings

When evaluating `u-exp` inside a `u-string` (which is the required syntax type
for `stdout`, `stderr`, mutation `value`, and all string fields inside side
effects):

*   **None-Value Propagation**: If any inner lookup term evaluates to `None`
    (representing a missing key inside a Map container), the outer brackets
    operator undergoes a logical short-circuit—propagating the `None` value up
    to the root level.
*   **Safety Defaulting (`?` Suffix)**: If the expression evaluates to `None`,
    and suffix `?` is present, it returns the fallback `StringLiteral` constant
    (e.g. `#C['device_id']?'unknown'`).
*   **Value Formatting (`:` Suffix)**: If the final resolved value is not
    `None`, and suffix `:` is present, the value is formatted using Python's
    printf-style format operator `%` (e.g., `#C['device_id']:'device-%s'`). If
    the value evaluates to `None` and no `?` is present, the formatting (`:`) is
    skipped.
*   **Evaluation Precedence (`?` before `:`)**: The coalescing operator `?`
    evaluates first to resolve any `None` values, followed by the `:` formatter.
    To force format an empty string (e.g., output `"device-"` when key is
    missing), write `#C['device_id']?'':'device-%s'`.
*   **Outermost Interpolation Boundary**: Under string interpolation (`${}`), if
    the final evaluated `u-exp` is `None`, it resolves to an empty string
    (`""`). For example, `${#C['device_id']:'device-%s'}` resolves to `""` if
    the device ID is missing, because the inner expression evaluates to `None`
    and formatting (`:`) is skipped (with the outermost `${}` final rendering
    replacing the resulting `None` with `""`). To force formatting in this case,
    explicitly coalesce to empty quotes first:
    `${#C['device_id']?'':'device-%s'}`.

#### 7.4.2. Resolving Expressions to ContextNodes

When USMF evaluates a `u-node-exp` expression, it traverses the lookup paths
recursively to resolve variables against the active contexts:

*   **None Propagation**: If any inner lookup term evaluates to `None`
    (representing a missing key or array out of bounds), the lookup
    safety-aborts and propagates `None` upward.
*   **Lazy Map Creation (Backfilling) during Mutation**: When locating a target
    `state_node` for a state mutation (e.g., `set`, `plus`, `add_to_list`,
    `add_to_set`), if any intermediate nested maps along the path are missing,
    USMF automatically initializes them as empty mutable maps (`{}`). This
    allows you to write nested states without having to pre-populate their
    parent structures. Note that this backfilling behavior **only** occurs when
    identifying the destination node for modifications (not for condition
    matching, resolving mutation values, or generating stdout/stderr).
*   **Syntactic Constraints**: Since condition and mutation targets strictly
    require the raw `u-node-exp` syntax type representing a direct node
    reference, appending null-coalescing (`?`) or formatting (`:`) operators in
    these fields is syntactically invalid and triggers a validation
    `SyntaxError`.

For low-level AST resolution rules and the exact backfilling algorithm, see
**[Section 13.4 (AST Resolution and Lazy Backfilling Algorithm)](#134-ast-resolution-and-lazy-backfilling-algorithm)**.

### 7.5. Context Mapping Rules

Rule Field                              | Syntax Type  | Evaluation Behavior                                                            | Code Sample
:-------------------------------------- | :----------- | :----------------------------------------------------------------------------- | :----------
**Condition Target (`state_node`)**     | `u-node-exp` | Evaluated under state and captures. Must be explicitly rooted in #S.           | `state_node: "#S['installed'][#C['device_id']]"`
**Mutation Target (`state_node`)**      | `u-node-exp` | Auto-creates ancestor directories/maps if they are missing at target location. | `state_node: "#S['stats'][#C['device_id']]"`
**Mutation Value (`value`)**            | `u-string`   | Template string substitution merged stream.                                    | `value: "${#V['pkg_map'][#C['apk']]}"`
**Standard Output (`stdout`/`stderr`)** | `u-string`   | Formatted and merged into standard streams.                                    | `"stdout": "Got ${#C['val']}"`

--------------------------------------------------------------------------------

## 8. State Mutations and Operators

When a rule matches, the mock binary executes a list of `state_mutations` in
transaction lock. To mutate maps or items, target `state_node` expressions are
evaluated in step to locate parent maps and index subscripts (e.g.
`#S['stats'][#C['device_id']]` locates the parent map `#S['stats']` and the
target index subscript matching the device ID).

The following mutation operators are supported at the location:

*   **`set`**: Sets/replaces the key's value with the new value.
*   **`plus`**: Increments/decrements a numeric value.
*   **`add_to_list`**: Appends the value to a list.
*   **`add_to_set`**: Appends the value to a set ensuring uniqueness.

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
        .setVariables(
            JsonParser.parseString(
                    """
                    {
                      "pkg_map": {
                        "foo.apk": "com.foo.app"
                      }
                    }
                    """)
                .getAsJsonObject())
        .addRule(
            UsmfRule.builder()
                .addCondition(
                    CommandCondition.regexMatch(
                        ".*?(?:-s\\s+(?P<device_id>\\S+))?.*install.*\\s+(?:.*/)?(?P<apk_name>[a-zA-Z0-9_]+)\\.apk"))
                .setBehavior(
                    CommandBehavior.stdout("Success\n")
                        .addStateMutation(
                            BinaryStateMutation.stateNode("#S['installed_packages'][#C['device_id']]")
                                .addToSet("${#V['pkg_map'][#C['apk_name']]}"))
                        .build())
                .build())
        .addRule(
            UsmfRule.builder()
                .addCondition(
                    CommandCondition.regexMatch(
                        ".*?(?:-s\\s+(?P<device_id>\\S+))?.*shell.*pm\\s+list\\s+packages"))
                .setBehavior(
                    CommandBehavior.stdout("${#S['installed_packages'][#C['device_id']]:'package:%s\n'}")
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
├── bin/
│   ├── <mock_binary>            (Mock command execution shell wrapper proxy, e.g., "adb")
│   └── usmf_stub.py             (The core mock rule execution stub)
├── rules/
│   └── mock_rules.json          (Expected expectation declarations matching JSON)
├── logs/
│   └── history_*.json           (Chronological transaction history trace logs)
└── states/
    └── states.json              (Active file-based key-value state database file)
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
    mock binary name is placed in the `bin/` subdirectory of the sandbox. This
    shell wrapper exports the paths of the rules file, state file, and logs path
    locally to the subprocess, then delegating implementation to `usmf_stub.py`.
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
*   **Simple and readable format**: JSON is human-readable and writable
    natively. Defining rules in JSON format (`mock_rules.json`) allows
    developers to manually inspect sandbox directories, write manual rules, or
    inspect the stub behavior immediately without needing Protobuf toolchains or
    schemas.

### 13.4. AST Resolution and Lazy Backfilling Algorithm

This section defines the internal logic used by the expression evaluator when
resolving a `u-node-exp` to a `ContextNode` reference.

When evaluating a `u-node-exp` expression node A, a boolean parameter
`isPureLeftLeaf` is passed from its parent expression node to A. If A has no
parent expression node (i.e., A is the root node of the expression),
`isPureLeftLeaf` is `false`.

Conceptually, `isPureLeftLeaf` represents whether every node along the path from
A to the root node (excluding the root node itself) is the left-side
`u-node-exp` component of a `u-node-exp[u-exp]` index access expression.

During the process of evaluating A (which involves parsing its structure,
recursively evaluating its child nodes, and computing A's value based on the
child nodes' evaluation results):

#### 1. Child Evaluation Context Propagation

When recursively evaluating a child node B of A:

*   `isPureLeftLeaf` for B is `true` if and only if **both** of the following
    conditions are met:
    *   **c1**: A's current `isPureLeftLeaf` is `true`, or A is the root node of
        the expression.
    *   **c2**: A is a `u-node-exp[u-exp]` expression, and the child node B is
        the left-side `u-node-exp` component.
*   Otherwise, `isPureLeftLeaf` for B is `false`.

#### 2. Result Resolution and Lazy Backfilling

When computing the value of A based on the evaluation results of its child
nodes:

*   If **all** of the following validation conditions are met:
    *   **c1**: A is a `u-node-exp[u-exp]` index access expression.
    *   **c2**: The left child node L and the right child node R of A both
        evaluate to non-`None` values (where the left child's result VL is an
        existing `ContextNode`, and the right child's result VR is a `string`).
        *If either child evaluates to None, the evaluation of A immediately
        short-circuits and propagates None upward.*
    *   **c3**: VL is a mutable `StateNode` structure (rather than read-only
        structures under #C or #V), and the entire expression is being evaluated
        for the purpose of **locating a target `state_node` for state mutation**
        (not for locating a `state_node` in a state condition match, resolving
        the mutation `value` in a state mutation, or evaluating the expression
        to a string such as for stdout, stderr, or side effects). *Otherwise,
        immediately propagate None upward.*
    *   **c4**: VL is a map container (e.g., dict) rather than a sequence
        container (e.g., list). *Otherwise, immediately propagate None upward.*
    *   **c5**: VL does not contain the key VR. *If VL contains key VR, it
        directly returns the existing value VL[VR].*
    *   **c6**: A's current `isPureLeftLeaf` is `true`. *Otherwise, immediately
        propagate None upward.*
*   Then, instead of propagating `None` upward, the resolver performs the
    following actions:
    *   **a1**: Modifies VL by adding a new entry with key VR and an empty
        mutable map (e.g., `{}`) as its value (making the path VL[VR] a valid
        existing `StateNode`).
    *   **a2**: Returns the newly backfilled VL[VR] Map as the evaluation result
        of A.
*   If any of conditions **c1** through **c6** are violated, the resolver does
    not perform backfilling and evaluates the node standardly (which propagates
    `None` upward if the key VR is missing).
