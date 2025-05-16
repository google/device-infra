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

import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.SubDeviceDecoratorStack;
import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceInfo;
import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceKey;
import com.google.devtools.mobileharness.platform.testbed.mobly.MoblyConstant;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.TestbedDevice;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.validator.util.MoblyDecoratorAdapterJobValidatorUtil;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.model.lab.in.CompositeDimensions;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.DeviceSelector;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.DeviceToJobSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.MoblyDecoratorAdapterSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.SubDeviceJobSpec;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decorator which applies a set of subdecorators over managed devices within a TestbedDevice,
 * matching by subdevice label.
 */
@DecoratorAnnotation(
    help =
        "Applies a set of subdecorators to subdevices within a TestbedDevice, matching by label.")
public class MoblyDecoratorAdapter extends CompositeDeviceAdapterBase
    implements SpecConfigable<MoblyDecoratorAdapterSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private MoblyDecoratorAdapterSpec spec;

  public MoblyDecoratorAdapter(Driver decoratedDriver, TestInfo testInfo) {
    super(decoratedDriver, testInfo);
  }

  @VisibleForTesting
  MoblyDecoratorAdapter(Driver decoratedDriver, TestInfo testInfo, EventBus eventBus) {
    super(decoratedDriver, testInfo, eventBus);
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    spec = testInfo.jobInfo().combinedSpec(this);
    MoblyDecoratorAdapterJobValidatorUtil.validateSpec(spec);
    Collection<SubDeviceDecoratorStack> stacks = makeSubDeviceStacks(testInfo);
    runInParallel(testInfo, stacks);
    // After subTests are done and subTestInfo were updated, sync them to original root testInfo.
    syncAdapterInfoToTestInfo(testInfo, stacks);
  }

  /**
   * Update root testInfo with test result in subTestInfo created for each subDevice.
   *
   * <p>Because of the hack mentioned at {@link MoblyDecoratorAdapter#makeSubDeviceTestInfo}, Mobly
   * test creates subJobInfo for each subDevice that needs different job parameters and settings.
   *
   * <p>The created subJobInfo together with subTestInfo is passed down to decorators of each
   * subDevice, which requires different job/test configuration.
   *
   * <p>However, this means that the created subJobInfo and subTestInfo are not in sync with
   * original root jobInfo and root testInfo. Therefore they are not synced back to client side for
   * plugins to retrieve test results.
   */
  private void syncAdapterInfoToTestInfo(
      TestInfo testInfo, Collection<SubDeviceDecoratorStack> stacks) throws MobileHarnessException {
    JobInfo moblySubJobInfo = null;
    for (SubDeviceDecoratorStack stack : stacks) {
      // Subtest created by {@link MoblyDecoratorAdapter}
      TestInfo moblySubTestInfo = stack.testInfo();
      String serial = stack.syncer().getDevice().getDeviceId();
      // Subtest to be updated
      TestInfo mhSubTestInfo = testInfo.subTests().getById(serial);
      if (mhSubTestInfo == null) {
        mhSubTestInfo = testInfo.subTests().add(serial, testInfo.locator().getName());
      }

      // Update mhSubTestInfo attributes
      mhSubTestInfo.remoteGenFiles().addAll(moblySubTestInfo.remoteGenFiles().getAll());
      mhSubTestInfo.status().set(moblySubTestInfo.status().get());
      mhSubTestInfo.result().set(moblySubTestInfo.result().get());
      mhSubTestInfo.log().append(moblySubTestInfo.log().get(0));
      mhSubTestInfo.properties().addAll(moblySubTestInfo.properties().getAll());
      mhSubTestInfo
          .properties()
          .add(PropertyName.Test.DefaultSpongeTreeGenerator.OMIT_FROM_SPONGE, "true");
      mhSubTestInfo.warnings().addAll(moblySubTestInfo.warnings().getAll());
      if (moblySubJobInfo == null) {
        moblySubJobInfo = moblySubTestInfo.jobInfo();
      }
    }
    // Set subJobInfo to be DONE after tests are completed
    // This subJobInfo is created for sub-tests as part of {@link MoblyDecoratorAdapter}
    // decorator-driver stack.
    moblySubJobInfo.status().set(TestStatus.DONE);
  }

  /**
   * Creates a decorator stack for each selector in the {@code spec}.
   *
   * @param testInfo {@link TestInfo} for the current test
   * @return a Collection of {@link SubDeviceDecoratorStack} objects
   */
  private Collection<SubDeviceDecoratorStack> makeSubDeviceStacks(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    TestbedDevice testbedDevice = (TestbedDevice) getDevice();
    Set<SubDeviceDecoratorStack> stacks = new HashSet<>();
    for (DeviceToJobSpec deviceSpec : spec.getDeviceToJobSpecList()) {
      boolean foundMatch = false;
      for (Device subDevice : testbedDevice.getManagedDevices()) {
        if (deviceMatchesSelector(subDevice, deviceSpec.getDeviceSelector())) {
          TestInfo subTestInfo =
              makeSubDeviceTestInfo(testInfo, subDevice, deviceSpec.getJobSpec());
          SubDeviceDecoratorStack stack =
              makeSubDeviceDecoratorStack(
                  subDevice, subTestInfo, deviceSpec.getJobSpec().getDecoratorList());
          stacks.add(stack);
          foundMatch = true;
        }
      }
      if (!foundMatch) {
        logger.atInfo().log("No match found, printing subdevices.");
        for (Device subDevice : testbedDevice.getManagedDevices()) {
          logger.atInfo().log("subDevice: %s", subDevice.getDeviceId());
          logger.atInfo().log("dimensions: %s", subDevice.getDimensions());
        }
        throw new MobileHarnessException(
            ExtErrorId.MOBLY_DECORATOR_ADAPTER_DECORATOR_SPEC_ERROR,
            "No devices matching selector <"
                + shortDebugString(deviceSpec.getDeviceSelector())
                + "> in testbed "
                + testbedDevice.getDeviceId());
      }
    }
    return stacks;
  }

  private boolean deviceMatchesSelector(Device device, DeviceSelector selector) {
    if (selector.hasDeviceLabel() && !deviceMatchesLabel(device, selector.getDeviceLabel())) {
      return false;
    }
    if (selector.getDimensionsCount() != 0
        && !deviceMatchesDimensions(device, selector.getDimensionsList())) {
      return false;
    }
    if (selector.hasSubDeviceId() && !deviceMatchSubDeviceId(device, selector.getSubDeviceId())) {
      return false;
    }
    return true;
  }

  private static boolean deviceMatchSubDeviceId(Device device, String subDeviceId) {
    return device.getDeviceId().equals(subDeviceId);
  }

  private boolean deviceMatchesLabel(Device device, String label) {
    String deviceLabel = null;
    ImmutableMap<SubDeviceKey, SubDeviceInfo> subDeviceInfos =
        ((TestbedDevice) getDevice()).getConfig().getDevices();
    for (SubDeviceKey key : subDeviceInfos.keySet()) {
      if (key.deviceId().equals(device.getDeviceId())
          && key.deviceType().equals(device.getClass())) {
        deviceLabel =
            (String)
                subDeviceInfos
                    .get(key)
                    .getProperties()
                    .get(MoblyConstant.ConfigKey.SUBDEVICE_LABEL);
      }
    }
    return label.equals(deviceLabel);
  }

  private boolean deviceMatchesDimensions(Device device, List<StrPair> jobDimensions) {
    CompositeDimensions dimensions = new CompositeDimensions();
    dimensions.supported().addAll(device.getDimensions());
    return dimensions.supportAndSatisfied(StrPairUtil.convertCollectionToMap(jobDimensions));
  }

  /**
   * Creates a sub-{@link TestInfo} for a subdevice. It's based on the sub-{@link
   * com.google.wireless.qa.mobileharness.shared.proto.spec.JobSpec} and a modified test ID which
   * appends the device ID after the root ID.
   *
   * <p>The modified test ID is a hack used to create the sub-genfiled dir for each device, while
   * also setting a custom spec for each subdevice by treating it as a sibling node in the job-tests
   * tree. Note that this implementation differs from {@link
   * CompositeDeviceDecoratorAdapter#makeSubDeviceTestInfo} which uses the subtest() API, which
   * properly creates subdevice nodes as children of the (single) test node tree but lacks support
   * for differing jobspecs per subtest.
   */
  private static TestInfo makeSubDeviceTestInfo(
      TestInfo rootTestInfo, Device subdevice, SubDeviceJobSpec deviceSpec)
      throws MobileHarnessException {
    String id = rootTestInfo.locator().getId() + "/" + subdevice.getDeviceId();
    JobInfo subJobInfo =
        MoblyDecoratorAdapterJobValidatorUtil.makeSubDeviceJobInfo(
            rootTestInfo.jobInfo(), deviceSpec);
    TestInfo subTestInfo = subJobInfo.tests().add(id, rootTestInfo.locator().getName());
    // Populates subTestInfo properties with the ones from rootTestInfo as some decorators like
    // AndroidAccountDecorator rely on them to pass info from client plugin to the decorator.
    subTestInfo.properties().addAll(rootTestInfo.properties().getAll());
    return subTestInfo;
  }
}
