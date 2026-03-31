# Android TestLoop Test Spec

This is a re-implementation of UTP's
[TestLoopDriver](java/com/google/testing/platform/android/driver/testloop) as a
Mobile Harness driver. It must support the same features.

How to add new driver in MH is documented at
company/teams/omnilab/products/omnirun/developer/test_runner/add_driver_decorator.md.

## 1. UTP Implementation Analysis

The following details the original implementation of the UTP `TestLoopDriver`.
This serves as the reference for the new Mobile Harness driver.

### Overview and Purpose

The TestLoop driver runs scenarios of an app under test. This is also known as
the Firebase Test Lab Game Loop test.

### App Under Test (AUT) Requirements

The AUT is expected to declare its supported scenarios in its
`AndroidManifest.xml` using a `meta-data` tag:

```xml
<application>
  <meta-data android:name="com.google.test.loops" android:value="4" />
</application>
```

It must also configure a specific `<intent-filter>` to listen for the loop
intent:

```xml
<application>
  <intent-filter>
    <action android:name="com.google.intent.action.TEST_LOOP"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <data android:mimeType="application/javascript"/>
  </intent-filter>
</application>
```

### Inputs and Configuration

The UTP driver requires the following configuration (`TestLoopDriverProto`):

*   **`app_package`**: (Required) The package name of the app under test.
    Failure to provide this results in an `APP_PACKAGE_INVALID` error.
*   **`scenarios`**: (Required/Implicit) A list of scenarios to run.
*   **`test_timeout`**: (Optional) Max time the test can run before
    cancellation. Default is `5 minutes`.

### Execution Flow

1.  **Companion App Installation:** Before any scenario begins, the driver
    installs a companion application. The companion app does not need to be
    uninstalled.
2.  **Scenario Execution loop:** The driver iterates through the provided
    `scenarios` sequentially on a single device. For each scenario, it executes
    an `am instrument` command directed at the companion app.
3.  **Timeout Limits:** The overall `test_timeout` is enforced across the
    execution of all scenarios combined by the driver itself. If the elapsed
    time exceeds the bound:
    *   Any active scenario `am instrument` is terminated.
    *   Partial results keep flowing (the timeout is not immediately
        catastrophic).

### Outputs and Artifacts

*   **Scenario Results Output:** FTL Game Loop outputs results to
    `/sdcard/game_loop_results` on the device.

### Error Handling

*   **Instrumentation Crash/Error:** If `am instrument` for any scenario returns
    a non-successful status or an instrumentation error, the driver fails the
    test. The error message is set as a test property so it can be extracted.
*   **Early Exit:** A failure in any one scenario terminates the test
    immediately without attempting the remaining scenarios in the list.
*   **Timeouts:** Per FTL Game Loop behavior, timeout during a scenario loop
    execution does not strictly result in a Severe `Issue` crash, but test
    result post-processing determines test failure based on the crash details.

## 2. Mobile Harness Implementation

The overall execution flow will model the prior UTP setup. The differences lie
in using the Mobile Harness framework equivalents.

### Driver Configuration

*   The name of the driver is `AndroidTestLoopTest`.
*   The driver class is located in
    `third_party/deviceinfra/src/java/com/google/wireless/qa/mobileharness/shared/api/driver/`.
*   The driver's test class is located in
    `third_party/deviceinfra/src/javatests/com/google/wireless/qa/mobileharness/shared/api/driver/`.
*   The driver is a `SpecConfigable` driver. Configuration inputs are passed via
    the `AndroidTestLoopTestSpec` proto spec rather than raw job parameters. The
    fields are:
    *   **`app_package`**: Maps to UTP's `app_package`. Required.
    *   **`scenarios`**: Maps to UTP's `scenarios`. Expected as a
        comma-separated list of integers. Required.
    *   **`scenarios_timeout`**: Maps to UTP's `test_timeout`. Defaults to the
        remaining test timeout minus 30 seconds.

### Companion App

*   The companion APK is located at
    `//java/com/google/testing/platform/android/driver/testloop/companion/`.
*   The driver depends on its generated `.apk` as a resource dependency.
*   It should be installed using
    `com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller`
    (located at
    `third_party/deviceinfra/src/java/com/google/devtools/mobileharness/platform/android/lightning/apkinstaller/ApkInstaller.java`).
    If this fails, it throws a `MobileHarnessException`.

### Scenario Execution

*   To execute the loop, utilize
    `com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil`
    (located at
    `third_party/deviceinfra/src/java/com/google/devtools/mobileharness/platform/android/instrumentation/AndroidInstrumentationUtil.java`)
    rather than raw `am instrument`.
*   **Test Package:**
    `com.google.testing.platform.android.driver.testloop.companion`
*   **Runner Class:** `.TestLoopRunner`
*   **Arguments:** `packageName` (set to `app_package`) and `scenario` (set to
    `scenario_number`).

### Properties

*   The driver records the test start and end times to the `TestInfo`
    properties:
    *   `android_test_loop_test_start_epoch_ms`
    *   `android_test_loop_test_end_epoch_ms`

### Output Retrieval

*   The driver relies on the caller configuring `AndroidFilePullerDecorator` to
    pull `/sdcard/game_loop_results`.

### Error Handling

*   **Instrumentation Crash/Error:** If the instrumentation output contains
    `Exception`, `INSTRUMENTATION_FAILED`, or `INSTRUMENTATION_CODE: 0`, the
    driver throws a `MobileHarnessException` with ID
    `ANDROID_TEST_LOOP_INSTRUMENTATION_CRASH`, failing the test.
*   **Timeouts:** If the test timeout is closely approached during scenario
    execution (less than 5 seconds remaining), the driver stops executing
    further scenarios and returns early to ensure partial results can be
    processed.
