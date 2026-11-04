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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidDisplayDensityDecorator.PhysicalDisplay;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidDisplayDensityDecorator}. */
@RunWith(JUnit4.class)
public final class AndroidDisplayDensityDecoratorTest {

  private static final String DEVICE_ID = "R5CW1234ABC";

  private static final String INNER_DISPLAY_INFO =
      "DisplayDeviceInfo{\"Built-in Screen\": uniqueId=\"local:4619827259835644672\", 1812 x 2176,"
          + " modeId 1, renderFrameRate 120.0, defaultModeId 1, modes [{id=1, width=1812,"
          + " height=2176, fps=120.0}], deviceProductInfo DeviceProductInfo{name=,"
          + " manufacturerPnpId=, productId=, modelYear=null}, state ON, committedState ON,"
          + " frameRateOverride , brightnessMinimum 0.0, density 420, 403.411 x 399.737 dpi,"
          + " appVsyncOff 1000000, presDeadline 16666666, touch INTERNAL, rotation 0, type"
          + " INTERNAL, FLAG_DEFAULT_DISPLAY}";

  private static final String OUTER_DISPLAY_INFO =
      "DisplayDeviceInfo{\"Built-in Screen\": uniqueId=\"local:4619827259835644673\", 904 x 2316,"
          + " modeId 2, renderFrameRate 120.0, defaultModeId 2, modes [{id=2, width=904,"
          + " height=2316, fps=120.0}], state OFF, committedState OFF, density 380, 386.0 x 387.0"
          + " dpi, appVsyncOff 1000000, presDeadline 16666666, touch INTERNAL, rotation 0, type"
          + " INTERNAL}";

  private static final String VIRTUAL_DISPLAY_INFO =
      "DisplayDeviceInfo{\"VirtualDisplay\": uniqueId=\"virtual:com.android.systemui,10,foo\","
          + " 720 x 1280, modeId 3, defaultModeId 3, modes [{id=3, width=720, height=1280,"
          + " fps=60.0}], state ON, density 320, 320.0 x 320.0 dpi, type VIRTUAL}";

  private static final String FOLDABLE_DUMPSYS =
      String.join(
          "\n",
          "DISPLAY MANAGER (dumpsys display)",
          "Display Devices: size=3",
          "  DisplayDeviceAdapter LocalDisplayAdapter",
          "    mDisplayDeviceInfo=" + INNER_DISPLAY_INFO,
          "  DisplayDeviceAdapter LocalDisplayAdapter",
          "    mDisplayDeviceInfo=" + OUTER_DISPLAY_INFO,
          "  DisplayDeviceAdapter VirtualDisplayAdapter",
          "    mDisplayDeviceInfo=" + VIRTUAL_DISPLAY_INFO,
          "Logical Displays: size=1",
          "  Display 0:",
          "    mPrimaryDisplayDeviceInfo=" + INNER_DISPLAY_INFO);

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private Driver decoratedDriver;
  @Mock private Device device;
  @Mock private TestInfo testInfo;
  @Mock private Properties properties;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Log log;

  private AndroidDisplayDensityDecorator decorator;

  @Before
  public void setUp() {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.log()).thenReturn(log);
    when(testInfo.properties()).thenReturn(properties);
    decorator = new AndroidDisplayDensityDecorator(decoratedDriver, testInfo, androidAdbUtil);
  }

  @Test
  public void parseBuiltInDisplays_foldable_dedupesAndSkipsVirtualDisplays() {
    assertThat(AndroidDisplayDensityDecorator.parseBuiltInDisplays(FOLDABLE_DUMPSYS))
        .containsExactly(
            new PhysicalDisplay("Built-in Screen", "local:4619827259835644672", 1812, 2176, 420),
            new PhysicalDisplay("Built-in Screen", "local:4619827259835644673", 904, 2316, 380))
        .inOrder();
  }

  @Test
  public void getDisplayDimensions_foldable_returnsInnerAndOuter() {
    assertThat(AndroidDisplayDensityDecorator.getDisplayDimensions(FOLDABLE_DUMPSYS))
        .containsExactly(
            "display_count", "2",
            "inner_screen_density", "420",
            "inner_screen_size", "1812x2176",
            "outer_screen_density", "380",
            "outer_screen_size", "904x2316");
  }

  @Test
  public void getDisplayDimensions_singleDisplay_returnsCountOnly() {
    assertThat(
            AndroidDisplayDensityDecorator.getDisplayDimensions(
                "Display Devices: size=1\n  mDisplayDeviceInfo=" + INNER_DISPLAY_INFO))
        .containsExactly("display_count", "1");
  }

  @Test
  public void getDisplayDimensions_unparsableOutput_returnsZeroCount() {
    assertThat(AndroidDisplayDensityDecorator.getDisplayDimensions("Can't find service: display"))
        .containsExactly("display_count", "0");
  }

  @Test
  public void run_foldable_addsDimensionPropertiesAndRunsDecoratedDriver() throws Exception {
    when(androidAdbUtil.dumpSys(DEVICE_ID, DumpSysType.DISPLAY)).thenReturn(FOLDABLE_DUMPSYS);

    decorator.run(testInfo);

    verify(properties).add("dimension_display_count", "2");
    verify(properties).add("dimension_inner_screen_density", "420");
    verify(properties).add("dimension_inner_screen_size", "1812x2176");
    verify(properties).add("dimension_outer_screen_density", "380");
    verify(properties).add("dimension_outer_screen_size", "904x2316");
    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_dumpsysFails_doesNotFailTest() throws Exception {
    when(androidAdbUtil.dumpSys(DEVICE_ID, DumpSysType.DISPLAY))
        .thenThrow(
            new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_DUMPSYS_ERROR, "error"));

    decorator.run(testInfo);

    verify(properties, never()).add(anyString(), anyString());
    verify(decoratedDriver).run(testInfo);
  }
}
