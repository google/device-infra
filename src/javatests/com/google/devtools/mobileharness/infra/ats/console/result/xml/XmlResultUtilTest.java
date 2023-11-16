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

package com.google.devtools.mobileharness.infra.ats.console.result.xml;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlResultUtil.DevicePropertyInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import java.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class XmlResultUtilTest {

  private static final String DEVICE_ID = "FA6AR0301593";
  private static final String HOST_NAME = "test_host.bej.company.com";

  private static final ImmutableList<String> PROPS =
      ImmutableList.of(
          "prop_abi",
          "prop_abi2",
          "prop_abis",
          "prop_abis_32",
          "prop_abis_64",
          "prop_board",
          "prop_bootimage_fingerprint",
          "prop_brand",
          "prop_device",
          "prop_fingerprint",
          "prop_id",
          "prop_manufacturer",
          "prop_model",
          "prop_product",
          "prop_reference_fingerprint",
          "prop_serial",
          "prop_tags",
          "prop_type",
          "prop_vendor_fingerprint",
          "prop_version_base_os",
          "prop_version_incremental",
          "prop_version_release",
          "prop_version_sdk",
          "prop_version_security_patch");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private AndroidAdbUtil androidAdbUtil;

  private XmlResultUtil xmlResultUtil;

  @Before
  public void setUp() {
    xmlResultUtil = spy(new XmlResultUtil(androidAdbUtil));
  }

  @Test
  public void prepareResultElementAttrs() throws Exception {
    when(androidAdbUtil.getIntProperty(DEVICE_ID, AndroidProperty.SDK_VERSION)).thenReturn(33);
    when(xmlResultUtil.getHostName()).thenReturn(HOST_NAME);
    when(xmlResultUtil.getSystemProperty("os.name")).thenReturn("os_name_value");
    when(xmlResultUtil.getSystemProperty("os.version")).thenReturn("os_version_value");
    when(xmlResultUtil.getSystemProperty("os.arch")).thenReturn("os_arch_value");
    when(xmlResultUtil.getSystemProperty("java.vendor")).thenReturn("java_vendor_value");
    when(xmlResultUtil.getSystemProperty("java.version")).thenReturn("java_version_value");

    ImmutableMap.Builder<String, String> expected =
        ImmutableMap.<String, String>builder()
            .put(XmlResultUtil.START_TIME_ATTR, "0")
            .put(XmlResultUtil.END_TIME_ATTR, "1000")
            .put(XmlResultUtil.SUITE_NAME_ATTR, "CTS_VERIFIER")
            .put(XmlResultUtil.SUITE_CTS_VERIFIER_MODE_ATTR, "automated")
            .put(XmlResultUtil.SUITE_VERSION_ATTR, "13.0_r1")
            .put(XmlResultUtil.SUITE_PLAN_ATTR, "verifier")
            .put(XmlResultUtil.SUITE_BUILD_ATTR, "0")
            .put(XmlResultUtil.DEVICES_ATTR, DEVICE_ID)
            .put(XmlResultUtil.HOST_NAME_ATTR, HOST_NAME)
            .put(XmlResultUtil.OS_NAME_ATTR, "os_name_value")
            .put(XmlResultUtil.OS_VERSION_ATTR, "os_version_value")
            .put(XmlResultUtil.OS_ARCH_ATTR, "os_arch_value")
            .put(XmlResultUtil.JAVA_VENDOR_ATTR, "java_vendor_value")
            .put(XmlResultUtil.JAVA_VERSION_ATTR, "java_version_value");

    expected
        .put(XmlResultUtil.START_DISPLAY_TIME_ATTR, "Thu Jan 01 00:00:00 UTC 1970")
        .put(XmlResultUtil.END_DISPLAY_TIME_ATTR, "Thu Jan 01 00:00:01 UTC 1970");

    assertThat(
            xmlResultUtil.prepareResultElementAttrs(
                Instant.EPOCH, Instant.ofEpochSecond(1), ImmutableList.of(DEVICE_ID)))
        .containsExactlyEntriesIn(expected.buildOrThrow());
  }

  @Test
  public void prepareBuildElementAttrs() throws Exception {
    DevicePropertyInfo devicePropertyInfo = getDevicePropertyInfo();
    when(androidAdbUtil.getIntProperty(DEVICE_ID, AndroidProperty.SDK_VERSION)).thenReturn(30);
    when(androidAdbUtil.getProperty(eq(DEVICE_ID), ArgumentMatchers.<ImmutableList<String>>any()))
        .thenReturn("prop_value");

    ImmutableMap<String, String> expected =
        devicePropertyInfo.getPropertyMap().entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> "prop_value"));

    assertThat(xmlResultUtil.prepareBuildElementAttrs(DEVICE_ID))
        .containsExactlyEntriesIn(expected);

    verify(androidAdbUtil, times(24))
        .getProperty(eq(DEVICE_ID), ArgumentMatchers.<ImmutableList<String>>any());
  }

  private DevicePropertyInfo getDevicePropertyInfo() {
    return DevicePropertyInfo.of(
        PROPS.get(0),
        PROPS.get(1),
        PROPS.get(2),
        PROPS.get(3),
        PROPS.get(4),
        PROPS.get(5),
        PROPS.get(6),
        PROPS.get(7),
        PROPS.get(8),
        PROPS.get(9),
        PROPS.get(10),
        PROPS.get(11),
        PROPS.get(12),
        PROPS.get(13),
        PROPS.get(14),
        PROPS.get(15),
        PROPS.get(16),
        PROPS.get(17),
        PROPS.get(18),
        PROPS.get(19),
        PROPS.get(20),
        PROPS.get(21),
        PROPS.get(22),
        PROPS.get(23));
  }
}
