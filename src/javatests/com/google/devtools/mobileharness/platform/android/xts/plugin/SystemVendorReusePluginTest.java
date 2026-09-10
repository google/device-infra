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

package com.google.devtools.mobileharness.platform.android.xts.plugin;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsPropertyName.Job;
import com.google.devtools.mobileharness.platform.android.xts.suite.subplan.SubPlan;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class SystemVendorReusePluginTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private LocalTestStartingEvent event;

  private JobInfo jobInfo;
  private TestInfo testInfo;
  private SystemVendorReusePlugin plugin;

  @Before
  public void setUp() throws Exception {
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder().setDevice("AndroidRealDevice").setDriver("NoOpDriver").build())
            .setTiming(new Timing())
            .build();
    testInfo = jobInfo.tests().add("test_id", "test_name");
    when(event.getTest()).thenReturn(testInfo);
    plugin = new SystemVendorReusePlugin();
  }

  @Test
  public void onTestStarting_svrDisabled_doesNothing() throws Exception {
    jobInfo.params().add(XtsConstants.IS_SYSTEM_VENDOR_REUSE_ENABLED, "false");

    plugin.onTestStarting(event);

    assertThat(testInfo.properties().getAll()).isEmpty();
  }

  @Test
  public void onTestStarting_svrEnabled_generatesSubplan() throws Exception {
    File subplanFile = new File(tempFolder.newFolder("subplans"), "svr_session_123.xml");
    jobInfo.params().add(XtsConstants.IS_SYSTEM_VENDOR_REUSE_ENABLED, "true");
    jobInfo.properties().add(Job.SESSION_ID, "session_123");
    jobInfo.properties().add(Job.SVR_SUBPLAN_FILE_PATH, subplanFile.getAbsolutePath());

    plugin.onTestStarting(event);

    assertThat(subplanFile.exists()).isTrue();

    SubPlan subPlan = new SubPlan();
    try (InputStream inputStream = new FileInputStream(subplanFile)) {
      subPlan.parse(inputStream);
    }
    assertThat(subPlan.getIncludeFiltersMultimap().keySet())
        .containsExactly("CtsSampleDeviceTestCases", "CtsGestureTestCases");
  }

  @Test
  public void onTestStarting_missingSessionId_throwsException() throws Exception {
    File subplanFile = new File(tempFolder.newFolder("subplans_no_session"), "svr_nosession.xml");
    jobInfo.params().add(XtsConstants.IS_SYSTEM_VENDOR_REUSE_ENABLED, "true");
    jobInfo.properties().add(Job.SVR_SUBPLAN_FILE_PATH, subplanFile.getAbsolutePath());

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> plugin.onTestStarting(event));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.SYSTEM_VENDOR_REUSE_PLUGIN_PROPERTY_NOT_FOUND);
  }

  @Test
  public void onTestStarting_missingSubplanFilePath_throwsException() {
    jobInfo.params().add(XtsConstants.IS_SYSTEM_VENDOR_REUSE_ENABLED, "true");
    jobInfo.properties().add(Job.SESSION_ID, "session_123");

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> plugin.onTestStarting(event));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.SYSTEM_VENDOR_REUSE_PLUGIN_PROPERTY_NOT_FOUND);
  }

  @Test
  public void onTestStarting_emptyModules_throwsException() throws Exception {
    File subplanFile = new File(tempFolder.newFolder("subplans_empty"), "svr_empty.xml");
    jobInfo.params().add(XtsConstants.IS_SYSTEM_VENDOR_REUSE_ENABLED, "true");
    jobInfo.properties().add(Job.SESSION_ID, "session_123");
    jobInfo.properties().add(Job.SVR_SUBPLAN_FILE_PATH, subplanFile.getAbsolutePath());

    SystemVendorReusePlugin emptyPlugin =
        new SystemVendorReusePlugin(new LocalFileUtil()) {
          @Override
          ImmutableList<String> parseModulesFromJson(String json) {
            return ImmutableList.of();
          }
        };

    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> emptyPlugin.onTestStarting(event));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.SYSTEM_VENDOR_REUSE_PLUGIN_NO_CANDIDATE_MODULES);
  }

  @Test
  public void parseModulesFromJson_parsesValidAndFiltersInvalid() {
    assertThat(plugin.parseModulesFromJson("{}")).isEmpty();
    assertThat(plugin.parseModulesFromJson("{\"modules\": []}")).isEmpty();
    assertThat(plugin.parseModulesFromJson("{\"modules\": [\" \", null, \"CtsModule\"]}"))
        .containsExactly("CtsModule");
    assertThat(plugin.parseModulesFromJson("{\"modules\": [\"CtsModule\", \"CtsModule\"]}"))
        .containsExactly("CtsModule");
    assertThat(plugin.parseModulesFromJson("{invalid_json")).isEmpty();
  }

  @Test
  public void constructor_visibleForTestingInstantiatesCorrectly() {
    SystemVendorReusePlugin customPlugin = new SystemVendorReusePlugin(new LocalFileUtil());
    assertThat(customPlugin).isNotNull();
  }
}
