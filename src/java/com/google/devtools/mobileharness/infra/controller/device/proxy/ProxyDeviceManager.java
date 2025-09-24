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

package com.google.devtools.mobileharness.infra.controller.device.proxy;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.Futures.immediateCancelledFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.Futures.whenAllComplete;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.Runnables.doNothing;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.allAsMap;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.getUnchecked;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.mobileharness.api.model.job.JobLocator;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/** Manager for leasing/releasing and managing proxied devices. */
@SuppressWarnings("Interruption")
public class ProxyDeviceManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Proxied devices of a test. */
  @AutoValue
  public abstract static class ProxyDevices {

    /** [sub_device_index, device]. */
    public abstract ImmutableMap<Integer, Device> devices();

    public static ProxyDevices of(ImmutableMap<Integer, Device> devices) {
      return new AutoValue_ProxyDeviceManager_ProxyDevices(devices);
    }
  }

  /**
   * The timeout for waiting for an interrupted device leasing operation to complete before
   * releasing the device.
   */
  private static final Duration CANCEL_LEASING_TIMEOUT = Duration.ofSeconds(10L);

  private final ListeningExecutorService threadPool;
  private final ProxyDeviceRunner.Factory proxyDeviceRunnerFactory;

  private final Map<JobLocator, Job> jobs = new ConcurrentHashMap<>();

  @Inject
  ProxyDeviceManager(
      ListeningExecutorService threadPool, ProxyDeviceRunner.Factory proxyDeviceRunnerFactory) {
    this.threadPool = threadPool;
    this.proxyDeviceRunnerFactory = proxyDeviceRunnerFactory;
  }

  /**
   * Returns proxied devices of a test.
   *
   * @throws IllegalStateException if the job or the test doesn't exist
   */
  public ListenableFuture<ProxyDevices> getDevicesOfTest(TestLocator testLocator) {
    Job job = jobs.get(testLocator.jobLocator());
    checkState(job != null, "Job [%s] does not exist", testLocator.jobLocator());
    return job.getTest(testLocator);
  }

  /**
   * Leases devices of a job asynchronously.
   *
   * <p>Leasing operations might be delayed due to the specific proxying concurrency control
   * strategy in use.
   *
   * @param deviceRequirements [sub_device_index, device_requirement]
   * @throws IllegalStateException if the job has been added
   */
  public ImmutableMap<TestLocator, ListenableFuture<ProxyDevices>> leaseDevicesOfJobAsync(
      JobLocator jobLocator,
      ImmutableMap<Integer, ProxyDeviceRequirement> deviceRequirements,
      ImmutableSet<TestLocator> testLocators,
      JobSetting jobSetting,
      Params params) {
    logger.atInfo().log("Add job [%s] with tests %s", jobLocator, testLocators);
    AtomicReference<ImmutableMap<TestLocator, ListenableFuture<ProxyDevices>>> result =
        new AtomicReference<>();
    jobs.compute(
        jobLocator,
        (locator, oldJob) -> {
          checkState(oldJob == null, "Job [%s] has already been added", jobLocator);
          Job job = new Job(jobLocator, deviceRequirements, testLocators, jobSetting, params);
          synchronized (job.tests) {
            result.set(
                job.tests.entrySet().stream()
                    .collect(
                        toImmutableMap(Entry::getKey, e -> e.getValue().getLeaseDevicesFuture())));

            // Leases devices asynchronously immediately.
            if (Flags.instance().proxyModeLeaseDevicesImmediately.getNonNull()) {
              job.tests.values().forEach(Test::leaseDevicesAsync);
            }
          }
          return job;
        });
    return result.get();
  }

  /**
   * Adds a test to the job in the manager, and leases devices of the test asynchronously.
   *
   * <p>Leasing operations might be delayed due to the specific proxying concurrency control
   * strategy in use.
   *
   * @throws IllegalStateException if the job doesn't exist, or the test has been added to the job,
   *     or {@link #releaseDevicesOfJob(JobLocator)} of the job has been called
   */
  public ListenableFuture<ProxyDevices> leaseDevicesOfTestAsync(
      TestLocator testLocator, JobSetting jobSetting, Params params) {
    Job job = jobs.get(testLocator.jobLocator());
    checkState(job != null, "Job [%s] does not exist", testLocator.jobLocator());
    return job.addTest(testLocator, jobSetting, params);
  }

  /**
   * Releases all proxied devices of the given test, and removes the test from the manager
   * synchronously.
   *
   * <p>Does nothing if the test doesn't exist.
   */
  public void releaseDevicesOfTest(TestLocator testLocator) {
    Job job = jobs.get(testLocator.jobLocator());
    if (job == null) {
      logger.atWarning().log("Job [%s] does not exist", testLocator.jobLocator());
      return;
    }
    job.releaseDevicesOfTest(testLocator);
  }

  /**
   * Releases all proxies devices of the given job, and removes the job from the manager
   * synchronously.
   *
   * <p>Does nothing if the job doesn't exist.
   */
  public void releaseDevicesOfJob(JobLocator jobLocator) {
    Job job = jobs.remove(jobLocator);
    if (job == null) {
      logger.atWarning().log("Job [%s] does not exist", jobLocator);
      return;
    }
    job.releaseDevices();
  }

  private class Job {

    private final JobLocator jobLocator;

    /** [sub_device_index, device_requirement]. */
    private final ImmutableMap<Integer, ProxyDeviceRequirement> deviceRequirements;

    @GuardedBy("itself")
    private final Map<TestLocator, Test> tests;

    /** Whether {@link #releaseDevices()} has been called. */
    @GuardedBy("tests")
    private boolean deviceReleased;

    private Job(
        JobLocator jobLocator,
        ImmutableMap<Integer, ProxyDeviceRequirement> deviceRequirements,
        ImmutableSet<TestLocator> testLocators,
        JobSetting jobSetting,
        Params params) {
      this.jobLocator = jobLocator;
      this.deviceRequirements = deviceRequirements;
      this.tests =
          testLocators.stream()
              .collect(
                  toMap(
                      identity(),
                      testLocator -> new Test(testLocator, deviceRequirements, jobSetting, params),
                      (a, b) -> a,
                      HashMap::new));
    }

    private ListenableFuture<ProxyDevices> getTest(TestLocator testLocator) {
      synchronized (tests) {
        Test test = tests.get(testLocator);
        checkState(test != null, "Test [%s] does not exist in job", testLocator);
        return test.getLeaseDevicesFuture();
      }
    }

    private ListenableFuture<ProxyDevices> addTest(
        TestLocator testLocator, JobSetting jobSetting, Params params) {
      synchronized (tests) {
        checkState(
            !deviceReleased,
            "Cannot add test [%s] because proxied devices of job have already been" + " released",
            testLocator);
        checkState(
            !tests.containsKey(testLocator),
            "Test [%s] has already been added to job",
            testLocator);
        logger.atWarning().log("Add test [%s] to job", testLocator);
        Test test = new Test(testLocator, deviceRequirements, jobSetting, params);
        tests.put(testLocator, test);

        // Lease devices immediately.
        if (Flags.instance().proxyModeLeaseDevicesImmediately.getNonNull()) {
          test.leaseDevicesAsync();
        }

        return test.getLeaseDevicesFuture();
      }
    }

    private void releaseDevicesOfTest(TestLocator testLocator) {
      Test test;
      synchronized (tests) {
        if (deviceReleased) {
          logger.atInfo().log(
              "Skip releasing proxied devices of test [%s] because its job has already"
                  + " released proxied devices",
              testLocator);
          return;
        } else {
          test = tests.remove(testLocator);
        }
      }
      if (test == null) {
        logger.atWarning().log("Test [%s] does not exist in job", testLocator);
      } else {
        test.releaseDevices();
      }
    }

    private void releaseDevices() {
      ImmutableMap<TestLocator, Test> tests;
      synchronized (this.tests) {
        deviceReleased = true;
        tests = ImmutableMap.copyOf(this.tests);
      }

      logger.atInfo().log(
          "Releasing all proxied devices of job [%s] with tests %s", jobLocator, tests.keySet());
      ImmutableList<ListenableFuture<?>> testDevicesReleasers =
          tests.values().stream()
              .map(
                  test ->
                      logFailure(
                          threadPool.submit(
                              threadRenaming(
                                  test::releaseDevices,
                                  () -> "test-devices-releaser-" + test.testLocator)),
                          Level.WARNING,
                          "Failed to release proxied devices of test [%s]",
                          test.testLocator))
              .collect(toImmutableList());
      try {
        getUnchecked(whenAllComplete(testDevicesReleasers).run(doNothing(), threadPool));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        testDevicesReleasers.forEach(
            testDevicesReleaser -> testDevicesReleaser.cancel(/* mayInterruptIfRunning= */ true));
      }
      logger.atInfo().log("Released all proxied devices of job [%s]", jobLocator);
    }
  }

  private class Test {

    private final TestLocator testLocator;

    /** [sub_device_index, proxied_device]. */
    private final ImmutableMap<Integer, ProxiedDevice> subDevices;

    private final SettableFuture<ProxyDevices> leaseDevicesFuture = SettableFuture.create();

    private Test(
        TestLocator testLocator,
        ImmutableMap<Integer, ProxyDeviceRequirement> deviceRequirements,
        JobSetting jobSetting,
        Params params) {
      this.testLocator = testLocator;
      this.subDevices =
          deviceRequirements.entrySet().stream()
              .collect(
                  toImmutableMap(
                      Entry::getKey,
                      e ->
                          new ProxiedDevice(
                              testLocator, e.getKey(), e.getValue(), jobSetting, params)));
    }

    private ListenableFuture<ProxyDevices> getLeaseDevicesFuture() {
      return leaseDevicesFuture;
    }

    private void leaseDevicesAsync() {
      logger.atInfo().log("Start leasing all proxied devices of test [%s]", testLocator);
      leaseDevicesFuture.setFuture(
          transform(
              allAsMap(
                  subDevices.entrySet().stream()
                      .collect(
                          toImmutableMap(Entry::getKey, e -> e.getValue().leaseDeviceAsync()))),
              ProxyDevices::of,
              directExecutor()));
    }

    private void releaseDevices() {
      logger.atInfo().log("Releasing all proxied devices of test [%s]", testLocator);
      ImmutableList<ListenableFuture<?>> deviceReleasers =
          subDevices.values().stream()
              .map(
                  proxiedDevice ->
                      logFailure(
                          threadPool.submit(
                              threadRenaming(
                                  proxiedDevice::releaseDevice,
                                  () -> proxiedDevice.deviceReleaserThreadName)),
                          Level.WARNING,
                          "Failed to release %s",
                          proxiedDevice.formattedDeviceLocator))
              .collect(toImmutableList());
      try {
        getUnchecked(whenAllComplete(deviceReleasers).run(doNothing(), threadPool));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        deviceReleasers.forEach(
            deviceReleaser -> deviceReleaser.cancel(/* mayInterruptIfRunning= */ true));
      }
      logger.atInfo().log("Released all proxied devices of test [%s]", testLocator);
    }
  }

  private class ProxiedDevice {

    private final ProxyDeviceRunner proxyDeviceRunner;
    private final String formattedDeviceLocator;
    private final String deviceLeaserThreadName;
    private final String deviceReleaserThreadName;
    private final Object leaseDeviceLock = new Object();

    @GuardedBy("leaseDeviceLock")
    private ListenableFuture<Device> leaseDeviceFuture;

    @GuardedBy("leaseDeviceLock")
    private boolean deviceReleased;

    private ProxiedDevice(
        TestLocator testLocator,
        int subDeviceIndex,
        ProxyDeviceRequirement deviceRequirement,
        JobSetting jobSetting,
        Params params) {
      this.formattedDeviceLocator =
          String.format("proxied device #%s of test [%s]", subDeviceIndex, testLocator);
      this.proxyDeviceRunner =
          proxyDeviceRunnerFactory.create(
              formattedDeviceLocator, deviceRequirement, testLocator, jobSetting, params);
      this.deviceLeaserThreadName =
          String.format("device-leaser-#%s-%s", subDeviceIndex, testLocator);
      this.deviceReleaserThreadName =
          String.format("device-releaser-#%s-%s", subDeviceIndex, testLocator);
    }

    private ListenableFuture<Device> leaseDeviceAsync() {
      logger.atInfo().log("Start leasing %s", formattedDeviceLocator);
      synchronized (leaseDeviceLock) {
        if (deviceReleased) {
          logger.atWarning().log(
              "Skip leasing %s because it has already been released", formattedDeviceLocator);
          return immediateCancelledFuture();
        }

        // Leases the device asynchronously.
        leaseDeviceFuture =
            threadPool.submit(
                threadRenaming(proxyDeviceRunner::leaseDevice, () -> deviceLeaserThreadName));
        return leaseDeviceFuture;
      }
    }

    private void releaseDevice() {
      logger.atInfo().log("Start releasing %s", formattedDeviceLocator);

      // Cancels the incomplete device leasing operation if any.
      ListenableFuture<Device> leaseDeviceFuture;
      synchronized (leaseDeviceLock) {
        deviceReleased = true;
        leaseDeviceFuture = this.leaseDeviceFuture;
      }
      if (leaseDeviceFuture != null && !leaseDeviceFuture.isDone()) {
        logger.atInfo().log("Cancelling leasing %s", formattedDeviceLocator);
        // Interrupts the device leasing operation.
        leaseDeviceFuture.cancel(/* mayInterruptIfRunning= */ true);

        // Waits for the device leasing operation to complete.
        try {
          leaseDeviceFuture.get(CANCEL_LEASING_TIMEOUT.toMillis(), MILLISECONDS);
          logger.atInfo().log("Cancelled leasing %s", formattedDeviceLocator);
        } catch (InterruptedException | TimeoutException e) {
          logger.atWarning().withCause(e).log(
              "Leasing %s does not complete in %s", formattedDeviceLocator, CANCEL_LEASING_TIMEOUT);
          if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
          }
        } catch (ExecutionException | CancellationException e) {
          logger.atInfo().log("Cancelled leasing %s", formattedDeviceLocator);
        }
      }

      // Releases the device.
      proxyDeviceRunner.releaseDevice();
    }
  }
}
