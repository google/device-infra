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

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess.worker;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.JobLocator;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.DimensionEntry;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.ExceptionDetail;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.FileEntry;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventRequest;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventResponse;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.SkipTestExceptionDetail;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.SubTestDetail;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.TestMessageDetail;
import com.google.devtools.mobileharness.infra.controller.test.event.LocalDecoratorPostForwardEventImpl;
import com.google.devtools.mobileharness.infra.controller.test.event.LocalDecoratorPreForwardEventImpl;
import com.google.devtools.mobileharness.infra.controller.test.event.LocalDriverEndedEventImpl;
import com.google.devtools.mobileharness.infra.controller.test.event.LocalDriverStartingEventImpl;
import com.google.devtools.mobileharness.infra.controller.test.event.TestEndedEventImpl;
import com.google.devtools.mobileharness.infra.controller.test.event.TestEndingEventImpl;
import com.google.devtools.mobileharness.infra.controller.test.event.TestStartedEventImpl;
import com.google.devtools.mobileharness.infra.controller.test.event.TestStartingEventImpl;
import com.google.devtools.mobileharness.shared.util.comm.messaging.message.TestMessageInfo;
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
import com.google.wireless.qa.mobileharness.shared.controller.event.util.EventInjectionScope;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Dispatches test events to user plugin instances and computes mutation deltas. */
public final class WorkerEventDispatcher {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ImmutableList<Object> plugins;
  private final List<TestMessageDetail> outgoingMessages;
  private final WorkerSubscriberExceptionHandler exceptionHandler;
  private final EventBus eventBus;

  public WorkerEventDispatcher(List<Object> plugins) {
    this(plugins, new ArrayList<>());
  }

  public WorkerEventDispatcher(List<Object> plugins, List<TestMessageDetail> outgoingMessages) {
    this.plugins = ImmutableList.copyOf(plugins);
    this.outgoingMessages = outgoingMessages;
    this.exceptionHandler = new WorkerSubscriberExceptionHandler();
    this.eventBus = new EventBus(exceptionHandler);
    for (Object plugin : this.plugins) {
      eventBus.register(plugin);
    }
  }

