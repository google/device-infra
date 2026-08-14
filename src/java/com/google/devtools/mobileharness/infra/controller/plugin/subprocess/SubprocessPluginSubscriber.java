/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess;

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDecoratorPostForwardEvent;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDecoratorPreForwardEvent;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDriverEndedEvent;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDriverStartingEvent;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException.DesiredTestResult;
import com.google.devtools.mobileharness.api.testrunner.plugin.SubscribeEventOfAllDrivers;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.DeviceInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.JobInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.PluginWorkerServiceGrpc.PluginWorkerServiceBlockingStub;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventRequest;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventResponse;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.SkipTestExceptionDetail;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.TestInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.TestMessageDetail;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartedEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Host-side subscriber registered with the host's EventBus to proxy test lifecycle events to the
 * isolated worker subprocess.
 */
public final class SubprocessPluginSubscriber implements AutoCloseable {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Supplier<PluginWorkerServiceBlockingStub> stubSupplier;
  @Nullable private AutoCloseable closeable;

  public SubprocessPluginSubscriber(SubprocessPluginLauncher launcher) {
    this(
        () -> {
          try {
            return launcher.getStub();
          } catch (Exception e) {
            throw new IllegalStateException("Failed to get plugin worker stub", e);
          }
        },
        launcher);
  }

  public SubprocessPluginSubscriber(Supplier<PluginWorkerServiceBlockingStub> stubSupplier) {
    this(stubSupplier, null);
  }

  public SubprocessPluginSubscriber(
      Supplier<PluginWorkerServiceBlockingStub> stubSupplier, @Nullable AutoCloseable closeable) {
    this.stubSupplier = stubSupplier;
    this.closeable = closeable;
  }

