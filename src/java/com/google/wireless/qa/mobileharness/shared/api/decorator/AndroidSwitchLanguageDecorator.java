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
import com.google.devtools.mobileharness.platform.android.systemsetting.LanguageSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.LanguageSettingUtil.LocaleRestoreState;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidSwitchLanguageDecoratorSpec;
import java.time.Duration;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Driver decorator for setting device language and country before running test. */
@DecoratorAnnotation(
    help = "For switching device language and country before running test on rooted device.")
public class AndroidSwitchLanguageDecorator extends LifecycleDecorator
    implements SpecConfigable<AndroidSwitchLanguageDecoratorSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LanguageSettingUtil languageSettingUtil;

  @Nullable private LocaleRestoreState restoreState;
  private Duration logSignalTimeout = LanguageSettingUtil.DEFAULT_LOG_SIGNAL_TIMEOUT;

  @Inject
  AndroidSwitchLanguageDecorator(
      Driver decoratedDriver, TestInfo testInfo, LanguageSettingUtil languageSettingUtil) {
    super(decoratedDriver, testInfo);
    this.languageSettingUtil = languageSettingUtil;
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    Device device = getDevice();
    String deviceId = device.getDeviceId();

    AndroidSwitchLanguageDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);
    logSignalTimeout = Duration.ofSeconds(spec.getLogSignalTimeoutSec());
    Optional<LocaleRestoreState> state =
        languageSettingUtil.switchLanguage(
            device, spec.getLanguage(), spec.getCountry(), logSignalTimeout, testInfo.log());
    if (spec.getRestoreLanguageAfterTest()) {
      restoreState = state.orElse(null);
    }
    return SetupResult.continueDecorated();
  }

  @Override
  protected void tearDown(TeardownContext context) throws InterruptedException {
    LocaleRestoreState currentRestoreState = restoreState;
    if (currentRestoreState == null) {
      return;
    }
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();
    testInfo.log().atInfo().alsoTo(logger).log("Restoring language on device %s", deviceId);
    try {
      languageSettingUtil.restoreLanguage(
          deviceId, currentRestoreState, logSignalTimeout, testInfo.log());
    } catch (MobileHarnessException e) {
      testInfo.warnings().addAndLog(e, logger);
    }
  }
}
