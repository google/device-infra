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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.toCollection;

import com.google.common.base.Ascii;
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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
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
import com.google.devtools.mobileharness.platform.android.xts.config.DynamicConfig;
import com.google.devtools.mobileharness.platform.android.xts.config.DynamicConfigResolver;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.SetupOnlyDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidBusinessLogicSkipModuleDecoratorSpec;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Decorator that skips module execution based on xTS business logic rules. Only supports release
 * types.
 */
@DecoratorAnnotation(help = "Skips module execution if specified by business logic.")
public class AndroidBusinessLogicSkipModuleDecorator extends SetupOnlyDecorator
    implements SpecConfigable<AndroidBusinessLogicSkipModuleDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String SUITE_PLACEHOLDER = "{suite-name}";

  private static final String FEATURE_PREFIX = "feature:";

  private static final String DYNAMIC_CONFIG_FEATURES_KEY = "business_logic_device_features";
  private static final String DYNAMIC_CONFIG_PROPERTIES_KEY = "business_logic_device_properties";
  private static final String DYNAMIC_CONFIG_PACKAGES_KEY = "business_logic_device_packages";

  /** Dynamic config values are used in adb shell commands, so only plain names are accepted. */
  private static final Pattern VALID_DYNAMIC_CONFIG_VALUE = Pattern.compile("[A-Za-z0-9_.-]+");

  private static final String XTS_TEST_DIR_PARAM = "xts_test_dir";

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
          "ro.build.version.release",
          "ro.build.version.sdk",
          "ro.build.version.sdk_full",
          "ro.product.brand",
          "ro.product.device",
          "ro.product.first_api_level",
          "ro.product.manufacturer",
          "ro.product.model",
          "ro.product.name",
          "ro.soc.model");

  private static final ImmutableSet<String> BUSINESS_LOGIC_DEVICE_PACKAGES =
      ImmutableSet.of("com.google.android.gms", "com.android.vending");

  private final MapSplitter xtsSuiteInfoSplitter = Splitter.on(",").withKeyValueSeparator("=");

  private final AndroidSystemSpecUtil androidSystemSpecUtil;
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final BusinessLogicFetcher businessLogicFetcher;
  private final DynamicConfigResolver dynamicConfigResolver;

  @Inject
  AndroidBusinessLogicSkipModuleDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidSystemSpecUtil androidSystemSpecUtil,
      AndroidAdbUtil androidAdbUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      BusinessLogicFetcher businessLogicFetcher,
      DynamicConfigResolver dynamicConfigResolver) {
    super(decoratedDriver, testInfo);
    this.androidSystemSpecUtil = androidSystemSpecUtil;
    this.androidAdbUtil = androidAdbUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.businessLogicFetcher = businessLogicFetcher;
    this.dynamicConfigResolver = dynamicConfigResolver;
  }

  /**
   * The exception to throw if the module execution should be skipped or fail based on business
   * logic. If {@code null}, the module execution should continue.
   */
  @Nullable private MobileHarnessException evaluationResultException;

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    String deviceId = getDevice().getDeviceId();
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, deviceId);

    if (!spec.getBusinessLogicUrl().isEmpty()) {
      ImmutableMap<String, String> xtsSuiteInfoMap =
          !spec.getXtsSuiteInfo().isEmpty()
              ? ImmutableMap.copyOf(xtsSuiteInfoSplitter.split(spec.getXtsSuiteInfo()))
              : ImmutableMap.of();

      String formattedUrl = formatBusinessLogicUrl(spec.getBusinessLogicUrl(), xtsSuiteInfoMap);

      try {
        ImmutableSetMultimap<String, String> params =
            buildRequestParams(testInfo, spec, deviceId, xtsSuiteInfoMap);
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
        boolean matched = logic.applyLogicFor(key, executor);
        if (!matched) {
          skipTest(
              String.format("Module %s skipped because no business logic rules matched.", key));
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
        this.evaluationResultException = null;
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log("Failed to evaluate business logic. Continuing with module execution.");
      }

      // If the exception is non-null, skip the decorated execution (the test result has already
      // been set). Otherwise, the execution continues normally.
      if (this.evaluationResultException != null) {
        return SetupResult.skipDecoratedWithoutResult();
      }
    }
    return SetupResult.continueDecorated();
  }

  /** Called via reflection by {@link BusinessLogicExecutor}. */
  public void continueTest() {
    this.evaluationResultException = null;
  }

  /** Called via reflection by {@link BusinessLogicExecutor}. */
  public void continueTestWithFeatures(String... allowedFeatures)
      throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    Set<String> deviceFeatures = androidSystemSpecUtil.getSystemFeatures(deviceId);
    ImmutableSet<String> strippedFeatures =
        deviceFeatures.stream()
            .map(f -> f.startsWith(FEATURE_PREFIX) ? f.substring(FEATURE_PREFIX.length()) : f)
            .collect(toImmutableSet());
    ImmutableSet<String> allowedSet = ImmutableSet.copyOf(allowedFeatures);
    boolean hasMatchingFeature = !Collections.disjoint(strippedFeatures, allowedSet);
    if (hasMatchingFeature) {
      this.evaluationResultException = null;
    } else {
      skipTest(
          String.format(
              "Module skipped because device lacks required features. Allowed: %s, Device: %s",
              allowedSet, strippedFeatures));
    }
  }

  /** Called via reflection by {@link BusinessLogicExecutor}. */
  public void skipTest(String errorMessage) {
    MobileHarnessException exception =
        MobileHarnessExceptionFactory.createExceptionWithoutStackTrace(
            AndroidErrorId.ANDROID_BUSINESS_LOGIC_SKIP_MODULE_DECORATOR_SKIPPED, errorMessage);

    getTest().resultWithCause().setNonPassing(TestResult.SKIP, exception);
    getTest().getRootTest().resultWithCause().setNonPassing(TestResult.SKIP, exception);
    this.evaluationResultException = exception;
  }

  /** Called via reflection by {@link BusinessLogicExecutor}. */
  public void failTest(String errorMessage) {
    MobileHarnessException exception =
        MobileHarnessExceptionFactory.createExceptionWithoutStackTrace(
            AndroidErrorId.ANDROID_BUSINESS_LOGIC_SKIP_MODULE_DECORATOR_FAIL, errorMessage);

    getTest().resultWithCause().setNonPassing(TestResult.ERROR, exception);
    getTest().getRootTest().resultWithCause().setNonPassing(TestResult.ERROR, exception);
    this.evaluationResultException = exception;
  }

  private ImmutableSetMultimap<String, String> buildRequestParams(
      TestInfo testInfo,
      AndroidBusinessLogicSkipModuleDecoratorSpec spec,
      String deviceId,
      Map<String, String> xtsSuiteInfoMap)
      throws MobileHarnessException, InterruptedException {
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

    ImmutableMap<String, List<String>> dynamicConfig =
        getSuiteDynamicConfig(testInfo, spec, xtsSuiteInfoMap);
    ImmutableSet<String> businessLogicDeviceFeatures =
        union(testInfo, BUSINESS_LOGIC_DEVICE_FEATURES, dynamicConfig, DYNAMIC_CONFIG_FEATURES_KEY);
    ImmutableSet<String> businessLogicDeviceProperties =
        union(
            testInfo,
            BUSINESS_LOGIC_DEVICE_PROPERTIES,
            dynamicConfig,
            DYNAMIC_CONFIG_PROPERTIES_KEY);
    ImmutableSet<String> businessLogicDevicePackages =
        union(testInfo, BUSINESS_LOGIC_DEVICE_PACKAGES, dynamicConfig, DYNAMIC_CONFIG_PACKAGES_KEY);

    try {
      Set<String> deviceFeatures = androidSystemSpecUtil.getSystemFeatures(deviceId);
      Set<String> strippedFeatures =
          deviceFeatures.stream()
              .map(f -> f.substring(FEATURE_PREFIX.length()))
              .filter(businessLogicDeviceFeatures::contains)
              .collect(toCollection(HashSet::new));
      paramsBuilder.putAll("features", strippedFeatures);
    } catch (MobileHarnessException e) {
      testInfo.log().atWarning().alsoTo(logger).withCause(e).log("Failed to get device features");
    }

    for (String propertyName : businessLogicDeviceProperties) {
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
      installedPackages.retainAll(businessLogicDevicePackages);
      paramsBuilder.putAll("packages", installedPackages);
    } catch (MobileHarnessException e) {
      testInfo.log().atWarning().alsoTo(logger).withCause(e).log("Failed to list device packages");
    }

    return paramsBuilder.build();
  }

  /** Returns the suite dynamic config, or an empty map if suite name or version is missing. */
  private ImmutableMap<String, List<String>> getSuiteDynamicConfig(
      TestInfo testInfo,
      AndroidBusinessLogicSkipModuleDecoratorSpec spec,
      Map<String, String> xtsSuiteInfoMap)
      throws MobileHarnessException {
    String suiteName = xtsSuiteInfoMap.getOrDefault("suite_name", "");
    String suiteVersion = xtsSuiteInfoMap.getOrDefault("suite_version", "");
    if (suiteName.isEmpty() || suiteVersion.isEmpty()) {
      return ImmutableMap.of();
    }
    String xtsTestDir = testInfo.jobInfo().params().getOptional(XTS_TEST_DIR_PARAM).orElse("");
    Map<String, List<String>> config =
        new HashMap<>(getLocalSuiteDynamicConfig(testInfo, suiteName, xtsTestDir));
    String sessionId = testInfo.jobInfo().properties().getOptional(Job.SESSION_ID).orElse("");
    Duration timeout = Duration.ofMillis(spec.getBusinessLogicTimeoutMs());
    try {
      config.putAll(
          dynamicConfigResolver.fetchServerConfig(suiteName, suiteVersion, sessionId, timeout));
    } catch (MobileHarnessException e) {
      if (!spec.getIgnoreBusinessLogicFailure()) {
        throw e;
      }
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log(
              "Failed to fetch dynamic config of %s from server, using local config only",
              suiteName);
    }
    return ImmutableMap.copyOf(config);
  }

  private ImmutableMap<String, List<String>> getLocalSuiteDynamicConfig(
      TestInfo testInfo, String suiteName, String xtsTestDir) {
    // The JAR is not available on, e.g., remote runners of ATS Server, where xts_test_dir is a
    // path on the server. In that case only the server config is used.
    File localConfigFile =
        dynamicConfigResolver.extractFromTradefedJar(
            suiteName, xtsTestDir, Ascii.toLowerCase(suiteName) + "-tradefed");
    if (localConfigFile == null) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("No dynamic config of %s found in the suite tradefed JAR", suiteName);
      return ImmutableMap.of();
    }
    try {
      return ImmutableMap.copyOf(DynamicConfig.createConfigMap(localConfigFile));
    } catch (IOException | XmlPullParserException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to parse %s", localConfigFile);
      return ImmutableMap.of();
    } finally {
      if (!localConfigFile.delete()) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Failed to delete temp dynamic config file %s", localConfigFile);
      }
    }
  }

  /**
   * Returns {@code builtInValues} extended with the values of {@code key} in the dynamic config.
   */
  private static ImmutableSet<String> union(
      TestInfo testInfo,
      ImmutableSet<String> builtInValues,
      Map<String, List<String>> dynamicConfig,
      String key) {
    ImmutableSet.Builder<String> values = ImmutableSet.<String>builder().addAll(builtInValues);
    for (String value : dynamicConfig.getOrDefault(key, ImmutableList.of())) {
      if (VALID_DYNAMIC_CONFIG_VALUE.matcher(value).matches()) {
        values.add(value);
      } else {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log("Ignored invalid value '%s' of %s in dynamic config", value, key);
      }
    }
    return values.build();
  }

  private String formatBusinessLogicUrl(String url, Map<String, String> xtsSuiteInfoMap) {
    return url.replace(SUITE_PLACEHOLDER, xtsSuiteInfoMap.getOrDefault("suite_name", ""));
  }
}
