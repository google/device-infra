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

package com.google.wireless.qa.mobileharness.shared.api.validator.job;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.job.in.Decorators;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidReverseUsbTetheringDecoratorSpec;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AndroidReverseUsbTetheringDecoratorJobValidator}. */
@RunWith(TestParameterInjector.class)
public class AndroidReverseUsbTetheringDecoratorJobValidatorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private JobInfo jobInfo;
  @Mock private SubDeviceSpecs subDeviceSpecs;

  private AndroidReverseUsbTetheringDecoratorJobValidator validator;

  @Before
  public void setUp() {
    validator = new AndroidReverseUsbTetheringDecoratorJobValidator();
    when(jobInfo.subDeviceSpecs()).thenReturn(subDeviceSpecs);
    when(subDeviceSpecs.getAllSubDevices()).thenReturn(ImmutableList.of());
  }

  @Test
  public void validate_defaultSpec_passes() throws Exception {
    when(jobInfo.combinedSpecForDevices(eq(validator), any()))
        .thenReturn(ImmutableList.of(AndroidReverseUsbTetheringDecoratorSpec.getDefaultInstance()));

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_conflictsWithSetWifiOnSameSubDevice_fails() throws Exception {
    SubDeviceSpec subDevice =
        mockSubDeviceWithDecorators(
            ImmutableList.of("AndroidReverseUsbTetheringDecorator", "AndroidSetWifiDecorator"));
    when(subDeviceSpecs.getAllSubDevices()).thenReturn(ImmutableList.of(subDevice));
    when(jobInfo.combinedSpecForDevices(eq(validator), any()))
        .thenReturn(ImmutableList.of(AndroidReverseUsbTetheringDecoratorSpec.getDefaultInstance()));

    assertThat(validator.validate(jobInfo))
        .containsExactly(
            "AndroidReverseUsbTetheringDecorator and AndroidSetWifiDecorator cannot both be"
                + " configured on the same device.");
  }

  @Test
  public void validate_setWifiOnDifferentSubDevice_passes() throws Exception {
    SubDeviceSpec usbDevice =
        mockSubDeviceWithDecorators(ImmutableList.of("AndroidReverseUsbTetheringDecorator"));
    SubDeviceSpec wifiDevice =
        mockSubDeviceWithDecorators(ImmutableList.of("AndroidSetWifiDecorator"));
    when(subDeviceSpecs.getAllSubDevices()).thenReturn(ImmutableList.of(usbDevice, wifiDevice));
    when(jobInfo.combinedSpecForDevices(eq(validator), any()))
        .thenReturn(ImmutableList.of(AndroidReverseUsbTetheringDecoratorSpec.getDefaultInstance()));

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_nonPositiveTimeout_fails() throws Exception {
    AndroidReverseUsbTetheringDecoratorSpec spec =
        AndroidReverseUsbTetheringDecoratorSpec.newBuilder().setConnectivityTimeoutSec(0).build();
    when(jobInfo.combinedSpecForDevices(eq(validator), any())).thenReturn(ImmutableList.of(spec));

    assertThat(validator.validate(jobInfo))
        .containsExactly("Please set connectivity_timeout_sec to a positive integer.");
  }

  @Test
  public void validate_validCheckHost_passes() throws Exception {
    AndroidReverseUsbTetheringDecoratorSpec spec =
        AndroidReverseUsbTetheringDecoratorSpec.newBuilder()
            .setConnectivityCheckHost("connectivitycheck.gstatic.com")
            .build();
    when(jobInfo.combinedSpecForDevices(eq(validator), any())).thenReturn(ImmutableList.of(spec));

    assertThat(validator.validate(jobInfo)).isEmpty();
  }

  @Test
  public void validate_invalidCheckHost_fails(
      @TestParameter({
            "www.google.com; rm -rf /",
            "-f",
            "--help",
            "-",
            "example.com-",
            ".example.com",
            "example.com."
          })
          String invalidHost)
      throws Exception {
    AndroidReverseUsbTetheringDecoratorSpec spec =
        AndroidReverseUsbTetheringDecoratorSpec.newBuilder()
            .setConnectivityCheckHost(invalidHost)
            .build();
    when(jobInfo.combinedSpecForDevices(eq(validator), any())).thenReturn(ImmutableList.of(spec));

    assertThat(validator.validate(jobInfo))
        .containsExactly(
            "Invalid connectivity_check_host: only ASCII letters, digits, '.', and '-' are"
                + " allowed, and it must start and end with a letter or digit.");
  }

  private static SubDeviceSpec mockSubDeviceWithDecorators(ImmutableList<String> decoratorNames) {
    SubDeviceSpec subDevice = mock(SubDeviceSpec.class);
    Decorators decorators = mock(Decorators.class);
    when(subDevice.decorators()).thenReturn(decorators);
    when(decorators.getAll()).thenReturn(decoratorNames);
    return subDevice;
  }
}
