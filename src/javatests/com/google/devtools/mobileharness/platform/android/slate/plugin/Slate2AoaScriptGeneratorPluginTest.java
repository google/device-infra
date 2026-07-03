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

package com.google.devtools.mobileharness.platform.android.slate.plugin;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link Slate2AoaScriptGeneratorPlugin}. */
@RunWith(JUnit4.class)
public final class Slate2AoaScriptGeneratorPluginTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Mock private LocalTestEndingEvent event;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Device device;
  @Mock private Result resultWithCause;

  private Slate2AoaScriptGeneratorPlugin plugin;
  private Properties testProperties;
  private Params jobParams;
  private LocalFileUtil localFileUtil;
  private File genFileDir;

  @Before
  public void setUp() throws Exception {
    genFileDir = tempFolder.newFolder("genfiles");
    localFileUtil = new LocalFileUtil();

    testProperties = new Properties(new Timing());
    jobParams = new Params(new Timing());

    when(event.getTest()).thenReturn(testInfo);
    when(event.getLocalDevices()).thenReturn(ImmutableMap.of("device_id", device));
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.getGenFileDir()).thenReturn(genFileDir.getAbsolutePath());
    when(testInfo.properties()).thenReturn(testProperties);
    when(jobInfo.params()).thenReturn(jobParams);
    when(testInfo.log()).thenReturn(new Log(new Timing()));
    when(testInfo.resultWithCause()).thenReturn(resultWithCause);

    plugin = new Slate2AoaScriptGeneratorPlugin();
  }

  /**
   * Verifies that when a test ends successfully and contains UI interaction logs, the plugin parses
   * click, write, sleep, home, back, enter, and swipe commands into aoa_script.txt.
   */
  @Test
  public void onTestEnding_success_generatesAoaScriptAndResolvesTaxonomy() throws Exception {
    when(resultWithCause.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    when(device.getDimension("hardware_sku")).thenReturn(ImmutableList.of("PVT"));
    when(device.getDimension("sim_state")).thenReturn(ImmutableList.of("READY"));

    File slateLog = new File(genFileDir, "slate_run.log");
    localFileUtil.writeToFile(
        slateLog.getAbsolutePath(),
        String.join(
                "\n",
                ImmutableList.of(
                    "INFO: Starting UI automation",
                    "Action performed: tap 500, 800",
                    "Action performed: tap 1440, 3120",
                    "Action performed: write \"my_password\"",
                    "Action performed: sleep 1000",
                    "Action performed: NAVIGATE_HOME",
                    "Action performed: press.back",
                    "Action performed: KEYBOARD_ENTER",
                    "Action performed: swipe 100, 800, 100, 200 duration 400",
                    "Action performed: swipe 1080, 2400, 1080, 100 duration 500"))
            + "\n");

    plugin.onTestEnding(event);

    assertThat(testProperties.get("aoa_device_stage")).isEqualTo("PVT");
    assertThat(testProperties.get("aoa_sim_config")).isEqualTo("sim");

    File aoaScript = new File(genFileDir, "aoa_script.txt");
    assertThat(aoaScript.exists()).isTrue();

    List<String> generatedCommands =
        localFileUtil.readLineListFromFile(aoaScript.getAbsolutePath());
    assertThat(generatedCommands)
        .containsExactly(
            "click 500 800",
            "click 1440 3120",
            "write my_password",
            "sleep 1000",
            "home",
            "back",
            "key 66",
            "swipe 100 800 400 100 200",
            "swipe 1080 2400 500 1080 100")
        .inOrder();
  }

  /**
   * Verifies that when a test halts with a failure status, plugin post-processing is safely
   * bypassed without writing any script files.
   */
  @Test
  public void onTestEnding_failedTest_skipsGeneration() throws Exception {
    when(resultWithCause.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.FAIL,
                new MobileHarnessException(BasicErrorId.COMMAND_EXEC_FAIL, "simulated fail")));

    plugin.onTestEnding(event);
    File aoaScript = new File(genFileDir, "aoa_script.txt");
    assertThat(aoaScript.exists()).isFalse();
  }

  /**
   * Confirms resilience against missing log files when a test passes without leaving a raw
   * slate_run.log artifact behind.
   */
  @Test
  public void onTestEnding_missingLogFile_skipsWithoutException() throws Exception {
    when(resultWithCause.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    plugin.onTestEnding(event);

    File aoaScript = new File(genFileDir, "aoa_script.txt");
    assertThat(aoaScript.exists()).isFalse();
  }

  /**
   * Verifies filtering accuracy against noisy input logs (timestamps, debug headers, unformatted
   * lines), ensuring only matched UI actions are output in exact order.
   */
  @Test
  public void onTestEnding_mixedLogs_onlyExtractsActions() throws Exception {
    when(resultWithCause.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    when(device.getDimension("hardware_sku")).thenReturn(ImmutableList.of("EVT"));
    when(device.getDimension("sim_state")).thenReturn(ImmutableList.of("LOADED"));

    File slateLog = new File(genFileDir, "slate_run.log");
    localFileUtil.writeToFile(
        slateLog.getAbsolutePath(),
        String.join(
                "\n",
                ImmutableList.of(
                    "2026-06-29 [DEBUG] Starting initialization",
                    "Action performed: tap 10, 20",
                    "2026-06-29 [INFO] Page loaded",
                    "Action performed: wait 500",
                    "Unhandled log line",
                    "Action performed: write 'test'"))
            + "\n");

    plugin.onTestEnding(event);

    assertThat(testProperties.get("aoa_device_stage")).isEqualTo("EVT");
    assertThat(testProperties.get("aoa_sim_config")).isEqualTo("sim");

    File aoaScript = new File(genFileDir, "aoa_script.txt");
    assertThat(aoaScript.exists()).isTrue();
    assertThat(localFileUtil.readLineListFromFile(aoaScript.getAbsolutePath()))
        .containsExactly("click 10 20", "sleep 500", "write test")
        .inOrder();
  }

  /**
   * Verifies that when event.getLocalDevices() is empty, onTestEnding returns safely without
   * generating scripts or throwing an exception.
   */
  @Test
  public void onTestEnding_noLocalDevice_skipsWithoutException() throws Exception {
    when(resultWithCause.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    when(event.getLocalDevices()).thenReturn(ImmutableMap.of());

    plugin.onTestEnding(event);

    File aoaScript = new File(genFileDir, "aoa_script.txt");
    assertThat(aoaScript.exists()).isFalse();
  }

  /**
   * Verifies that explicit job parameters take precedence over device hardware dimensions when
   * resolving device stage and SIM taxonomy.
   */
  @Test
  public void onTestEnding_explicitParams_overrideDeviceDimensions() throws Exception {
    when(resultWithCause.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    when(device.getDimension("hardware_sku")).thenReturn(ImmutableList.of("PVT"));
    when(device.getDimension("sim_state")).thenReturn(ImmutableList.of("READY"));

    jobParams.add("aoa_device_stage", "DVT_OVERRIDE");
    jobParams.add("aoa_sim_config", "no-sim");

    File slateLog = new File(genFileDir, "slate_run.log");
    localFileUtil.writeToFile(slateLog.getAbsolutePath(), "Action performed: tap 10, 20\n");

    plugin.onTestEnding(event);

    assertThat(testProperties.get("aoa_device_stage")).isEqualTo("DVT_OVERRIDE");
    assertThat(testProperties.get("aoa_sim_config")).isEqualTo("no-sim");
  }

  /**
   * Verifies that when slate_run.log exists but contains zero matching UI actions, script file
   * creation is skipped without error.
   */
  @Test
  public void onTestEnding_noMatchingActionsInLog_skipsScriptFileCreation() throws Exception {
    when(resultWithCause.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    when(device.getDimension("hardware_sku")).thenReturn(ImmutableList.of("EVT"));

    File slateLog = new File(genFileDir, "slate_run.log");
    localFileUtil.writeToFile(
        slateLog.getAbsolutePath(),
        "2026-06-29 [INFO] Initialized\n2026-06-29 [DEBUG] No UI interactions performed\n");

    plugin.onTestEnding(event);

    File aoaScript = new File(genFileDir, "aoa_script.txt");
    assertThat(aoaScript.exists()).isFalse();
  }
}
