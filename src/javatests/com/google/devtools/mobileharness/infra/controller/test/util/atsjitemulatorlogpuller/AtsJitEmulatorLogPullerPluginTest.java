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

package com.google.devtools.mobileharness.infra.controller.test.util.atsjitemulatorlogpuller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidJitEmulator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.LocalTestEndingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AtsJitEmulatorLogPullerPluginTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private LocalTestEndingEvent event;
  @Mock private TestInfo testInfo;
  @Mock private AndroidJitEmulator jitEmulator;
  @Mock private Device otherDevice;

  private AtsJitEmulatorLogPullerPlugin plugin;

  @Before
  public void setUp() {
    plugin = new AtsJitEmulatorLogPullerPlugin();
    when(event.getTest()).thenReturn(testInfo);
  }

  @Test
  public void onTestEnding_androidJitEmulator_pullsLogs() throws Exception {
    when(event.getLocalDevices()).thenReturn(ImmutableMap.of("device_1", jitEmulator));

    plugin.onTestEnding(event);

    verify(jitEmulator).pullCvdLogs(testInfo);
  }

  @Test
  public void onTestEnding_otherDevice_doesNothing() throws Exception {
    when(event.getLocalDevices()).thenReturn(ImmutableMap.of("device_2", otherDevice));

    plugin.onTestEnding(event);

    verifyNoInteractions(otherDevice);
  }

  @Test
  public void onTestEnding_multipleDevices_pullsLogsForJitEmulatorOnly() throws Exception {
    when(event.getLocalDevices())
        .thenReturn(ImmutableMap.of("device_1", jitEmulator, "device_2", otherDevice));

    plugin.onTestEnding(event);

    verify(jitEmulator).pullCvdLogs(testInfo);
    verifyNoInteractions(otherDevice);
  }
}
