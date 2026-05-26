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

package com.google.devtools.mobileharness.infra.ats.console.util.command;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.console.util.command.DeviceFilterParser.DeviceFilterInfo;
import com.google.devtools.mobileharness.infra.controller.device.proto.DeviceFilterSetting;
import com.google.devtools.mobileharness.infra.controller.device.proto.DeviceListFilter.FilterTypeCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceFilterParserTest {

  @Test
  public void parse_emptyArgs_returnsDefaultSettingAndEmptyArgs() {
    DeviceFilterInfo info = DeviceFilterParser.parse(ImmutableList.of());

    assertThat(info.remainingArgs()).isEmpty();
    DeviceFilterSetting setting = info.deviceFilterSetting();
    assertThat(setting.hasAllowlistFilter()).isFalse();
    assertThat(setting.hasBlocklistFilter()).isFalse();
  }

  @Test
  public void parse_onlyDeviceAllowlist_extractsSettingAndRemovesOption() {
    DeviceFilterInfo info =
        DeviceFilterParser.parse(ImmutableList.of("--xts_device_allowlist=id1,id2"));

    assertThat(info.remainingArgs()).isEmpty();
    DeviceFilterSetting setting = info.deviceFilterSetting();
    assertThat(setting.hasAllowlistFilter()).isTrue();
    assertThat(setting.getAllowlistFilter().getFilterTypeCase())
        .isEqualTo(FilterTypeCase.EXPLICIT_FILTER);
    assertThat(setting.getAllowlistFilter().getExplicitFilter().getControlIdsList())
        .containsExactly("id1", "id2");
    assertThat(setting.hasBlocklistFilter()).isFalse();
  }

  @Test
  public void parse_onlyDeviceBlocklist_extractsSettingAndRemovesOption() {
    DeviceFilterInfo info =
        DeviceFilterParser.parse(ImmutableList.of("--xts_device_blocklist=id3"));

    assertThat(info.remainingArgs()).isEmpty();
    DeviceFilterSetting setting = info.deviceFilterSetting();
    assertThat(setting.hasAllowlistFilter()).isFalse();
    assertThat(setting.hasBlocklistFilter()).isTrue();
    assertThat(setting.getBlocklistFilter().getFilterTypeCase())
        .isEqualTo(FilterTypeCase.EXPLICIT_FILTER);
    assertThat(setting.getBlocklistFilter().getExplicitFilter().getControlIdsList())
        .containsExactly("id3");
  }

  @Test
  public void parse_allowlistAndBlocklistWithRemainingCommand_extractsBothAndRetainsCommand() {
    DeviceFilterInfo info =
        DeviceFilterParser.parse(
            ImmutableList.of(
                "--xts_device_allowlist=id1",
                "run",
                "cts",
                "-m",
                "foo",
                "--xts_device_blocklist=id2,id3"));

    assertThat(info.remainingArgs()).containsExactly("run", "cts", "-m", "foo").inOrder();
    DeviceFilterSetting setting = info.deviceFilterSetting();
    assertThat(setting.hasAllowlistFilter()).isTrue();
    assertThat(setting.getAllowlistFilter().getExplicitFilter().getControlIdsList())
        .containsExactly("id1");
    assertThat(setting.hasBlocklistFilter()).isTrue();
    assertThat(setting.getBlocklistFilter().getExplicitFilter().getControlIdsList())
        .containsExactly("id2", "id3");
  }

  @Test
  public void parse_allowlistEmptySet_returnsExplicitFilterWithNoElements() {
    DeviceFilterInfo info = DeviceFilterParser.parse(ImmutableList.of("--xts_device_allowlist="));

    assertThat(info.remainingArgs()).isEmpty();
    DeviceFilterSetting setting = info.deviceFilterSetting();
    assertThat(setting.hasAllowlistFilter()).isTrue();
    assertThat(setting.getAllowlistFilter().getFilterTypeCase())
        .isEqualTo(FilterTypeCase.EXPLICIT_FILTER);
    assertThat(setting.getAllowlistFilter().getExplicitFilter().getControlIdsList()).isEmpty();
  }

  @Test
  public void parse_allowlistUniversalSet_returnsUniversalFilter() {
    DeviceFilterInfo info = DeviceFilterParser.parse(ImmutableList.of("--xts_device_allowlist=*"));

    assertThat(info.remainingArgs()).isEmpty();
    DeviceFilterSetting setting = info.deviceFilterSetting();
    assertThat(setting.hasAllowlistFilter()).isTrue();
    assertThat(setting.getAllowlistFilter().getFilterTypeCase())
        .isEqualTo(FilterTypeCase.UNIVERSAL);
  }

  @Test
  public void parse_blocklistUniversalSet_returnsUniversalFilter() {
    DeviceFilterInfo info = DeviceFilterParser.parse(ImmutableList.of("--xts_device_blocklist=*"));

    assertThat(info.remainingArgs()).isEmpty();
    DeviceFilterSetting setting = info.deviceFilterSetting();
    assertThat(setting.hasBlocklistFilter()).isTrue();
    assertThat(setting.getBlocklistFilter().getFilterTypeCase())
        .isEqualTo(FilterTypeCase.UNIVERSAL);
  }

  @Test
  public void parse_allowlistMixedWithWildcard_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> DeviceFilterParser.parse(ImmutableList.of("--xts_device_allowlist=id1,*")));

    assertThat(exception).hasMessageThat().contains("--xts_device_allowlist=");
  }

  @Test
  public void parse_repeatedAllowlistOptions_usesLastSpecifiedOption() {
    DeviceFilterInfo info =
        DeviceFilterParser.parse(
            ImmutableList.of("--xts_device_allowlist=id1", "--xts_device_allowlist=id2"));

    assertThat(info.remainingArgs()).isEmpty();
    DeviceFilterSetting setting = info.deviceFilterSetting();
    assertThat(setting.hasAllowlistFilter()).isTrue();
    assertThat(setting.getAllowlistFilter().getExplicitFilter().getControlIdsList())
        .containsExactly("id2");
  }
}