  /** Dispatches an event according to {@link RunTestEventRequest} and captures returned deltas. */
  public RunTestEventResponse dispatchEvent(RunTestEventRequest request) {
    JobInfo jobInfo = LocalModelFactory.createJobInfo(request.getJobInfo());
    TestInfo testInfo;
    try {
      testInfo = LocalModelFactory.createTestInfo(jobInfo, request.getTestInfo());
    } catch (MobileHarnessException | RuntimeException e) {
      logger.atWarning().withCause(e).log("Failed to recreate TestInfo in worker");
      return RunTestEventResponse.getDefaultInstance();
    }
    Device device = LocalModelFactory.createDevice(request.getDeviceInfo());

    // Record baseline state before plugin runs
    Map<String, String> initialProperties = new LinkedHashMap<>(testInfo.properties().getAll());
    Multimap<String, String> initialDimensions =
        ImmutableMultimap.copyOf(device.info().dimensions().supported().getAll());
    TestStatus initialStatus = testInfo.status().get();
    TestResult initialResult = testInfo.result().get();
    Set<String> initialFiles = new HashSet<>(testInfo.files().getAll().values());
    Set<String> initialRemoteGenFiles = new HashSet<>(testInfo.remoteGenFiles().getAll());
    int initialWarningCount = testInfo.warnings().getAll().size();

    // Prepare injection scope for @RunnerEventScoped / @Inject
    EventInjectionScope.instance.enter();
    try {
      EventInjectionScope.instance.put(TestInfo.class, testInfo);
      EventInjectionScope.instance.put(JobInfo.class, jobInfo);
      EventInjectionScope.instance.put(Device.class, device);

      com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation legacyAllocation =
          new com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation(
              testInfo.locator(),
              new com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator(
                  device.getDeviceId()),
              device.info().dimensions().supported().getAll());
      Allocation modernAllocation =
          new Allocation(
              TestLocator.of(
                  testInfo.locator().getId(),
                  testInfo.locator().getName(),
                  JobLocator.of(jobInfo.locator().getId(), jobInfo.locator().getName())),
              DeviceLocator.of(device.getDeviceId(), LabLocator.LOCALHOST));
      DeviceInfo deviceInfoProto = DeviceInfo.newBuilder().setId(device.getDeviceId()).build();
      ImmutableMap<String, Device> localDevices = ImmutableMap.of(device.getDeviceId(), device);

      ImmutableList<Object> events =
          createEvents(
              request.getEventClassName(),
              request.hasDriverName() ? request.getDriverName() : null,
              request.hasTestMessage() ? request.getTestMessage() : null,
              testInfo,
              localDevices,
              legacyAllocation,
              modernAllocation,
              deviceInfoProto,
              device);

      for (Object event : events) {
        try {
          eventBus.post(event);
        } catch (RuntimeException e) {
          logger.atWarning().withCause(e).log(
              "Exception occurred during plugin execution of %s", request.getEventClassName());
        }
      }
    } finally {
      EventInjectionScope.instance.exit();
    }

    // Build delta response
    RunTestEventResponse.Builder responseBuilder = RunTestEventResponse.newBuilder();

    // 1. Property mutations
    Map<String, String> currentProperties = testInfo.properties().getAll();
    for (Map.Entry<String, String> entry : currentProperties.entrySet()) {
      if (!initialProperties.containsKey(entry.getKey())
          || !initialProperties.get(entry.getKey()).equals(entry.getValue())) {
        responseBuilder.putAddedProperties(entry.getKey(), entry.getValue());
      }
    }
    for (String initialKey : initialProperties.keySet()) {
      if (!currentProperties.containsKey(initialKey)) {
        responseBuilder.addRemovedPropertyKeys(initialKey);
      }
    }

    // 2. Status & Result overrides
    if (testInfo.status().get() != initialStatus) {
      responseBuilder.setOverrideStatus(testInfo.status().get().name());
    }
    if (testInfo.result().get() != initialResult) {
      responseBuilder.setOverrideResult(testInfo.result().get().name());
    }

    // 3. New files added
    for (Map.Entry<String, String> entry : testInfo.files().getAll().entries()) {
      if (!initialFiles.contains(entry.getValue())) {
        responseBuilder.addAddedFiles(
            FileEntry.newBuilder().setTag(entry.getKey()).setPath(entry.getValue()).build());
      }
    }
    for (String remoteGenFile : testInfo.remoteGenFiles().getAll()) {
      if (!initialRemoteGenFiles.contains(remoteGenFile)) {
        responseBuilder.addAddedRemoteGenFiles(
            FileEntry.newBuilder().setPath(remoteGenFile).build());
      }
    }

    // 4. New warnings added
    List<ExceptionProto.ExceptionDetail> allWarnings = testInfo.warnings().getAll();
    for (int i = initialWarningCount; i < allWarnings.size(); i++) {
      ExceptionProto.ExceptionDetail warning = allWarnings.get(i);
      responseBuilder.addWarnings(
          ExceptionDetail.newBuilder()
              .setExceptionClass(warning.getSummary().getClassType().getClassName())
              .setMessage(warning.getSummary().getMessage())
              .build());
    }

    // 5. Device dimension mutations
    Multimap<String, String> currentDimensions = device.info().dimensions().supported().getAll();
    for (Map.Entry<String, String> entry : currentDimensions.entries()) {
      if (!initialDimensions.containsEntry(entry.getKey(), entry.getValue())) {
        responseBuilder.addAddedDimensions(
            DimensionEntry.newBuilder().setName(entry.getKey()).setValue(entry.getValue()).build());
      }
    }

    // 6. Sub-test creations
    for (TestInfo subTest : testInfo.subTests().getAll().values()) {
      responseBuilder.addAddedSubTests(
          SubTestDetail.newBuilder()
              .setSubTestId(subTest.locator().getId())
              .setSubTestName(subTest.locator().getName())
              .build());
    }

    // 7. Outgoing test messages
    synchronized (outgoingMessages) {
      if (!outgoingMessages.isEmpty()) {
        responseBuilder.addAllOutgoingTestMessages(outgoingMessages);
        outgoingMessages.clear();
      }
    }

    // 8. Check plugin exceptions (SkipTestException)
    List<Throwable> exceptions = exceptionHandler.pollExceptions();
    for (Throwable ex : exceptions) {
      if (ex instanceof SkipTestException skipTestException) {
        SkipTestExceptionDetail.Builder skipBuilder =
            SkipTestExceptionDetail.newBuilder()
                .setMessage(
                    skipTestException.getMessage() != null ? skipTestException.getMessage() : "")
                .setDesiredTestResult(skipTestException.testResult().name());
        if (skipTestException.errorId() != null) {
          skipBuilder
              .setErrorIdCode(skipTestException.errorId().code())
              .setErrorIdName(skipTestException.errorId().name())
              .setErrorType(skipTestException.errorId().type().name());
        }
        responseBuilder.setSkipTestException(skipBuilder.build());
        break;
      }
    }

    return responseBuilder.build();
  }

