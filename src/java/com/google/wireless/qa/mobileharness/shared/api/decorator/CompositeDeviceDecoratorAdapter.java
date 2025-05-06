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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.SubDeviceDecoratorStack;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.job.JobTypeUtil;
import com.google.wireless.qa.mobileharness.shared.api.spec.CompositeDeviceDecoratorAdapterSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.util.HashSet;
import java.util.Set;

/**
 * Decorator which applies a set of subdecorators over all managed devices within a {@link
 * CompositeDevice}.
 */
@DecoratorAnnotation(
    help = "Applies a set of subdecorators to all managed devices in a CompositeDevice.")
public class CompositeDeviceDecoratorAdapter extends CompositeDeviceAdapterBase
    implements CompositeDeviceDecoratorAdapterSpec {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public CompositeDeviceDecoratorAdapter(Driver decoratedDriver, TestInfo testInfo) {
    super(decoratedDriver, testInfo);
  }

  @VisibleForTesting
  CompositeDeviceDecoratorAdapter(Driver decoratedDriver, TestInfo testInfo, EventBus eventBus) {
    super(decoratedDriver, testInfo, eventBus);
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    // Convert our spec to a list of decorator classes
    String jobTypeStr = testInfo.jobInfo().params().get(PARAM_COMPOSITE_DEVICE_SUBDECORATORS);
    // The first two values are device and driver; we will make fake ones for parsing.
    jobTypeStr = "_nodevice_+_nodriver_+" + jobTypeStr;
    JobType jobType = JobTypeUtil.parseString(jobTypeStr);

    // Get a stack of decorators for each subdevice.
    Set<SubDeviceDecoratorStack> stacks = new HashSet<>();
    for (Device subdevice : ((CompositeDevice) getDevice()).getManagedDevices()) {
      if (isCompatible(subdevice, jobType.getDecoratorList())) {
        TestInfo subDeviceTestInfo = makeSubDeviceTestInfo(testInfo, subdevice);
        SubDeviceDecoratorStack stack =
            makeSubDeviceDecoratorStack(subdevice, subDeviceTestInfo, jobType.getDecoratorList());
        stacks.add(stack);
      } else {
        Set<String> invalidDecoratorTypes = new HashSet<>(jobType.getDecoratorList());
        invalidDecoratorTypes.removeAll(subdevice.getDecoratorTypes());
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Did not run subdecorator stack \"%s\" against device of type %s with id \"%s\" "
                    + "because decorators %s are not applicable to this device type.",
                jobTypeStr,
                subdevice.getDeviceTypes(),
                subdevice.getDeviceId(),
                invalidDecoratorTypes);
      }
    }

    // Make sure we found something to do.
    if (stacks.isEmpty()) {
      String msg =
          String.format(
              "Invalid %s param: no managed devices matched driver list %s",
              PARAM_COMPOSITE_DEVICE_SUBDECORATORS,
              testInfo.jobInfo().params().get(PARAM_COMPOSITE_DEVICE_SUBDECORATORS));
      throw new MobileHarnessException(
          ExtErrorId.COMPOSITE_DEVICE_DECORATOR_ADAPTER_DECORATOR_JOB_PARAM_ERROR, msg);
    }

    // Kick off each driver in parallel.
    runInParallel(testInfo, stacks);
  }

  private static TestInfo makeSubDeviceTestInfo(TestInfo rootTestInfo, Device subdevice)
      throws MobileHarnessException {
    return rootTestInfo
        .subTests()
        .add(
            subdevice.getDeviceId(),
            String.format("%s_%s", rootTestInfo.locator().getName(), subdevice.getDeviceId()));
  }
}
