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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.function.Predicate.not;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidDeviceFeaturesCheckDecoratorSpec;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

/** Decorator for skipping the test based on device features (pm list features). */
@DecoratorAnnotation(
    help =
        "Decorator for skipping the test based on device features (pm list features). See"
            + " AndroidDeviceFeaturesCheckDecoratorSpec for more details.")
public class AndroidDeviceFeaturesCheckDecorator extends BaseDecorator
    implements SpecConfigable<AndroidDeviceFeaturesCheckDecoratorSpec> {

  private static final String FEATURE_PREFIX = "feature:";

  private final AndroidSystemSpecUtil androidSystemSpecUtil;

  @Inject
  AndroidDeviceFeaturesCheckDecorator(
      Driver decorated, TestInfo testInfo, AndroidSystemSpecUtil androidSystemSpecUtil) {
    super(decorated, testInfo);
    this.androidSystemSpecUtil = androidSystemSpecUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();

    AndroidDeviceFeaturesCheckDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);
    List<String> requiredFeatures = spec.getRequiredFeatureList();
    List<String> forbiddenFeatures = spec.getForbiddenFeatureList();

    // Gets all supported features.
    Set<String> supportedFeatures = androidSystemSpecUtil.getSystemFeatures(deviceId);

    // Calculates nonexistent required features and existent forbidden features.
    ImmutableList<String> nonexistentRequiredFeatures =
        requiredFeatures.stream()
            .map(AndroidDeviceFeaturesCheckDecorator::formatFeature)
            .filter(not(supportedFeatures::contains))
            .collect(toImmutableList());
    ImmutableList<String> existentForbiddenFeatures =
        forbiddenFeatures.stream()
            .map(AndroidDeviceFeaturesCheckDecorator::formatFeature)
            .filter(supportedFeatures::contains)
            .collect(toImmutableList());

    if (nonexistentRequiredFeatures.isEmpty() && existentForbiddenFeatures.isEmpty()) {
      // The check passes.
      getDecorated().run(testInfo);
    } else {
      // The check fails.
      MobileHarnessException error =
          new MobileHarnessException(
              AndroidErrorId.ANDROID_DEVICE_FEATURES_CHECK_DECORATOR_CEHCK_FAILURE,
              String.format(
                  "Skipped due to incompatible device features."
                      + " nonexistent_required_features=%s, existent_forbidden_features=%s,"
                      + " device_features=%s, device_id=%s",
                  nonexistentRequiredFeatures,
                  existentForbiddenFeatures,
                  supportedFeatures,
                  deviceId));
      testInfo.resultWithCause().setNonPassing(TestResult.SKIP, error);
      testInfo.getRootTest().resultWithCause().setNonPassing(TestResult.SKIP, error);
      // Skips later decorators and driver execution because it wants to skip the test. Don't throw
      // the exception out so to avoid the test result being overridden as FAIL/ERROR later.
    }
  }

  private static String formatFeature(String feature) {
    return feature.startsWith(FEATURE_PREFIX) ? feature : FEATURE_PREFIX + feature;
  }
}
