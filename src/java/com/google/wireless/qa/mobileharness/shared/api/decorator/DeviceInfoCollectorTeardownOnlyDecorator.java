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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.TeardownOnlyDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.PhaseSkippableDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.DeviceInfoCollectorDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.state.decorator.DeviceInfoCollectorDecoratorState;
import javax.inject.Inject;

/**
 * Teardown-only decorator of {@link DeviceInfoCollectorDecorator}. See {@link
 * DeviceInfoCollectorDecorator} for more details.
 */
@DecoratorAnnotation(help = "For collecting device info from device.")
public class DeviceInfoCollectorTeardownOnlyDecorator extends TeardownOnlyDecorator
    implements SpecConfigable<DeviceInfoCollectorDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ApkInstaller apkInstaller;

  @Inject
  DeviceInfoCollectorTeardownOnlyDecorator(
      Driver decorated, TestInfo testInfo, ApkInstaller apkInstaller) {
    super(decorated, testInfo);
    this.apkInstaller = apkInstaller;
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();
    DeviceInfoCollectorDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);
    String packageName = spec.getPackageName();

    DeviceInfoCollectorDecoratorState state =
        PhaseSkippableDecoratorUtil.getState(
                testInfo, deviceId, DeviceInfoCollectorDecoratorState.class)
            .orElse(DeviceInfoCollectorDecoratorState.getDefaultInstance());

    if (state.hasInstalled() && state.getInstalled() && !packageName.isEmpty()) {
      testInfo.log().atInfo().alsoTo(logger).log("Uninstalling package: %s", packageName);
      apkInstaller.uninstallApk(getDevice(), packageName, /* logFailures= */ false, testInfo.log());
      PhaseSkippableDecoratorUtil.setState(
          testInfo,
          deviceId,
          DeviceInfoCollectorDecoratorState.newBuilder().setInstalled(false).build());
    }
  }
}
