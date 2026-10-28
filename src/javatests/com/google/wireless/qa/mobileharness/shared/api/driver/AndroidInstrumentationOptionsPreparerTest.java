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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidInstrumentation.ANDROID_INSTRUMENTATION_SMART_SHARD_TEST_NAMES;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.driver.AndroidInstrumentationOptionsPreparer.PreparedInstrumentationOptions;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidInstrumentationDriverSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfoMocker;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidInstrumentationOptionsPreparerTest {
  private static final String DEVICE_ID = "device_id";
  private static final int SDK_VERSION = 34;
  private static final String TMP_DIR = "/tmp/test_dir";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private AndroidInstrumentationUtil androidInstrumentationUtil;

  private final LocalFileUtil fileUtil = new LocalFileUtil();
  private final TestInfo testInfo = TestInfoMocker.mockTestInfo();
  private final JobInfo jobInfo = testInfo.jobInfo();
  private AndroidInstrumentationOptionsPreparer preparer;

  @Before
  public void setUp() throws Exception {
    when(testInfo.locator())
        .thenReturn(
            new TestLocator("test_id", "com.example.DefaultTest#testCase", jobInfo.locator()));
    when(testInfo.getTmpFileDir()).thenReturn(TMP_DIR);
    preparer =
        new AndroidInstrumentationOptionsPreparer(
            new AndroidInstrumentationTestFileSpiller(fileUtil, androidInstrumentationUtil));
  }

  @Test
  public void prepare_singletonOptions_preservesInsertionOrderInPropertyOptions() throws Exception {
    jobInfo
        .params()
        .add(AndroidInstrumentationDriverSpec.PARAM_OPTIONS, "zKey=last,aKey=first\\,second");

    PreparedInstrumentationOptions prepared = prepareOptions();

    assertThat(prepared.optionMaps()).hasSize(1);
    assertThat(prepared.optionMaps().get(0).orElseThrow())
        .containsExactly("zKey", "last", "aKey", "first,second")
        .inOrder();
    assertThat(testInfo.properties().get(AndroidInstrumentationDriverSpec.PROPERTY_OPTIONS))
        .isEqualTo("zKey=last,aKey=first,second");
    assertThat(testInfo.properties().has(AndroidInstrumentationDriverSpec.PROPERTY_ITER_COUNT))
        .isFalse();
  }

  @Test
  public void prepare_sequentialEscapedCommasAndSkippedMap_decodesOptionsAndRecordsProperties()
      throws Exception {
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_0", "k=a\\,b");
    jobInfo
        .params()
        .add(
            AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_1",
            AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR);
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_2", "   ");

    PreparedInstrumentationOptions prepared = prepareOptions();

    assertThat(prepared.testTarget()).isEqualTo("com.example.DefaultTest#testCase");
    assertThat(prepared.optionMaps()).hasSize(2);
    assertThat(prepared.optionMaps().get(0).orElseThrow()).containsExactly("k", "a,b");
    assertThat(prepared.optionMaps().get(1)).isEmpty();
    assertThat(testInfo.properties().get(AndroidInstrumentationDriverSpec.PROPERTY_ITER_COUNT))
        .isEqualTo("2");
    assertThat(testInfo.properties().get(AndroidInstrumentationDriverSpec.PROPERTY_OPTIONS + "_0"))
        .isEqualTo("k=a,b");
    assertThat(testInfo.properties().get(AndroidInstrumentationDriverSpec.PROPERTY_OPTIONS + "_1"))
        .isEqualTo(AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR);
  }

  @Test
  public void prepare_testNameAll_returnsNullTestTarget() throws Exception {
    when(testInfo.locator()).thenReturn(new TestLocator("test_id", "ALL", jobInfo.locator()));

    PreparedInstrumentationOptions prepared = prepareOptions();

    assertThat(prepared.testTarget()).isNull();
    assertThat(prepared.optionMaps().get(0).orElseThrow()).isEmpty();
  }

  @Test
  public void prepare_testNameSmallWithSkippedMap_addsSizeOnlyToNonSkippedMaps() throws Exception {
    when(testInfo.locator()).thenReturn(new TestLocator("test_id", "SMALL", jobInfo.locator()));
    jobInfo.params().add(AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_0", "foo=bar");
    jobInfo
        .params()
        .add(
            AndroidInstrumentationDriverSpec.PARAM_OPTIONS + "_1",
            AndroidInstrumentationDriverSpec.SKIPPED_OPTION_STR);

    PreparedInstrumentationOptions prepared = prepareOptions();

    assertThat(prepared.testTarget()).isNull();
    assertThat(prepared.optionMaps().get(0).orElseThrow())
        .containsExactly("foo", "bar", "size", "small");
    assertThat(prepared.optionMaps().get(1)).isEmpty();
  }

  @Test
  public void prepare_smartShardPropertyPresent_usesSmartShardTestNames() throws Exception {
    testInfo
        .properties()
        .add(
            ANDROID_INSTRUMENTATION_SMART_SHARD_TEST_NAMES,
            "com.example.ShardA,com.example.ShardB");

    PreparedInstrumentationOptions prepared = prepareOptions();

    assertThat(prepared.testTarget()).isEqualTo("com.example.ShardA,com.example.ShardB");
  }

  private PreparedInstrumentationOptions prepareOptions()
      throws MobileHarnessException, InterruptedException {
    return preparer.prepare(testInfo, DEVICE_ID, SDK_VERSION, "/sdcard");
  }
}
