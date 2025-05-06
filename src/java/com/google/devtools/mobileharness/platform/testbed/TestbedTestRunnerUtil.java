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

package com.google.devtools.mobileharness.platform.testbed;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.Joiner;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.infra.controller.test.TestContext.TestContextCallable;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/** A utility with static helper methods for running testbed decorator-driver stacks. */
public final class TestbedTestRunnerUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Prevent instantiation of static util class
  private TestbedTestRunnerUtil() {}

  private static ListenableFuture<Void> startSubDeviceStack(
      ListeningExecutorService executor, final SubDeviceDecoratorStack stack) {
    return executor.submit(
        new TestContextCallable<Void>(
            () -> {
              TestInfo testInfo = stack.testInfo();
              testInfo.status().set(TestStatus.RUNNING);
              Driver driver = stack.decoratorStack();
              try {
                logger.atInfo().log(
                    "Starting decorator stack on device %s", driver.getDevice().getDeviceId());
                driver.run(testInfo);
                if (!testInfo.result().get().equals(TestResult.UNKNOWN)
                    && !stack.syncer().isReadyToSync()) {
                  throw new com.google.devtools.mobileharness.api.model.error
                      .MobileHarnessException(
                      ExtErrorId.TESTBED_DEVICE_DECORATOR_SETUP_ERROR,
                      "Failure occurred in decorator setup on device "
                          + driver.getDevice().getDeviceId());
                }
                return null;
              } catch (MobileHarnessException | InterruptedException e) {
                // Something failed; immediately release the main thread to handle the exception.
                logger.atSevere().log(
                    "Exception found in setting up device %s; cancelling: %s",
                    driver.getDevice().getDeviceId(), e.getMessage());
                Exception finalException;
                // TODO: Can remove when the MH exception from the driver is updated.
                if (e instanceof MobileHarnessException
                    && !(e
                        instanceof
                        com.google.devtools.mobileharness.api.model.error.MobileHarnessException)) {
                  // Convert old MobileHarnessException to new one.
                  MobileHarnessException mhException = (MobileHarnessException) e;
                  finalException =
                      new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
                          ExtErrorId.TESTBED_DEVICE_DECORATOR_SETUP_ERROR,
                          "<Device " + driver.getDevice().getDeviceId() + "> ",
                          mhException);
                } else {
                  finalException = e;
                }
                stack.syncer().preSyncError(finalException);
                throw finalException;
              }
            }));
  }

  private static void waitForBringup(Collection<SubDeviceDecoratorStack> stacks) throws Exception {
    // First pass: wait for every driver to complete setup or abort. We don't want to interrupt
    // after the first one because the others might be doing something uninterruptible like
    // flashing.
    for (SubDeviceDecoratorStack stack : stacks) {
      SubDeviceSynchronizationDriver syncer = stack.syncer();
      syncer.sync();
      Optional<Exception> syncError = syncer.error();
      if (syncError.isPresent()) {
        String serial = stack.decoratorStack().getDevice().getDeviceId();
        logger.atSevere().withCause(syncError.get()).log(
            "Error in decorator stack for device %s", serial);
      }
    }
    // Second pass: if anyone threw an exception, abort now.
    for (SubDeviceDecoratorStack stack : stacks) {
      Optional<Exception> syncError = stack.syncer().error();
      if (syncError.isPresent()) {
        throw syncError.get();
      }
    }
  }

  /**
   * Checks that a list of decorators is compatible with a subdevice
   *
   * @param subdevice the subdevice to check
   * @param decorators the list of {@link
   *     com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator} names
   * @return boolean true if the device supports all decorators
   */
  public static boolean isCompatible(Device subdevice, List<String> decorators) {
    return subdevice.getDecoratorTypes().containsAll(decorators);
  }

  public static void checkCompatible(Device subdevice, List<String> decorators)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    if (!isCompatible(subdevice, decorators)) {
      Set<String> invalidDecoratorTypes = new HashSet<>(decorators);
      invalidDecoratorTypes.removeAll(subdevice.getDecoratorTypes());
      String msg =
          String.format(
              "Cannot run subdecorator stack \"%s\" against device %s because decorators %s "
                  + "are not applicable to these device type(s): %s.",
              Joiner.on('+').join(decorators),
              subdevice.getDeviceId(),
              invalidDecoratorTypes,
              subdevice.getDeviceTypes());
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          ExtErrorId.TESTBED_DEVICE_DECORATOR_NOT_COMPATIBLE_ERROR, msg);
    }
  }

  /**
   * Runs multiple {@link SubDeviceDecoratorStack} objects in parallel
   *
   * @param testInfo The {@link TestInfo} for the entire testbed test
   * @param subDeviceStacks a Collection of {@link SubDeviceDecoratorStack}
   * @param mainDriver The driver to run synchronously at the inside of all decorator stacks
   * @throws MobileHarnessException when errors occur inside any decorator or driver
   * @throws InterruptedException when threads timeout or need to be interrupted
   */
  public static void runParallelSubDeviceStacks(
      TestInfo testInfo, Collection<SubDeviceDecoratorStack> subDeviceStacks, Driver mainDriver)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    ListeningExecutorService executor =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(subDeviceStacks.size()));
    Set<ListenableFuture<Void>> runFutures = new HashSet<>();
    for (SubDeviceDecoratorStack stack : subDeviceStacks) {
      ListenableFuture<Void> future = startSubDeviceStack(executor, stack);
      runFutures.add(future);
    }
    executor.shutdown();
    ListenableFuture<List<Void>> allFuture = Futures.allAsList(runFutures);

    // Wait for all threads to finish setup or abort.
    Exception throwException = null;
    try {
      waitForBringup(subDeviceStacks);
    } catch (Exception e) {
      throwException = e;
      for (SubDeviceDecoratorStack stack : subDeviceStacks) {
        // Set all stack errors to the thrown exception.
        if (stack.syncer().error().isEmpty()) {
          stack.syncer().postSyncError(e);
        }
      }
    }

    if (throwException == null) {
      // Everyone made it through set up. Run the rest of the driver stack.
      try {
        mainDriver.run(testInfo);
      } catch (MobileHarnessException | InterruptedException e) {
        logger.atSevere().withCause(e).log(
            "Error executing driver. Trying to tear down subdecorators.");
        throwException = e;
        for (SubDeviceDecoratorStack stack : subDeviceStacks) {
          stack.syncer().postSyncError(e);
        }
      }
    }

    // Some decorators have result dependent teardown logic (e.g., AndroidSdVideoDecorator), so
    // propagate test result to subdevice testInfos.
    // Release the threads into teardown.
    for (SubDeviceDecoratorStack stack : subDeviceStacks) {
      stack.testInfo().result().set(testInfo.result().get());
      stack.syncer().releaseSync();
    }

    // Wait for everything to die before continuing, to avoid leaving dying threads of subdevices
    // around while the main thread moves on. The timeout is high to allow for long teardown
    // processes such as bugreports and/or flashing.
    Duration remainingTestTime = testInfo.timer().remainingTimeJava();
    logger.atInfo().log(
        "Waiting up to %d seconds for subdevice decorator threads to terminate.",
        remainingTestTime.toSeconds());

    // Join all the futures. If any failed, this will throw.
    try {
      allFuture.get(remainingTestTime.toMillis(), MILLISECONDS);
    } catch (TimeoutException e) {
      MobileHarnessExceptions.rethrow(e, ExtErrorId.TESTBED_RUN_SUB_PARRALLEL_DEVICE_STACK_ERROR);
    } catch (ExecutionException e) {
      MobileHarnessExceptions.rethrow(
          e.getCause(), ExtErrorId.TESTBED_RUN_SUB_PARRALLEL_DEVICE_STACK_ERROR);
    } finally {
      allFuture.cancel(true /* mayInterruptIfRunning */);
      // We still need to wait for the futures to complete after cancelling them.
      remainingTestTime = testInfo.timer().remainingTimeJava();
      executor.awaitTermination(remainingTestTime.toMillis(), MILLISECONDS);
    }

    if (throwException != null) {
      MobileHarnessExceptions.rethrow(
          throwException, ExtErrorId.TESTBED_RUN_SUB_PARRALLEL_DEVICE_STACK_ERROR);
    }
  }
}
