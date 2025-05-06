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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.ClassUtil;
import com.google.wireless.qa.mobileharness.shared.api.decorator.Decorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.driver.DriverFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.List;

/** Represents decorators that a testbed's subdevice runs outside of the main testbed driver. */
public class SubDeviceDecoratorStack {

  private final SubDeviceSynchronizationDriver syncer;

  private final Driver decoratorStack;

  private final TestInfo testInfo;

  /**
   * Creates a subdevice decorator stack, with {@link SubDeviceSynchronizationDriver} in the middle.
   *
   * @param subDevice the device to decorate
   * @param testInfo the test info
   * @param decorators the names of decorator classes to apply to the subdevice
   * @param eventBus {@link EventBus} that decorators and drivers in the stack may subscribe to
   * @return a {@link SubDeviceDecoratorStack}
   * @throws MobileHarnessException if specified decorators are not found or not supported.
   */
  public static SubDeviceDecoratorStack create(
      Device subDevice, TestInfo testInfo, List<String> decorators, EventBus eventBus)
      throws MobileHarnessException {
    return SubDeviceDecoratorStack.create(
        subDevice, testInfo, decorators, eventBus, new DriverFactory());
  }

  @VisibleForTesting
  static SubDeviceDecoratorStack create(
      Device subDevice,
      TestInfo testInfo,
      List<String> decorators,
      EventBus eventBus,
      DriverFactory driverFactory)
      throws MobileHarnessException {
    TestbedTestRunnerUtil.checkCompatible(subDevice, decorators);
    List<Class<? extends Decorator>> decoratorClasses = ClassUtil.getDecoratorClasses(decorators);
    // Prepare the underlying driver stack. We need to make sure that the run() methods of all
    // the decorators are interleaved correctly (i.e. that decorators "outside" of the main driver
    // finish their setup, then the run() of the main driver is called, and then the back half of
    // decorators happens), so we will synchronize each driver chain on a syncer and wait for it to
    // be reached from all threads.
    SubDeviceSynchronizationDriver syncer = new SubDeviceSynchronizationDriver(subDevice, testInfo);
    Driver decoratorStack =
        driverFactory.decorateDriver(
            syncer,
            testInfo,
            decoratorClasses,
            /* driverWrapper= */ null,
            /* decoratorExtender= */ null);
    // Register all created drivers and decorators
    ClassUtil.getAllSubscribersOfDriver(decoratorStack).forEach(eventBus::register);
    return new SubDeviceDecoratorStack(testInfo, syncer, decoratorStack);
  }

  private SubDeviceDecoratorStack(
      TestInfo testInfo, SubDeviceSynchronizationDriver syncer, Driver decoratorStack) {
    this.testInfo = testInfo;
    this.syncer = syncer;
    this.decoratorStack = decoratorStack;
  }

  /** {@link TestInfo} for this stack */
  public TestInfo testInfo() {
    return testInfo;
  }

  /** The {@link SubDeviceSynchronizationDriver} of this stack. */
  public SubDeviceSynchronizationDriver syncer() {
    return syncer;
  }

  /** The {@code Runnable} that runs the entire decorator stack */
  public Driver decoratorStack() {
    return decoratorStack;
  }
}
