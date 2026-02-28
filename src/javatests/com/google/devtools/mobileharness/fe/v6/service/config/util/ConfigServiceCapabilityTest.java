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

package com.google.devtools.mobileharness.fe.v6.service.config.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ConfigServiceCapabilityTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Mock private Environment environment;

  @Before
  public void setUp() {
    flags.setAllFlags(ImmutableMap.of("fe_connect_to_config_server", "true"));
  }

  @Test
  public void isConfigServiceAvailable_googleInternal_supported() {
    when(environment.isGoogleInternal()).thenReturn(true);

    assertThat(new ConfigServiceCapability(environment, "google_1p").isConfigServiceAvailable())
        .isTrue();
    assertThat(new ConfigServiceCapability(environment, "").isConfigServiceAvailable()).isTrue();
  }

  @Test
  public void isConfigServiceAvailable_googleInternal_unsupported() {
    when(environment.isGoogleInternal()).thenReturn(true);

    assertThat(new ConfigServiceCapability(environment, "other").isConfigServiceAvailable())
        .isFalse();
  }

  @Test
  public void isConfigServiceAvailable_notGoogleInternal_supported() {
    when(environment.isGoogleInternal()).thenReturn(false);

    assertThat(new ConfigServiceCapability(environment, "").isConfigServiceAvailable()).isTrue();
  }

  @Test
  public void isConfigServiceAvailable_notGoogleInternal_unsupported() {
    when(environment.isGoogleInternal()).thenReturn(false);

    assertThat(new ConfigServiceCapability(environment, "other").isConfigServiceAvailable())
        .isFalse();
  }

  @Test
  public void checkConfigServiceAvailability_googleInternal_unsupported() {
    when(environment.isGoogleInternal()).thenReturn(true);
    ConfigServiceCapability capability =
        new ConfigServiceCapability(environment, "unsupported_env");

    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class, capability::checkConfigServiceAvailability);

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "Configuration operations are not currently supported for universe 'unsupported_env'");
  }

  @Test
  public void checkConfigServiceAvailability_notGoogleInternal_unsupported() {
    when(environment.isGoogleInternal()).thenReturn(false);
    ConfigServiceCapability capability =
        new ConfigServiceCapability(environment, "unsupported_env");

    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class, capability::checkConfigServiceAvailability);

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Configuration operations are not currently supported.");
  }

  @Test
  public void checkConfigServiceAvailability_disabledByFlag() {
    flags.setAllFlags(ImmutableMap.of("fe_connect_to_config_server", "false"));
    ConfigServiceCapability capability = new ConfigServiceCapability(environment, "");

    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class, capability::checkConfigServiceAvailability);

    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("Configuration service is currently disabled.");
  }

  @Test
  public void calculateHostUiStatus_ats() {
    when(environment.isAts()).thenReturn(true);
    ConfigServiceCapability capability = new ConfigServiceCapability(environment, "");

    HostConfigUiStatus status = capability.calculateHostUiStatus();

    assertThat(status.getHostAdmins().getVisible()).isFalse();
    assertThat(status.getDeviceConfigMode().getVisible()).isTrue();
    assertThat(status.getDeviceConfig().getSectionStatus().getVisible()).isTrue();
    assertThat(status.getHostProperties().getSectionStatus().getVisible()).isFalse();
    assertThat(status.getDeviceDiscovery().getVisible()).isFalse();
  }

  @Test
  public void calculateHostUiStatus_notAts() {
    when(environment.isAts()).thenReturn(false);
    ConfigServiceCapability capability = new ConfigServiceCapability(environment, "");

    HostConfigUiStatus status = capability.calculateHostUiStatus();

    assertThat(status.getHostAdmins().getVisible()).isTrue();
    assertThat(status.getHostAdmins().getEditability().getEditable()).isTrue();
    assertThat(status.getDeviceConfigMode().getVisible()).isTrue();
    assertThat(status.getDeviceConfig().getSectionStatus().getVisible()).isTrue();
    assertThat(status.getHostProperties().getSectionStatus().getVisible()).isTrue();
    assertThat(status.getDeviceDiscovery().getVisible()).isTrue();
  }

  @Test
  public void calculateDeviceUiStatus_ats() {
    when(environment.isAts()).thenReturn(true);
    ConfigServiceCapability capability = new ConfigServiceCapability(environment, "");

    DeviceConfigUiStatus status = capability.calculateDeviceUiStatus();

    assertThat(status.getPermissions().getVisible()).isFalse();
    assertThat(status.getWifi().getVisible()).isTrue();
    assertThat(status.getDimensions().getVisible()).isTrue();
    assertThat(status.getSettings().getVisible()).isFalse();
  }

  @Test
  public void calculateDeviceUiStatus_notAts() {
    when(environment.isAts()).thenReturn(false);
    ConfigServiceCapability capability = new ConfigServiceCapability(environment, "");

    DeviceConfigUiStatus status = capability.calculateDeviceUiStatus();

    assertThat(status.getPermissions().getVisible()).isTrue();
    assertThat(status.getPermissions().getEditability().getEditable()).isTrue();
    assertThat(status.getWifi().getVisible()).isTrue();
    assertThat(status.getDimensions().getVisible()).isTrue();
    assertThat(status.getSettings().getVisible()).isTrue();
    assertThat(status.getSettings().getEditability().getEditable()).isTrue();
  }
}
