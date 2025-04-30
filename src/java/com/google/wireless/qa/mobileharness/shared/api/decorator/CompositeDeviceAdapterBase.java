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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.util.SubscriberExceptionLoggingHandler;
import com.google.devtools.mobileharness.platform.testbed.SubDeviceDecoratorStack;
import com.google.devtools.mobileharness.platform.testbed.TestbedTestRunnerUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Collection;
import java.util.List;

/** A common base class for adapting decorators to {@link CompositeDevice} subdevices. */
public abstract class CompositeDeviceAdapterBase extends BaseDecorator {

  // Local {@link EventBus} available to {@link CompositeDeviceAdapterBase} and its sub-classes.
  // This event bus is used to bridge MH {@link EventScope.TEST_MESSAGE} scoped messages to
  // underlying decorators that are created by {@link CompositeDeviceAdapterBase}
  // It's unlikely that {@link CompositeDeviceAdapterBase} and sub-decorators will need to subscribe
  // to other scoped event messages, so here a single eventBus is used to bridge messages.
  private final EventBus eventBus;

  protected CompositeDeviceAdapterBase(Driver decorated, TestInfo testInfo) {
    this(decorated, testInfo, new EventBus(new SubscriberExceptionLoggingHandler()));
  }

  // TODO: This @VisibleForTesting annotation was being ignored by prod code.
  // Please check that removing it is correct, and remove this comment along with it.
  // @VisibleForTesting
  CompositeDeviceAdapterBase(Driver decorated, TestInfo testInfo, EventBus eventBus) {
    super(decorated, testInfo);
    this.eventBus = eventBus;
  }

  /*
   * Receive {@link TestEventMessage} and forward it to sub-decorators
   */
  @Subscribe
  private void onReceiveTestMessage(TestMessageEvent event) {
    eventBus.post(event);
  }

  protected boolean isCompatible(Device subdevice, List<String> decorators) {
    return TestbedTestRunnerUtil.isCompatible(subdevice, decorators);
  }

  protected void checkCompatible(Device subdevice, List<String> decorators)
      throws MobileHarnessException {
    TestbedTestRunnerUtil.checkCompatible(subdevice, decorators);
  }

  protected void runInParallel(TestInfo testInfo, Collection<SubDeviceDecoratorStack> stacks)
      throws MobileHarnessException, InterruptedException {
    TestbedTestRunnerUtil.runParallelSubDeviceStacks(testInfo, stacks, getDecorated());
  }

  /**
   * Creates {@link SubDeviceDecoratorStack} for a subdevice.
   *
   * @param subdevice the device to decorate
   * @param testInfo the test info
   * @param decorators the names of decorator classes to apply to the subdevice
   * @return a {@link SubDeviceDecoratorStack}
   */
  protected SubDeviceDecoratorStack makeSubDeviceDecoratorStack(
      Device subdevice, TestInfo testInfo, List<String> decorators)
      throws MobileHarnessException, InterruptedException {
    return SubDeviceDecoratorStack.create(subdevice, testInfo, decorators, eventBus);
  }
}