  @Override
  public synchronized void close() {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception e) {
        // Ignored
      }
      closeable = null;
    }
  }

  @Subscribe
  public void onLocalTestStarting(LocalTestStartingEvent event) throws SkipTestException {
    forwardEvent(
        event.getClass().getSimpleName(),
        event.getTest(),
        TestInfoSnapshotter.snapshotDeviceInfo(event.getLocalDevice()),
        /* driverName= */ null,
        /* testMessageDetail= */ null,
        event.getLocalDevice());
  }

  @Subscribe
  public void onLocalTestStarted(LocalTestStartedEvent event) throws SkipTestException {
    forwardEvent(
        event.getClass().getSimpleName(),
        event.getTest(),
        TestInfoSnapshotter.snapshotDeviceInfo(event.getLocalDevice()),
        /* driverName= */ null,
        /* testMessageDetail= */ null,
        event.getLocalDevice());
  }

  @Subscribe
  public void onLocalTestEnding(LocalTestEndingEvent event) throws SkipTestException {
    forwardEvent(
        event.getClass().getSimpleName(),
        event.getTest(),
        TestInfoSnapshotter.snapshotDeviceInfo(event.getLocalDevice()),
        /* driverName= */ null,
        /* testMessageDetail= */ null,
        event.getLocalDevice());
  }

  @Subscribe
  public void onLocalTestEnded(LocalTestEndedEvent event) throws SkipTestException {
    try {
      forwardEvent(
          event.getClass().getSimpleName(),
          event.getTest(),
          TestInfoSnapshotter.snapshotDeviceInfo(event.getLocalDevice()),
          /* driverName= */ null,
          /* testMessageDetail= */ null,
          event.getLocalDevice());
    } finally {
      close();
    }
  }

  @Subscribe
  public void onTestStarting(TestStartingEvent event) throws SkipTestException {
    forwardEvent(
        event.getClass().getSimpleName(),
        event.getTest(),
        DeviceInfoSnapshot.getDefaultInstance(),
        null);
  }

  @Subscribe
  public void onModernTestStarting(
      com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent event)
      throws SkipTestException {
    forwardEvent(
        "ModernTestStartingEvent", event.getTest(), DeviceInfoSnapshot.getDefaultInstance(), null);
  }

  @Subscribe
  public void onTestStarted(TestStartedEvent event) throws SkipTestException {
    forwardEvent(
        event.getClass().getSimpleName(),
        event.getTest(),
        DeviceInfoSnapshot.getDefaultInstance(),
        null);
  }

  @Subscribe
  public void onModernTestStarted(
      com.google.devtools.mobileharness.api.testrunner.event.test.TestStartedEvent event)
      throws SkipTestException {
    forwardEvent(
        "ModernTestStartedEvent", event.getTest(), DeviceInfoSnapshot.getDefaultInstance(), null);
  }

  @Subscribe
  public void onTestEnding(TestEndingEvent event) throws SkipTestException {
    forwardEvent(
        event.getClass().getSimpleName(),
        event.getTest(),
        DeviceInfoSnapshot.getDefaultInstance(),
        null);
  }

  @Subscribe
  public void onModernTestEnding(
      com.google.devtools.mobileharness.api.testrunner.event.test.TestEndingEvent event)
      throws SkipTestException {
    forwardEvent(
        "ModernTestEndingEvent", event.getTest(), DeviceInfoSnapshot.getDefaultInstance(), null);
  }

  @Subscribe
  public void onTestEnded(TestEndedEvent event) throws SkipTestException {
    try {
      forwardEvent(
          event.getClass().getSimpleName(),
          event.getTest(),
          DeviceInfoSnapshot.getDefaultInstance(),
          null);
    } finally {
      close();
    }
  }

  @Subscribe
  public void onModernTestEnded(
      com.google.devtools.mobileharness.api.testrunner.event.test.TestEndedEvent event)
      throws SkipTestException {
    try {
      forwardEvent(
          "ModernTestEndedEvent", event.getTest(), DeviceInfoSnapshot.getDefaultInstance(), null);
    } finally {
      close();
    }
  }

  @SubscribeEventOfAllDrivers
  @Subscribe
  public void onLocalDriverStarting(LocalDriverStartingEvent event) throws SkipTestException {
    forwardEvent(
        "LocalDriverStartingEvent",
        event.getTest(),
        TestInfoSnapshotter.snapshotDeviceInfo(event.getDevice()),
        event.getDriverName(),
        /* testMessageDetail= */ null,
        event.getDevice());
  }

  @SubscribeEventOfAllDrivers
  @Subscribe
  public void onLocalDriverEnded(LocalDriverEndedEvent event) throws SkipTestException {
    forwardEvent(
        "LocalDriverEndedEvent",
        event.getTest(),
        TestInfoSnapshotter.snapshotDeviceInfo(event.getDevice()),
        event.getDriverName(),
        /* testMessageDetail= */ null,
        event.getDevice());
  }

  @SubscribeEventOfAllDrivers
  @Subscribe
  public void onLocalDecoratorPreForward(LocalDecoratorPreForwardEvent event)
      throws SkipTestException {
    forwardEvent(
        "LocalDecoratorPreForwardEvent",
        event.getTest(),
        TestInfoSnapshotter.snapshotDeviceInfo(event.getDevice()),
        event.getDriverName(),
        /* testMessageDetail= */ null,
        event.getDevice());
  }

  @SubscribeEventOfAllDrivers
  @Subscribe
  public void onLocalDecoratorPostForward(LocalDecoratorPostForwardEvent event)
      throws SkipTestException {
    forwardEvent(
        "LocalDecoratorPostForwardEvent",
        event.getTest(),
        TestInfoSnapshotter.snapshotDeviceInfo(event.getDevice()),
        event.getDriverName(),
        /* testMessageDetail= */ null,
        event.getDevice());
  }

  @Subscribe
  public void onTestMessage(TestMessageEvent event) throws SkipTestException {
    TestMessageDetail messageDetail =
        TestMessageDetail.newBuilder()
            .setRootTestId(event.getTestMessageInfo().rootTestId())
            .addAllSubTestIdChain(event.getTestMessageInfo().subTestIdChain())
            .putAllMessage(event.getMessage())
            .build();
    forwardEvent(
        "TestMessageEvent",
        event.getTest(),
        DeviceInfoSnapshot.getDefaultInstance(),
        /* driverName= */ null,
        messageDetail,
        /* device= */ null);
  }

  private void forwardEvent(
      String eventClassName,
      TestInfo testInfo,
      DeviceInfoSnapshot deviceInfoSnapshot,
      @Nullable String driverName)
      throws SkipTestException {
    forwardEvent(
        eventClassName,
        testInfo,
        deviceInfoSnapshot,
        driverName,
        /* testMessageDetail= */ null,
        /* device= */ null);
  }

  private void forwardEvent(
      String eventClassName,
      TestInfo testInfo,
      DeviceInfoSnapshot deviceInfoSnapshot,
      @Nullable String driverName,
      @Nullable TestMessageDetail testMessageDetail,
      @Nullable Device device)
      throws SkipTestException {
    RunTestEventResponse response = null;
    try {
      PluginWorkerServiceBlockingStub stub = stubSupplier.get();

      TestInfoSnapshot testInfoSnapshot = TestInfoSnapshotter.snapshotTestInfo(testInfo);
      JobInfoSnapshot jobInfoSnapshot = TestInfoSnapshotter.snapshotJobInfo(testInfo.jobInfo());

      RunTestEventRequest.Builder requestBuilder =
          RunTestEventRequest.newBuilder()
              .setEventClassName(eventClassName)
              .setTestInfo(testInfoSnapshot)
              .setJobInfo(jobInfoSnapshot)
              .setDeviceInfo(deviceInfoSnapshot);
      if (driverName != null) {
        requestBuilder.setDriverName(driverName);
      }
      if (testMessageDetail != null) {
        requestBuilder.setTestMessage(testMessageDetail);
      }

      response = stub.runTestEvent(requestBuilder.build());
      TestInfoDeltaApplier.applyDelta(testInfo, device, response);
    } catch (Exception e) {
      logger.atWarning().withCause(e).log(
          "Failed to forward test event %s to plugin worker subprocess", eventClassName);
    }

    if (response != null && response.hasSkipTestException()) {
      SkipTestExceptionDetail detail = response.getSkipTestException();
      DesiredTestResult desiredResult = DesiredTestResult.PASS;
      try {
        desiredResult = DesiredTestResult.valueOf(detail.getDesiredTestResult());
      } catch (IllegalArgumentException | NullPointerException e) {
        // Default to PASS
      }

      ErrorId errorId;
      if (detail.getErrorIdCode() != 0) {
        ErrorType errorType = ErrorType.UNCLASSIFIED;
        try {
          if (!detail.getErrorType().isEmpty()) {
            errorType = ErrorType.valueOf(detail.getErrorType());
          }
        } catch (IllegalArgumentException e) {
          // ignore
        }
        int code = detail.getErrorIdCode();
        String name = detail.getErrorIdName();
        ErrorType finalType = errorType;
        errorId =
            new ErrorId() {
              @Override
              public int code() {
                return code;
              }

              @Override
              public String name() {
                return name;
              }

              @Override
              public ErrorType type() {
                return finalType;
              }
            };
      } else {
        errorId = BasicErrorId.USER_PLUGIN_SKIP_TEST;
      }
      throw SkipTestException.create(detail.getMessage(), desiredResult, errorId);
    }
  }
}
