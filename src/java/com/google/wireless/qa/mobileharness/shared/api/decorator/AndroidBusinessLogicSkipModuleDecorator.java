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

import static java.util.stream.Collectors.toCollection;

import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.common.SessionHandlerHelper;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.xts.businesslogic.BusinessLogic;
import com.google.devtools.mobileharness.platform.android.xts.businesslogic.BusinessLogicExecutor;
import com.google.devtools.mobileharness.platform.android.xts.businesslogic.BusinessLogicFetcher;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidBusinessLogicSkipModuleDecoratorSpec;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/**
 * Decorator that skips module execution based on xTS business logic rules. Only supports release
 * types.
 */
@DecoratorAnnotation(help = "Skips module execution if specified by business logic.")
public class AndroidBusinessLogicSkipModuleDecorator extends BaseDecorator
    implements SpecConfigable<AndroidBusinessLogicSkipModuleDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SUITE_PLACEHOLDER = "{suite-name}";

  private static final String FEATURE_PREFIX = "feature:";

  private static final ImmutableSet<String> BUSINESS_LOGIC_DEVICE_FEATURES =
      ImmutableSet.of(
          "android.hardware.type.automotive",
          "android.hardware.type.television",
          "android.hardware.type.watch",
          "android.hardware.type.embedded",
          "android.hardware.type.pc",
          "android.software.leanback",
          "com.google.android.feature.PIXEL_EXPERIENCE",
          "android.hardware.telephony",
          "android.hardware.vr.high_performance",
          "cn.google.services",
          "com.google.android.feature.RU",
          "android.hardware.ram.low",
          "com.google.android.feature.EEA_DEVICE",
          "com.google.android.paid.chrome",
          "com.google.android.paid.search");

  private static final ImmutableSet<String> BUSINESS_LOGIC_DEVICE_PROPERTIES =
      ImmutableSet.of(
          "ro.build.fingerprint",
          "ro.build.version.sdk",
          "ro.product.brand",
          "ro.product.first_api_level",
          "ro.product.manufacturer",
          "ro.product.model",
          "ro.product.name");

  private static final ImmutableSet<String> BUSINESS_LOGIC_DEVICE_PACKAGES =
      ImmutableSet.of("com.google.android.gms", "com.android.vending");

  private final MapSplitter xtsSuiteInfoSplitter = Splitter.on(",").withKeyValueSeparator("=");

  private final AndroidSystemSpecUtil androidSystemSpecUtil;
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final BusinessLogicFetcher businessLogicFetcher;

  @Inject
  AndroidBusinessLogicSkipModuleDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidSystemSpecUtil androidSystemSpecUtil,
      AndroidAdbUtil androidAdbUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      BusinessLogicFetcher businessLogicFetcher) {
    super(decoratedDriver, testInfo);
    this.androidSystemSpecUtil = androidSystemSpecUtil;
    this.androidAdbUtil = androidAdbUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.businessLogicFetcher = businessLogicFetcher;
  }

  private boolean shouldSkipRun = true;

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, deviceId);

    if (!spec.getBusinessLogicUrl().isEmpty()) {
      ImmutableMap<String, String> xtsSuiteInfoMap =
          !spec.getXtsSuiteInfo().isEmpty()
              ? ImmutableMap.copyOf(xtsSuiteInfoSplitter.split(spec.getXtsSuiteInfo()))
              : ImmutableMap.of();

      ImmutableSetMultimap<String, String> params =
          buildRequestParams(testInfo, deviceId, xtsSuiteInfoMap);

      String formattedUrl = formatBusinessLogicUrl(spec.getBusinessLogicUrl(), xtsSuiteInfoMap);

      try {
        String json =
            businessLogicFetcher.fetchBusinessLogic(
                formattedUrl,
                spec.getBusinessLogicApiScope(),
                spec.getBusinessLogicApiKey(),
                System.getenv("APE_API_KEY"),
                Duration.ofMillis(spec.getBusinessLogicTimeoutMs()),
                params);
        BusinessLogic logic = BusinessLogic.fromJsonString(json);

        BusinessLogicExecutor executor = new BusinessLogicExecutor(this);
        String configFileName = spec.getConfigFilename();
        if (Strings.isNullOrEmpty(configFileName)) {
          configFileName =
              testInfo
                  .jobInfo()
                  .properties()
                  .getOptional(SessionHandlerHelper.XTS_MODULE_NAME_PROP)
                  .orElse("");
        }
        String key = getClass().getName() + "#" + configFileName;
        logic.applyLogicFor(key, executor);
        if (this.shouldSkipRun) {
          return;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
      } catch (Exception e) {
        if (!spec.getIgnoreBusinessLogicFailure()) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_BUSINESS_LOGIC_SKIP_MODULE_DECORATOR_FAIL,
              "Failed to evaluate business logic. If this problem persists, re-invoking with option"
                  + " '--ignore-business-logic-failure' will cause tests to execute anyways"
                  + " (though tests depending on the remote configuration will fail).",
              e);
        }
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log("Failed to evaluate business logic. Continuing with module execution.");
      }
    }
    getDecorated().run(testInfo);
  }

  /** Called via reflection by {@link BusinessLogicExecutor}. */
  public void continueTest() {
    this.shouldSkipRun = false;
  }

  /** Called via reflection by {@link BusinessLogicExecutor}. */
  public void skipTest(String errorMessage) {
    this.shouldSkipRun = true;
    MobileHarnessException exception =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_BUSINESS_LOGIC_SKIP_MODULE_DECORATOR_SKIPPED, errorMessage);

    getTest().resultWithCause().setNonPassing(TestResult.SKIP, exception);
    getTest().getRootTest().resultWithCause().setNonPassing(TestResult.SKIP, exception);
  }

  /** Called via reflection by {@link BusinessLogicExecutor}. */
  public void failTest(String errorMessage) {
    this.shouldSkipRun = true;
    MobileHarnessException exception =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_BUSINESS_LOGIC_SKIP_MODULE_DECORATOR_FAIL, errorMessage);

    getTest().resultWithCause().setNonPassing(TestResult.ERROR, exception);
    getTest().getRootTest().resultWithCause().setNonPassing(TestResult.ERROR, exception);
  }

  private ImmutableSetMultimap<String, String> buildRequestParams(
      TestInfo testInfo, String deviceId, Map<String, String> xtsSuiteInfoMap)
      throws InterruptedException {
    ImmutableSetMultimap.Builder<String, String> paramsBuilder = ImmutableSetMultimap.builder();

    String suiteVersion = xtsSuiteInfoMap.getOrDefault("suite_version", "");
    if (!suiteVersion.isEmpty()) {
      paramsBuilder.put("suite_version", suiteVersion);
    }

    try {
      String oem = androidAdbUtil.getProperty(deviceId, AndroidProperty.MANUFACTURER);
      if (!Strings.isNullOrEmpty(oem)) {
        paramsBuilder.put("oem", oem);
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to get device manufacturer");
    }

    try {
      Set<String> deviceFeatures = androidSystemSpecUtil.getSystemFeatures(deviceId);
      Set<String> strippedFeatures =
          deviceFeatures.stream()
              .map(f -> f.substring(FEATURE_PREFIX.length()))
              .collect(toCollection(HashSet::new));
      strippedFeatures.retainAll(BUSINESS_LOGIC_DEVICE_FEATURES);
      paramsBuilder.putAll("features", strippedFeatures);
    } catch (MobileHarnessException e) {
      testInfo.log().atWarning().alsoTo(logger).withCause(e).log("Failed to get device features");
    }

    for (String propertyName : BUSINESS_LOGIC_DEVICE_PROPERTIES) {
      try {
        String propertyValue = androidAdbUtil.getProperty(deviceId, ImmutableList.of(propertyName));
        if (!Strings.isNullOrEmpty(propertyValue)) {
          paramsBuilder.put("properties", String.format("%s:%s", propertyName, propertyValue));
        }
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log("Failed to get device property %s", propertyName);
      }
    }

    try {
      Set<String> installedPackages =
          new HashSet<>(androidPackageManagerUtil.listPackages(deviceId, PackageType.ALL));
      installedPackages.retainAll(BUSINESS_LOGIC_DEVICE_PACKAGES);
      paramsBuilder.putAll("packages", installedPackages);
    } catch (MobileHarnessException e) {
      testInfo.log().atWarning().alsoTo(logger).withCause(e).log("Failed to list device packages");
    }

    return paramsBuilder.build();
  }

  private String formatBusinessLogicUrl(String url, Map<String, String> xtsSuiteInfoMap) {
    return url.replace(SUITE_PLACEHOLDER, xtsSuiteInfoMap.getOrDefault("suite_name", ""));
  }
}
