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
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.TeardownOnlyDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.util.PhaseSkippableDecoratorUtil;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAtsDynamicConfigPusherDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.state.decorator.AndroidAtsDynamicConfigPusherDecoratorState;
import javax.inject.Inject;

/**
 * Teardown-only decorator of {@link AndroidAtsDynamicConfigPusherDecorator}. See {@link
 * AndroidAtsDynamicConfigPusherDecorator} for more details.
 */
@DecoratorAnnotation(help = "Decorator to push dynamic config files from config repository.")
public class AndroidAtsDynamicConfigPusherTeardownOnlyDecorator extends TeardownOnlyDecorator
    implements SpecConfigable<AndroidAtsDynamicConfigPusherDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidFileUtil androidFileUtil;
  private final ApkInstaller apkInstaller;

  @Inject
  AndroidAtsDynamicConfigPusherTeardownOnlyDecorator(
      Driver decorated,
      TestInfo testInfo,
      AndroidFileUtil androidFileUtil,
      ApkInstaller apkInstaller) {
    super(decorated, testInfo);
    this.androidFileUtil = androidFileUtil;
    this.apkInstaller = apkInstaller;
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();
    AndroidAtsDynamicConfigPusherDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, deviceId);

    AndroidAtsDynamicConfigPusherDecoratorState state =
        PhaseSkippableDecoratorUtil.getState(
                testInfo, deviceId, AndroidAtsDynamicConfigPusherDecoratorState.class)
            .orElse(AndroidAtsDynamicConfigPusherDecoratorState.getDefaultInstance());

    if (state.hasDeviceFilePushedPath() && spec.getCleanup()) {
      String path = state.getDeviceFilePushedPath();
      try {
        androidFileUtil.removeFiles(deviceId, path);
        logger.atInfo().log("Cleaned up dynamic config file %s on device %s", path, deviceId);
      } catch (MobileHarnessException | RuntimeException | Error e) {
        logger.atWarning().withCause(e).log("Failed to clean up pushed file %s", path);
      }
    }
    if (state.hasContentProvider() && !state.getContentProvider().isEmpty() && spec.getCleanup()) {
      apkInstaller.uninstallApk(
          getDevice(), state.getContentProvider(), /* logFailures= */ true, /* log= */ null);
    }
  }
}