  private ImmutableList<Object> createEvents(
      String eventClassName,
      @Nullable String driverName,
      @Nullable TestMessageDetail testMessageDetail,
      TestInfo testInfo,
      ImmutableMap<String, Device> localDevices,
      com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation legacyAllocation,
      Allocation modernAllocation,
      DeviceInfo deviceInfoProto,
      Device device) {
    ImmutableList.Builder<Object> events = ImmutableList.builder();
    String resolvedDriverName =
        driverName != null && !driverName.isEmpty() ? driverName : "NoOpDriver";

    switch (eventClassName) {
      case "ModernTestStartingEvent":
      case "TestStartingEventImpl":
        events.add(
            new TestStartingEventImpl(
                DeviceFeature.getDefaultInstance(),
                ImmutableList.of(),
                testInfo,
                modernAllocation));
        break;
      case "LocalTestStartingEvent":
        events.add(
            new LocalTestStartingEvent(testInfo, localDevices, legacyAllocation, deviceInfoProto));
        break;
      case "TestStartingEvent":
        events.add(new TestStartingEvent(testInfo, legacyAllocation, deviceInfoProto));
        break;
      case "ModernTestStartedEvent":
      case "TestStartedEventImpl":
        events.add(
            new TestStartedEventImpl(
                DeviceFeature.getDefaultInstance(),
                ImmutableList.of(),
                testInfo,
                modernAllocation));
        break;
      case "LocalTestStartedEvent":
        events.add(
            new LocalTestStartedEvent(testInfo, localDevices, legacyAllocation, deviceInfoProto));
        break;
      case "TestStartedEvent":
        events.add(new TestStartedEvent(testInfo, legacyAllocation, deviceInfoProto));
        break;
      case "ModernTestEndingEvent":
      case "TestEndingEventImpl":
        events.add(new TestEndingEventImpl(testInfo, modernAllocation, Optional.empty()));
        break;
      case "LocalTestEndingEvent":
        events.add(
            new LocalTestEndingEvent(
                testInfo, localDevices, legacyAllocation, deviceInfoProto, null));
        break;
      case "TestEndingEvent":
        events.add(new TestEndingEvent(testInfo, legacyAllocation, deviceInfoProto, null));
        break;
      case "ModernTestEndedEvent":
      case "TestEndedEventImpl":
        events.add(new TestEndedEventImpl(testInfo, modernAllocation, Optional.empty()));
        break;
      case "LocalTestEndedEvent":
        events.add(
            new LocalTestEndedEvent(
                testInfo,
                localDevices,
                legacyAllocation,
                deviceInfoProto,
                testInfo.result().get() == TestResult.PASS,
                null));
        break;
      case "TestEndedEvent":
        events.add(
            new TestEndedEvent(
                testInfo,
                legacyAllocation,
                deviceInfoProto,
                testInfo.result().get() == TestResult.PASS,
                null));
        break;
      case "LocalDecoratorPreForwardEvent":
        events.add(
            new LocalDecoratorPreForwardEventImpl(
                resolvedDriverName,
                DeviceFeature.getDefaultInstance(),
                ImmutableList.of(),
                device,
                testInfo,
                modernAllocation));
        break;
      case "LocalDecoratorPostForwardEvent":
        events.add(
            new LocalDecoratorPostForwardEventImpl(
                resolvedDriverName,
                DeviceFeature.getDefaultInstance(),
                ImmutableList.of(),
                device,
                /* error= */ null,
                testInfo,
                modernAllocation));
        break;
      case "LocalDriverStartingEvent":
        events.add(
            new LocalDriverStartingEventImpl(
                resolvedDriverName,
                DeviceFeature.getDefaultInstance(),
                ImmutableList.of(),
                device,
                testInfo,
                modernAllocation));
        break;
      case "LocalDriverEndedEvent":
        events.add(
            new LocalDriverEndedEventImpl(
                resolvedDriverName,
                DeviceFeature.getDefaultInstance(),
                ImmutableList.of(),
                device,
                /* error= */ null,
                testInfo,
                modernAllocation));
        break;
      case "TestMessageEvent":
        if (testMessageDetail != null) {
          TestMessageInfo testMessageInfo =
              TestMessageInfo.of(
                  testMessageDetail.getRootTestId(),
                  testMessageDetail.getMessageMap(),
                  ImmutableList.copyOf(testMessageDetail.getSubTestIdChainList()),
                  /* isRemote= */ false);
          events.add(
              new TestMessageEvent(testMessageInfo, testInfo, legacyAllocation, deviceInfoProto));
        }
        break;
      default:
        logger.atWarning().log("Unrecognized event class name in worker: %s", eventClassName);
        break;
    }
    return events.build();
  }

  private static final class WorkerSubscriberExceptionHandler
      implements SubscriberExceptionHandler {
    private final List<Throwable> thrownExceptions = new ArrayList<>();

    @Override
    public void handleException(Throwable exception, SubscriberExceptionContext context) {
      logger.atWarning().withCause(exception).log(
          "Plugin subscriber [%s.%s] threw exception: %s",
          context.getSubscriber().getClass().getSimpleName(),
          context.getSubscriberMethod().getName(),
          exception.getMessage());
      synchronized (thrownExceptions) {
        thrownExceptions.add(exception);
      }
    }

    public ImmutableList<Throwable> pollExceptions() {
      synchronized (thrownExceptions) {
        ImmutableList<Throwable> result = ImmutableList.copyOf(thrownExceptions);
        thrownExceptions.clear();
        return result;
      }
    }
  }
}
