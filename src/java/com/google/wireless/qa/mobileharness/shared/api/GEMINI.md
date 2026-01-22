# OmniLab (Mobile Harness) Driver/Decorator Developer Guide

This directory
(`//third_party/deviceinfra/src/java/com/google/wireless/qa/mobileharness/shared/api/`)
contains the core extension points for OmniLab: **Drivers**, **Decorators**,
**Validators**, and **Listers**.

When generating or modifying code in this directory, adhere strictly to the
following patterns and conventions.

## 1. Directory Structure & Roles

*   `driver/`: Contains core test logic. Classes must extend `BaseDriver`.
*   `decorator/`: Contains logic to wrap drivers (e.g., logging, setup). Classes
    must extend `BaseDecorator`.
*   `validator/job/`: Validates job parameters.
*   `validator/env/`: Validates device environments.
*   `lister/`: Handles test sharding.

## 2. Implementing Drivers

*   **Location:** Must be in package `...shared.api.driver`.
*   **Naming Convention:**
    *   Class name should be meaningful.
*   **Inheritance:** Must extend `BaseDriver` and implement `void run(TestInfo
    testInfo)`.
*   **Annotation:** Must use `@DriverAnnotation(help = "...")`.
*   **Constructor:** Use `@Inject`. Do not use the legacy public constructor.

    ```java
    @Inject
    YourDriver(Device device, TestInfo testInfo, OtherDeps deps) {
      super(device, testInfo);
      this.deps = deps;
    }
    ```

*   **Setting Results:**

    *   Pass: `testInfo.resultWithCause().setPass();`
    *   Fail: `testInfo.resultWithCause().setNonPassing(TestResult.FAIL, new
        MobileHarnessException(YourErrorId.SOME_SPECIFIC_ERROR, "msg"));`

## 3. Implementing Decorators

*   **Location:** Must be in package `...shared.api.decorator`.
*   **Naming Convention:** Must suffix with `Decorator` (e.g.,
    `AndroidCleanAppsDecorator`).
*   **Inheritance:** Must extend `BaseDecorator`.
*   **Logic Flow:**
    1.  Pre-processing logic.
    2.  Call `getDecorated().run(testInfo)` (Chain execution).
    3.  Post-processing logic.
*   **Annotation:** Must use `@DecoratorAnnotation(help = "...")`.
*   **Constructor:** Use `@Inject`.

    ```java
    @Inject
    YourDecorator(Driver decoratedDriver, TestInfo testInfo, Device device, OtherDeps deps) {
      super(decoratedDriver, testInfo);
      // ...
    }
    ```

## 4. Inputs (Params and Files)

Do not hardcode parameter keys. Define them as constants with annotations.

*   **Annotations:** Use `@ParamAnnotation` for string options and
    `@FileAnnotation` for file inputs.
*   **Access:** Use `testInfo.jobInfo().params().get(...)` or
    `testInfo.jobInfo().files().getSingle(...)`.
*   **Example:**

    ```java
    @ParamAnnotation(required = false, help = "...")
    public static final String PARAM_MY_OPTION = "my_option";

    // In run(): String val = testInfo.jobInfo().params().get(PARAM_MY_OPTION);
    ```

## 5. Validators

Validators are optional but highly recommended. They are found via **strict
naming reflection**.

### Job Validator (Input Validation)

*   **Path:** `.../shared/api/validator/job/`
*   **Naming:** For a driver named `[Name]Driver`, the validator should be named
    `[Name]JobValidator` (e.g., `DummyTestJobValidator` for `DummyTestDriver`).
*   **Implementation:** Extend `JobValidator`. Return a `List<String>` of error
    messages (empty if valid).

### Env Validator (Device/Environment Validation)

*   **Path:** `.../shared/api/validator/env/`
*   **Naming:** For a driver named `[Name]Driver`, the validator should be named
    `[Name]EnvValidator`.
*   **Implementation:** Extend `EnvValidator`. Throw `MobileHarnessException` if
    the device environment is invalid (e.g., wrong SDK version).

## 6. Build Rules

When creating a new class, you must define a `java_library` and add it to the
`exports` list in
`//third_party/deviceinfra/src/java/com/google/wireless/qa/mobileharness/shared/api/BUILD`.

Select the appropriate target based on where the code runs:

*   `[:shared_lab_test]`: Core Lab only.
*   `[:satellite_lab_test]`: Satellite/SLaaS Labs only.
*   `[:local_mode_test]`: Local Mode only.
*   `[:satellite_lab_and_local_mode_test]`: Satellite/SLaaS Labs and Local Mode
    only.
*   `[:shared_lab_and_satellite_lab_and_local_mode_test]`: All environments.

## 7. Best Practices & Constraints

*   **Timeouts:** When executing commands, use
    `testInfo.timer().remainingTimeJava()` to respect the job timeout.
*   **Logging:**
    *   *User-facing (Debug info for the user):*
        `testInfo.log().atInfo().log(...)`
    *   *Lab-facing (Debug info for Lab ops):* `logger.atInfo().log(...)`
    *   *Both (Recommended):* `testInfo.log().atInfo().alsoTo(logger).log(...)`
