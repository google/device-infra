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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.JobLocator;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceLocator;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AbstractDeviceAllocator;
import com.google.devtools.mobileharness.infra.client.api.controller.allocation.allocator.AllocationWithStats;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceManager;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceManager.ProxyDevices;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceRequirement;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.AllocationEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

/** For managing the local device resources and allocating devices for a single job. */
public class LocalDeviceAllocator extends AbstractDeviceAllocator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final DeviceVerifier deviceVerifier;

  /** Universal scheduler for scheduling local devices for local tests. */
  private final ListenableFuture<AbstractScheduler> schedulerFuture;

  /** Allocations returned by scheduler, & haven't been retrieved by {@link #pollAllocations()}. */
  private final ConcurrentLinkedQueue<Allocation> allocations = new ConcurrentLinkedQueue<>();

  /** Handlers for allocation events of the scheduler. */
  private final AllocationEventHandler allocationEventHandler = new AllocationEventHandler();

  private final ListeningExecutorService threadPool;

  private final ProxyDeviceManager proxyDeviceManager;

  private final boolean enableProxyMode;

  public LocalDeviceAllocator(
      final JobInfo jobInfo,
      DeviceVerifier deviceVerifier,
      ListeningExecutorService threadPool,
      @Nullable ProxyDeviceManager proxyDeviceManager,
      ListenableFuture<AbstractScheduler> schedulerFuture) {
    super(jobInfo);
    this.deviceVerifier = deviceVerifier;
    this.threadPool = threadPool;
    this.proxyDeviceManager = proxyDeviceManager;
    this.schedulerFuture = schedulerFuture;
    this.enableProxyMode =
        proxyDeviceManager != null && Flags.instance().enableProxyMode.getNonNull();
  }

  @Override
  public boolean isLocal() {
    return true;
  }

  @Override
  public synchronized Optional<ExceptionDetail> setUp()
      throws MobileHarnessException, InterruptedException {
    // Currently a test's devices are assumed to be either all local or all proxied, determined
    // solely by the flag (whether a device can be proxied or provided locally is not verified).
    if (enableProxyMode) {
      // Prepares parameters to lease devices.
      JobLocator jobLocator = jobInfo.locator().toNewJobLocator();
      ImmutableMap<Integer, ProxyDeviceRequirement> deviceRequirements =
          IntStream.range(0, jobInfo.subDeviceSpecs().getSubDeviceCount())
              .boxed()
              .collect(
                  toImmutableMap(
                      identity(),
                      subDeviceIndex ->
                          ProxyDeviceRequirement.of(jobInfo.subDeviceSpecs(), subDeviceIndex)));
      ImmutableMap<TestLocator, TestInfo> tests =
          jobInfo.tests().getAll().values().stream()
              .collect(
                  toImmutableMap(testInfo -> testInfo.locator().toNewTestLocator(), identity()));

      // Leases the job's devices asynchronously.
      // ProxyDeviceManager will control the device leasing concurrency.
      try {
        proxyDeviceManager
            .leaseDevicesOfJobAsync(jobLocator, deviceRequirements, tests.keySet())
            .forEach(
                // When a test's devices have been leased, creates an allocation.
                (testLocator, proxyDevices) ->
                    addCallback(
                        proxyDevices,
                        new TestProxyDevicesCallback(tests.get(testLocator)),
                        threadPool));
      } catch (IllegalStateException e) {
        throw new MobileHarnessException(
            InfraErrorId.CLIENT_LOCAL_MODE_JOB_ALREADY_EXIST,
            "Job " + jobLocator.id() + " already exist",
            e);
      }
    } else {
      AbstractScheduler scheduler = getScheduler();
      scheduler.registerEventHandler(allocationEventHandler);
      if (!scheduler.addJob(jobInfo)) {
        throw new MobileHarnessException(
            InfraErrorId.CLIENT_LOCAL_MODE_JOB_ALREADY_EXIST,
            "Job " + jobInfo.locator().getId() + " already exist");
      }
      for (TestInfo test : jobInfo.tests().getAll().values()) {
        scheduler.addTest(test);
      }
    }
    return Optional.empty();
  }

  @Override
  public List<AllocationWithStats> pollAllocations()
      throws MobileHarnessException, InterruptedException {
    List<AllocationWithStats> results = new ArrayList<>();
    Allocation allocation;
    while ((allocation = allocations.poll()) != null) {
      // Finds the TestInfo in the current job.
      TestLocator testLocator = allocation.getTest();
      String testId = testLocator.id();
      String jobId = testLocator.jobLocator().id();
      TestInfo test = jobInfo.tests().getById(testId);

      if (enableProxyMode) {
        if (test == null) {
          jobInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      InfraErrorId.CLIENT_LOCAL_MODE_TEST_NOT_FOUND,
                      String.format("Unknown test %s of job %s in the allocation.", testId, jobId)),
                  logger);
          proxyDeviceManager.releaseDevicesOfTest(testLocator);
          continue;
        } else if (test.status().get() != TestStatus.NEW) {
          jobInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      InfraErrorId.CLIENT_LOCAL_MODE_TEST_NOT_NEW,
                      "Unexpected allocation to test with status " + test.status().get()),
                  logger);
          proxyDeviceManager.releaseDevicesOfTest(testLocator);
          continue;
        }
      } else {
        AbstractScheduler scheduler = getScheduler();
        if (test == null) {
          jobInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      InfraErrorId.CLIENT_LOCAL_MODE_TEST_NOT_FOUND,
                      String.format("Unknown test %s of job %s in the allocation.", testId, jobId)),
                  logger);
          scheduler.unallocate(
              allocation,
              // Releases the device back to IDLE.
              false,
              // Closes the test because it doesn't exist.
              true);
          continue;
        } else if (test.status().get() != TestStatus.NEW) {
          jobInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      InfraErrorId.CLIENT_LOCAL_MODE_TEST_NOT_NEW,
                      "Unexpected allocation to test with status " + test.status().get()),
                  logger);
          scheduler.unallocate(
              allocation,
              // Releases the device back to IDLE.
              false,
              // Closes the test in scheduler because it is not new and doesn't need new allocation.
              true);
          continue;
        }

        String deviceSerial = allocation.getDevice().id();
        Optional<String> verificationError = deviceVerifier.verifyDeviceForAllocation(deviceSerial);
        if (verificationError.isPresent()) {
          jobInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      InfraErrorId.CLIENT_LOCAL_MODE_DEVICE_NOT_READY, verificationError.get()),
                  logger);
          scheduler.unallocate(
              allocation,
              // Device is not active. Also removes it from scheduler.
              /* removeDevices= */ true,
              // Closes the test and adds it back to scheduler below to get a new allocation.
              /* closeTest= */ true);
          // Note that even if calling unallocate(allocation, true, false) above, it is necessary to
          // add the test back to scheduler here because the test may be removed from scheduler by
          // local device manager.
          scheduler.addTest(test);
          continue;
        }
      }

      // Marks the test as assigned. The scheduler uses cloned test objects so it won't update the
      // status of the real test.
      test.status().set(TestStatus.ASSIGNED);

      results.add(new AllocationWithStats(allocation));
    }
    return results;
  }

  @Override
  public void extraAllocation(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (enableProxyMode) {
      TestLocator testLocator = testInfo.locator().toNewTestLocator();

      // Leases the test's devices asynchronously.
      try {
        addCallback(
            proxyDeviceManager.leaseDevicesOfTestAsync(testLocator),
            new TestProxyDevicesCallback(testInfo),
            threadPool);
      } catch (IllegalStateException e) {
        throw new MobileHarnessException(
            InfraErrorId.CLIENT_LOCAL_MODE_TEST_ALREADY_EXIST,
            "Test " + testLocator + " already exists in job",
            e);
      }
    } else {
      AbstractScheduler scheduler = getScheduler();
      if (!scheduler.addTest(testInfo)) {
        throw new MobileHarnessException(
            InfraErrorId.CLIENT_LOCAL_MODE_TEST_ALREADY_EXIST,
            "Test "
                + testInfo.locator().getId()
                + " already exists in job "
                + jobInfo.locator().getId());
      }
    }
  }

  @Override
  public void releaseAllocation(Allocation allocation, TestResult testResult, boolean deviceDirty)
      throws MobileHarnessException, InterruptedException {
    if (enableProxyMode) {
      // Releases the test's devices synchronously.
      proxyDeviceManager.releaseDevicesOfTest(allocation.getTest());
    } else {
      DeviceLocator deviceLocator = allocation.getDevice();
      String deviceSerial = deviceLocator.id();
      AbstractScheduler scheduler = getScheduler();
      Optional<Boolean> deviceDirtyFromVerifier =
          deviceVerifier.getDeviceDirtyForAllocationRelease(deviceSerial);
      try {
        if (deviceDirtyFromVerifier.isPresent()) {
          deviceDirty = deviceDirtyFromVerifier.get();
        }
      } finally {
        jobInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Release device %s in scheduler, DeviceDirty=%s", deviceSerial, deviceDirty);
        scheduler.unallocate(deviceLocator, deviceDirty, true);
      }
    }
  }

  @Override
  public synchronized void tearDown() throws MobileHarnessException {
    if (enableProxyMode) {
      // Releases the job's devices synchronously.
      proxyDeviceManager.releaseDevicesOfJob(jobInfo.locator().toNewJobLocator());
    } else {
      if (!schedulerFuture.isDone()) {
        return;
      }
      AbstractScheduler scheduler = requireNonNull(getUnchecked(schedulerFuture));
      // Closes the job and changes the device back to IDLE.
      scheduler.removeJob(jobInfo.locator().getId(), false);
      scheduler.unregisterEventHandler(allocationEventHandler);
    }
  }

  /**
   * Callback invoked when either all proxied devices of a test have been leased successfully, or as
   * soon as any device fails to lease.
   */
  private class TestProxyDevicesCallback implements FutureCallback<ProxyDevices> {

    private final TestInfo testInfo;

    private TestProxyDevicesCallback(TestInfo testInfo) {
      this.testInfo = testInfo;
    }

    @Override
    public void onSuccess(ProxyDevices devices) {
      logger.atInfo().log("All proxy devices of test [%s] have been leased", testInfo.locator());

      // Adds the allocation of all proxied devices to the queue.
      allocations.add(createAllocation(devices));
    }

    @Override
    public void onFailure(Throwable t) {
      logger.atWarning().withCause(t).log(
          "Failed to proxy devices of test [%s]", testInfo.locator());
    }

    private Allocation createAllocation(ProxyDevices devices) {
      return new Allocation(
          testInfo.locator().toNewTestLocator(),
          IntStream.range(0, jobInfo.subDeviceSpecs().getSubDeviceCount())
              .boxed()
              .map(subDeviceIndex -> requireNonNull(devices.devices().get(subDeviceIndex)))
              .map(this::createDeviceLocator)
              .collect(toImmutableList()));
    }

    private DeviceLocator createDeviceLocator(Device device) {
      return DeviceLocator.of(device.getDeviceUuid(), LabLocator.LOCALHOST);
    }
  }

  private AbstractScheduler getScheduler() throws MobileHarnessException, InterruptedException {
    return MoreFutures.get(
        schedulerFuture, InfraErrorId.SCHEDULER_LOCAL_DEVICE_ALLOCATOR_SCHEDULER_INIT_ERROR);
  }

  private class AllocationEventHandler {

    @Subscribe
    private void onAllocation(AllocationEvent event) {
      Allocation allocation = event.getAllocation();
      if (allocation.getTest().jobLocator().id().equals(jobInfo.locator().getId())) {
        allocations.add(event.getAllocation());
      }
    }
  }

  /**
   * Device verifier for verifying a device allocation based on device status or getting device
   * dirty status when releasing an allocation.
   */
  public interface DeviceVerifier {

    /** Returns an error message if the device is invalid for a new created allocation. */
    Optional<String> verifyDeviceForAllocation(String deviceId);

    /** Returns whether the device is dirty (or empty for unknown) for an allocation release. */
    Optional<Boolean> getDeviceDirtyForAllocationRelease(String deviceId)
        throws InterruptedException;
  }

  /** An empty implementation for {@link DeviceVerifier}. */
  public static class EmptyDeviceVerifier implements DeviceVerifier {

    @Override
    public Optional<String> verifyDeviceForAllocation(String deviceId) {
      return Optional.empty();
    }

    @Override
    public Optional<Boolean> getDeviceDirtyForAllocationRelease(String deviceId) {
      return Optional.empty();
    }
  }
}
