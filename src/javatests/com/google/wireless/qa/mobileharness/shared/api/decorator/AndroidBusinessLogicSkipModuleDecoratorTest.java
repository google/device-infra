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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.common.SessionHandlerHelper;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.xts.businesslogic.BusinessLogicFetcher;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidBusinessLogicSkipModuleDecoratorSpec;
import java.time.Duration;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidBusinessLogicSkipModuleDecorator}. */
@RunWith(JUnit4.class)
public final class AndroidBusinessLogicSkipModuleDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Driver decoratedDriver;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Device device;
  @Mock private Log testLogger;
  @Mock private Api atInfo;
  @Mock private Api atWarning;
  @Mock private TestLocator testLocator;
  @Mock private Properties testProperties;
  @Mock private Properties jobProperties;
  @Mock private Result testResultWithCause;
  @Mock private AndroidAdbUtil mockAndroidAdbUtil;
  @Mock private AndroidSystemSpecUtil mockAndroidSystemSpecUtil;
  @Mock private AndroidPackageManagerUtil mockAndroidPackageManagerUtil;
  @Mock private BusinessLogicFetcher mockFetcher;

  private AndroidBusinessLogicSkipModuleDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn("fake_device_id");
    when(testInfo.log()).thenReturn(testLogger);
    when(testLogger.atInfo()).thenReturn(atInfo);
    when(atInfo.alsoTo(any(FluentLogger.class))).thenReturn(atInfo);
    when(atInfo.withCause(any(Throwable.class))).thenReturn(atInfo);

    when(testLogger.atWarning()).thenReturn(atWarning);
    when(atWarning.alsoTo(any(FluentLogger.class))).thenReturn(atWarning);
    when(atWarning.withCause(any(Throwable.class))).thenReturn(atWarning);

    when(testInfo.locator()).thenReturn(testLocator);
    when(testLocator.getName()).thenReturn("test_module");
    when(testInfo.properties()).thenReturn(testProperties);
    when(jobInfo.properties()).thenReturn(jobProperties);
    when(testInfo.resultWithCause()).thenReturn(testResultWithCause);
    when(testInfo.getRootTest()).thenReturn(testInfo);

    when(mockAndroidAdbUtil.getProperty(anyString(), any(AndroidProperty.class))).thenReturn("");
    when(mockAndroidAdbUtil.getProperty(anyString(), ArgumentMatchers.<ImmutableList<String>>any()))
        .thenReturn("");

    decorator =
        new AndroidBusinessLogicSkipModuleDecorator(
            decoratedDriver,
            testInfo,
            mockAndroidSystemSpecUtil,
            mockAndroidAdbUtil,
            mockAndroidPackageManagerUtil,
            mockFetcher);
  }

  @Test
  public void run_noBusinessLogicUrl_callsGetDecoratedRun() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(mockFetcher, never())
        .fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any());
  }

  @Test
  public void run_fetchFails_callsGetDecoratedRun() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.newBuilder()
            .setBusinessLogicUrl("http://fake-url/logic")
            .setIgnoreBusinessLogicFailure(true)
            .build();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);

    when(mockFetcher.fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any()))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_BUSINESS_LOGIC_SKIP_MODULE_DECORATOR_FETCH_ERROR,
                "Failed to fetch"));

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_skipConditionMet_skipsModule() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.newBuilder()
            .setBusinessLogicUrl("http://fake-url/logic")
            .setConfigFilename("AndroidBusinessLogicSkipModuleDecorator")
            .build();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);

    String jsonLogic =
        """
        {
          "businessLogicRulesLists": [
            {
              "testName": "com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidBusinessLogicSkipModuleDecorator#AndroidBusinessLogicSkipModuleDecorator",
              "businessLogicRules": [
                {
                  "ruleConditions": [],
                  "ruleActions": [
                    {
                      "methodName": "skipTest",
                      "methodArgs": ["not supported"]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;
    when(mockFetcher.fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any()))
        .thenReturn(jsonLogic);

    decorator.run(testInfo);

    verify(testResultWithCause, Mockito.times(2))
        .setNonPassing(eq(TestResult.SKIP), any(MobileHarnessException.class));
    verify(decoratedDriver, never()).run(any());
  }

  @Test
  public void run_urlWithSuiteNamePlaceholder_replacesPlaceholder() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.newBuilder()
            .setBusinessLogicUrl("http://fake-url/{suite-name}/logic")
            .setXtsSuiteInfo("suite_name=cts,suite_version=12.0")
            .build();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);
    when(mockFetcher.fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any()))
        .thenReturn("{}");

    decorator.run(testInfo);

    verify(mockFetcher)
        .fetchBusinessLogic(
            eq("http://fake-url/cts/logic"),
            eq(""),
            eq(""),
            eq(System.getenv("APE_API_KEY")),
            eq(Duration.ofSeconds(60)),
            eq(ImmutableSetMultimap.of("suite_version", "12.0")));
  }

  @Test
  public void run_withDeviceData_gathersParams() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.newBuilder()
            .setBusinessLogicUrl("http://fake-url/logic")
            .build();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);

    when(mockAndroidSystemSpecUtil.getSystemFeatures("fake_device_id"))
        .thenReturn(ImmutableSet.of("feature:android.hardware.type.television"));
    when(mockAndroidAdbUtil.getProperty("fake_device_id", ImmutableList.of("ro.build.version.sdk")))
        .thenReturn("30");
    when(mockAndroidPackageManagerUtil.listPackages("fake_device_id", PackageType.ALL))
        .thenReturn(ImmutableSet.of("com.google.android.gms"));
    when(mockFetcher.fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any()))
        .thenReturn("{}");

    decorator.run(testInfo);

    verify(mockFetcher)
        .fetchBusinessLogic(
            eq("http://fake-url/logic"),
            eq(""),
            eq(""),
            eq(System.getenv("APE_API_KEY")),
            eq(Duration.ofSeconds(60)),
            eq(
                ImmutableSetMultimap.<String, String>builder()
                    .put("features", "android.hardware.type.television")
                    .put("properties", "ro.build.version.sdk:30")
                    .put("packages", "com.google.android.gms")
                    .build()));
  }

  @Test
  public void run_withApiKey_passesKeyToFetcher() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.newBuilder()
            .setBusinessLogicUrl("http://fake-url/logic")
            .setBusinessLogicApiKey("fake_api_key")
            .build();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);
    when(mockFetcher.fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any()))
        .thenReturn("{}");

    decorator.run(testInfo);

    verify(mockFetcher)
        .fetchBusinessLogic(
            eq("http://fake-url/logic"),
            eq(""),
            eq("fake_api_key"),
            eq(System.getenv("APE_API_KEY")),
            eq(Duration.ofSeconds(60)),
            eq(ImmutableSetMultimap.of()));
  }

  @Test
  public void run_configFilenameMissing_fallsBackToModuleName() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.newBuilder()
            .setBusinessLogicUrl("http://fake-url/logic")
            .build();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);
    when(jobProperties.getOptional(SessionHandlerHelper.XTS_MODULE_NAME_PROP))
        .thenReturn(Optional.of("AndroidBusinessLogicSkipModuleDecorator"));

    String jsonLogic =
        """
        {
          "businessLogicRulesLists": [
            {
              "testName": "com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidBusinessLogicSkipModuleDecorator#AndroidBusinessLogicSkipModuleDecorator",
              "businessLogicRules": [
                {
                  "ruleConditions": [],
                  "ruleActions": [
                    {
                      "methodName": "skipTest",
                      "methodArgs": ["not supported"]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;
    when(mockFetcher.fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any()))
        .thenReturn(jsonLogic);

    decorator.run(testInfo);

    verify(testResultWithCause, Mockito.times(2))
        .setNonPassing(eq(TestResult.SKIP), any(MobileHarnessException.class));
    verify(decoratedDriver, never()).run(any());
  }

  @Test
  public void run_configFilenameExists_takesPrecedence() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.newBuilder()
            .setBusinessLogicUrl("http://fake-url/logic")
            .setConfigFilename("AndroidBusinessLogicSkipModuleDecorator")
            .build();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);
    when(jobProperties.getOptional(SessionHandlerHelper.XTS_MODULE_NAME_PROP))
        .thenReturn(Optional.of("DifferentModuleName"));

    String jsonLogic =
        """
        {
          "businessLogicRulesLists": [
            {
              "testName": "com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidBusinessLogicSkipModuleDecorator#AndroidBusinessLogicSkipModuleDecorator",
              "businessLogicRules": [
                {
                  "ruleConditions": [],
                  "ruleActions": [
                    {
                      "methodName": "skipTest",
                      "methodArgs": ["not supported"]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;
    when(mockFetcher.fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any()))
        .thenReturn(jsonLogic);

    decorator.run(testInfo);

    verify(testResultWithCause, Mockito.times(2))
        .setNonPassing(eq(TestResult.SKIP), any(MobileHarnessException.class));
    verify(decoratedDriver, never()).run(any());
  }

  @Test
  public void run_fullyQualifiedMethodName_skipsModule() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.newBuilder()
            .setBusinessLogicUrl("http://fake-url/logic")
            .setConfigFilename("AndroidBusinessLogicSkipModuleDecorator")
            .build();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);

    String jsonLogic =
        """
        {
          "businessLogicRulesLists": [
            {
              "testName": "com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidBusinessLogicSkipModuleDecorator#AndroidBusinessLogicSkipModuleDecorator",
              "businessLogicRules": [
                {
                  "ruleConditions": [],
                  "ruleActions": [
                    {
                      "methodName": "com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidBusinessLogicSkipModuleDecorator.skipTest",
                      "methodArgs": ["not supported"]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;
    when(mockFetcher.fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any()))
        .thenReturn(jsonLogic);

    decorator.run(testInfo);

    verify(testResultWithCause, Mockito.times(2))
        .setNonPassing(eq(TestResult.SKIP), any(MobileHarnessException.class));
    verify(decoratedDriver, never()).run(any());
  }

  @Test
  public void run_noRulesMatch_skipsModule() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.newBuilder()
            .setBusinessLogicUrl("http://fake-url/logic")
            .setConfigFilename("AndroidBusinessLogicSkipModuleDecorator")
            .build();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);
    when(mockFetcher.fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any()))
        .thenReturn("{}");

    decorator.run(testInfo);

    verify(decoratedDriver, never()).run(any());
  }

  @Test
  public void run_fetchTimeout_callsGetDecoratedRun() throws Exception {
    AndroidBusinessLogicSkipModuleDecoratorSpec spec =
        AndroidBusinessLogicSkipModuleDecoratorSpec.newBuilder()
            .setBusinessLogicUrl("http://fake-url/logic")
            .setBusinessLogicTimeoutMs(100) // 100ms timeout
            .build();
    when(jobInfo.combinedSpec(decorator, "fake_device_id")).thenReturn(spec);
    when(mockFetcher.fetchBusinessLogic(
            anyString(), anyString(), anyString(), any(), any(Duration.class), any()))
        .thenReturn("{}");

    decorator.run(testInfo);

    verify(mockFetcher)
        .fetchBusinessLogic(
            eq("http://fake-url/logic"),
            anyString(),
            anyString(),
            any(),
            eq(Duration.ofMillis(100)),
            any());
  }
}
