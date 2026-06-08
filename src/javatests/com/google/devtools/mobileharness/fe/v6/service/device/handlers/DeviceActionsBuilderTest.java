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

/*
 * Copyright 2026 Google LLC
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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceActions;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.FlashActionInfo;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class DeviceActionsBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private FlashButtonBuilder mockFlashButtonBuilder;
  @Mock private LogcatButtonBuilder mockLogcatButtonBuilder;
  @Mock private QuarantineButtonBuilder mockQuarantineButtonBuilder;
  @Mock private ScreenshotButtonBuilder mockScreenshotButtonBuilder;
  @Mock private ConfigurationButtonBuilder mockConfigurationButtonBuilder;
  @Mock private RemoteControlButtonBuilder mockRemoteControlButtonBuilder;
  @Mock private DeviceDecommissionButtonBuilder mockDecommissionButtonBuilder;

  private DeviceActionsBuilder deviceActionsBuilder;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();
  private static final DeviceInfo DEVICE_INFO = DeviceInfo.getDefaultInstance();

  @Before
  public void setUp() {
    deviceActionsBuilder =
        new DeviceActionsBuilder(
            mockFlashButtonBuilder,
            mockLogcatButtonBuilder,
            mockQuarantineButtonBuilder,
            mockScreenshotButtonBuilder,
            mockConfigurationButtonBuilder,
            mockRemoteControlButtonBuilder,
            mockDecommissionButtonBuilder);
  }

  @Test
  public void buildDeviceActions_success() {
    ActionButtonState screenshotState =
        ActionButtonState.newBuilder().setTooltip("screenshot").build();
    ActionButtonState logcatState = ActionButtonState.newBuilder().setTooltip("logcat").build();
    FlashActionInfo flashState =
        FlashActionInfo.newBuilder()
            .setState(ActionButtonState.newBuilder().setTooltip("flash"))
            .build();
    ActionButtonState remoteControlState =
        ActionButtonState.newBuilder().setTooltip("remote").build();
    ActionButtonState quarantineState =
        ActionButtonState.newBuilder().setTooltip("quarantine").build();
    ActionButtonState configurationState =
        ActionButtonState.newBuilder().setTooltip("config").build();
    ActionButtonState decommissionState =
        ActionButtonState.newBuilder().setTooltip("decommission").build();

    when(mockScreenshotButtonBuilder.build(DEVICE_INFO, UNIVERSE)).thenReturn(screenshotState);
    when(mockLogcatButtonBuilder.build(DEVICE_INFO, UNIVERSE)).thenReturn(logcatState);
    when(mockFlashButtonBuilder.build(DEVICE_INFO, UNIVERSE)).thenReturn(flashState);
    when(mockRemoteControlButtonBuilder.build(DEVICE_INFO, UNIVERSE))
        .thenReturn(remoteControlState);
    when(mockQuarantineButtonBuilder.build(DEVICE_INFO, UNIVERSE)).thenReturn(quarantineState);
    when(mockConfigurationButtonBuilder.build(DEVICE_INFO, UNIVERSE))
        .thenReturn(configurationState);
    when(mockDecommissionButtonBuilder.build(DEVICE_INFO, UNIVERSE)).thenReturn(decommissionState);

    DeviceActions result = deviceActionsBuilder.buildDeviceActions(DEVICE_INFO, UNIVERSE);

    DeviceActions expected =
        DeviceActions.newBuilder()
            .setScreenshot(screenshotState)
            .setLogcat(logcatState)
            .setFlash(flashState)
            .setRemoteControl(remoteControlState)
            .setQuarantine(quarantineState)
            .setConfiguration(configurationState)
            .setDecommission(decommissionState)
            .build();

    assertThat(result).isEqualTo(expected);
  }
}
