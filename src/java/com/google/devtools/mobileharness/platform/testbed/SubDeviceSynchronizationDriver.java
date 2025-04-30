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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.BaseDriver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * A {@link Driver} which doesn't execute any actions, but instead blocks (twice) until released.
 *
 * <p>This driver is used as a placeholder to separate the pre-driver, driver execution, and
 * post-driver steps of decorator stacks executed on one out of many subdevices, when there is a
 * single main driver (e.g., {@link
 * com.google.wireless.qa.mobileharness.shared.api.driver.MoblyTest}).
 *
 * <p>It allows the pre-driver portions of decorator stacks for each subdevice to be performed in
 * parallel, then blocks so that the main driver can be run synchronously only when all subdevices
 * are ready, then after the driver is done finally releases to run parallel post-driver decorators
 * again.
 */
public class SubDeviceSynchronizationDriver extends BaseDriver {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final CountDownLatch preDriverBarrier = new CountDownLatch(1);
  private final CountDownLatch postDriverBarrier = new CountDownLatch(1);

  // Not thread-safe: intended to be set/read (using {@link #preSyncError}, {@link #postSyncError},
  // {@link #error} below) from a single centralized thread that manages multiple decorator stacks.
  // TODO: make threadsafe.
  private Exception error = null;

  public SubDeviceSynchronizationDriver(Device device, TestInfo testInfo) {
    super(device, testInfo);
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    preDriverBarrier.countDown();
    postDriverBarrier.await();
    if (error != null) {
      // This decorator only calls Decorator.run() as a future, and this method only declares
      // {@link MobileHarnessException} and {@link InterruptedException}. Therefore if we get
      // a different type of exception the system is in an illegal state.
      MobileHarnessExceptions.rethrow(
          error, ExtErrorId.TESTBED_DEVICE_SYNCHRONIZATION_DRIVER_ERROR);
    }
  }

  public boolean isReadyToSync() {
    return preDriverBarrier.getCount() == 0;
  }

  public void sync() throws InterruptedException {
    preDriverBarrier.await();
  }

  /**
   * Allows the object orchestrating synchronization across {@link SubDeviceDecoratorStack}
   * instances to propagate errors to the stack before synchronization.
   *
   * <p>This should be used to propagate any errors from outside the subdevice stack to here for
   * storage (e.g., from other {@link SubDeviceDecoratorStack}, from the main Driver, etc.) This
   * call also releases any process awaiting synchronization.
   *
   * <p>It is the responsibility of whatever orchestrating the synchronization to use this
   * correctly.
   *
   * @param error an exception originating from outside of the decorator stack.
   */
  public void preSyncError(Exception error) {
    // Use postSyncError to guard against overriding error
    postSyncError(error);
    preDriverBarrier.countDown();
  }

  /**
   * Allows the central object orchestrating synchronization across {@link SubDeviceDecoratorStack}
   * instances to propagate errors to the stack after synchronization.
   *
   * <p>See {@link #preSyncError} for usage. Note: Cannot override any existing error.
   *
   * @param error an exception originating from outside of the decorator stack.
   */
  public void postSyncError(Exception error) {
    if (this.error == null) {
      this.error = error;
    } else {
      logger.atInfo().withCause(error).log("Cannot override existing error");
    }
  }

  /** Gets the error (if any has occurred). */
  public Optional<Exception> error() {
    return Optional.ofNullable(error);
  }

  public void releaseSync() {
    postDriverBarrier.countDown();
  }
}
