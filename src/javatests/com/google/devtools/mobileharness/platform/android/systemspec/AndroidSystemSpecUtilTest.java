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

package com.google.devtools.mobileharness.platform.android.systemspec;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.UsbDeviceLocator;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import com.google.wireless.qa.mobileharness.shared.proto.AndroidDeviceSpec.Abi;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AndroidSystemSpecUtilTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Mock private Adb adb;
  @Mock private AndroidAdbInternalUtil adbInternalUtil;
  @Mock private AndroidAdbUtil adbUtil;

  private static final String SERIAL = "363005dc750400ec";
  private static final String EMULATOR_SERIAL = "localhost:17605";
  private static final String VALID_SIM_ADB_OUTPUT =
      "Row: 0 _id=1, icc_id=89010005475451640413, sim_id=1, display_name=中華電信, carrier_name=沒有服務,"
          + " name_source=3, color=-16746133, number=NULL, display_number_format=1,"
          + " data_roaming=0, mcc=466, mnc=92, mcc_string=466, mnc_string=92, ehplmns=NULL,"
          + " hplmns=NULL, sim_provisioning_status=0, is_embedded=1,"
          + " card_id=89033023427100000000000001719763, access_rules=NULL,"
          + " access_rules_from_carrier_configs=NULL, is_removable=0,"
          + " enable_cmas_extreme_threat_alerts=1, enable_cmas_severe_threat_alerts=1,"
          + " enable_cmas_amber_alerts=1, enable_emergency_alerts=1, alert_sound_duration=4,"
          + " alert_reminder_interval=0, enable_alert_vibrate=1, enable_alert_speech=1,"
          + " enable_etws_test_alerts=0, enable_channel_50_alerts=1, enable_cmas_test_alerts=0,"
          + " show_cmas_opt_out_dialog=1, volte_vt_enabled=-1, vt_ims_enabled=-1,"
          + " wfc_ims_enabled=-1, wfc_ims_mode=-1, wfc_ims_roaming_mode=-1,"
          + " wfc_ims_roaming_enabled=-1, is_opportunistic=0, group_uuid=NULL, is_metered=1,"
          + " iso_country_code=tw, carrier_id=1884, profile_class=2, subscription_type=0,"
          + " group_owner=NULL, data_enabled_override_rules=NULL, imsi=466920123456789,"
          + " uicc_applications_enabled=1, allowed_network_types=-1, ims_rcs_uce_enabled=0,"
          + " cross_sim_calling_enabled=0, rcs_config=NULL,"
          + " allowed_network_types_for_reasons=user=850943,carrier=588799, d2d_sharing_status=0,"
          + " voims_opt_in_status=0, d2d_sharing_contacts=NULL, nr_advanced_calling_enabled=-1,"
          + " phone_number_source_carrier=NULL, phone_number_source_ims=NULL, port_index=0,"
          + " usage_setting=0\n";
  private static final String INVALID_SIM_ADB_OUTPUT =
      "Row: 1 _id=-1, icc_id=89886017157803910426, sim_id=-1, display_name=遠傳電信,"
          + " carrier_name=遠傳電信, name_source=3, color=-13408298, number=,"
          + " display_number_format=1, data_roaming=0, mcc=466, mnc=1, mcc_string=466,"
          + " mnc_string=01, ehplmns=, hplmns=46601, sim_provisioning_status=0, is_embedded=0,"
          + " card_id=89886017157803910426, access_rules=NULL,"
          + " access_rules_from_carrier_configs=NULL, is_removable=0,"
          + " enable_cmas_extreme_threat_alerts=1, enable_cmas_severe_threat_alerts=1,"
          + " enable_cmas_amber_alerts=1, enable_emergency_alerts=1, alert_sound_duration=4,"
          + " alert_reminder_interval=0, enable_alert_vibrate=1, enable_alert_speech=1,"
          + " enable_etws_test_alerts=0, enable_channel_50_alerts=1, enable_cmas_test_alerts=0,"
          + " show_cmas_opt_out_dialog=1, volte_vt_enabled=-1, vt_ims_enabled=-1,"
          + " wfc_ims_enabled=-1, wfc_ims_mode=-1, wfc_ims_roaming_mode=-1,"
          + " wfc_ims_roaming_enabled=-1, is_opportunistic=0, group_uuid=NULL, is_metered=1,"
          + " iso_country_code=tw, carrier_id=1881, profile_class=-1, subscription_type=0,"
          + " group_owner=NULL, data_enabled_override_rules=NULL, imsi=466011700391046,"
          + " uicc_applications_enabled=1, allowed_network_types=-1, ims_rcs_uce_enabled=0,"
          + " cross_sim_calling_enabled=0, rcs_config=NULL,"
          + " allowed_network_types_for_reasons=user=850943,carrier=588799, d2d_sharing_status=0,"
          + " voims_opt_in_status=0, d2d_sharing_contacts=NULL, nr_advanced_calling_enabled=-1,"
          + " phone_number_source_carrier=NULL, phone_number_source_ims=+886989055881,"
          + " port_index=0, usage_setting=0\n";

  private AndroidSystemSpecUtil systemSpecUtil;

  @Before
  public void setUp() {
    systemSpecUtil = new AndroidSystemSpecUtil(adb, adbInternalUtil, adbUtil);
  }

  @Test
  public void getDeviceUsbLocator() throws Exception {
    assertThat(systemSpecUtil.getUsbLocator("usb:2-5")).isEqualTo(UsbDeviceLocator.of(2, "5"));
    assertThat(systemSpecUtil.getUsbLocator("usb:2-11.4"))
        .isEqualTo(UsbDeviceLocator.of(2, "11.4"));

    when(adbInternalUtil.listDevices(/* timeout= */ null))
        .thenReturn(
            ImmutableList.of(
                "363005DC750400EC   device  usb:1-2",
                "0288504043411157   device  usb:3-4.7",
                "0288504043411158   device  usb:#-4.7"));
    assertThat(systemSpecUtil.getUsbLocator("363005DC750400EC"))
        .isEqualTo(UsbDeviceLocator.of(1, "2"));
    assertThat(systemSpecUtil.getUsbLocator("0288504043411157"))
        .isEqualTo(UsbDeviceLocator.of(3, "4.7"));
    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> systemSpecUtil.getUsbLocator("whatever"))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_USB_LOCATOR_SERIAL_NOT_FOUND);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> systemSpecUtil.getUsbLocator("0288504043411158"))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_USB_LOCATOR_INVALID_USB_ID);
  }

  @Test
  public void getDeviceAbiShouldReturnDesiredValue() throws Exception {
    Map<String, Abi> testCases =
        new HashMap<>(
            ImmutableMap.<String, Abi>builder()
                .put("armeabi", Abi.ARMEABI) // grandmother android phone.
                .put("armeabi-v7a", Abi.ARMEABI_V7A) // Nexus 5,6,7.
                .put("armeabi-v7a-hard", Abi.ARMEABI_V7A_HARD)
                // Just be here since it listed in android ABI doc.
                .put("arm64-v8a", Abi.ARM64_V8A) // Nexus 9, 5x, 6p.
                .put("x86", Abi.X86) // Some Intel Atom(TM)-based phone, emulator.
                .put("x86_64", Abi.X86_64) // Emulator.
                .put("mips", Abi.MIPS) // Just be here since it listed in android ABI doc.
                .put("mips64", Abi.MIPS64) // Just be here since it listed in android ABI doc.
                .buildOrThrow());

    for (String abiString : testCases.keySet()) {
      when(adbUtil.getProperty(SERIAL, AndroidProperty.ABI)).thenReturn(abiString);
      assertWithMessage("Convert " + abiString)
          .that(systemSpecUtil.getDeviceAbi(SERIAL))
          .isEqualTo(testCases.get(abiString));
    }
    when(adbUtil.getProperty(SERIAL, AndroidProperty.ABI)).thenReturn("ohno");
    assertThat(
            assertThrows(MobileHarnessException.class, () -> systemSpecUtil.getDeviceAbi(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_INVALID_ABI);

    when(adbUtil.getProperty(SERIAL, AndroidProperty.ABI)).thenThrow(new InterruptedException());
    assertThrows(InterruptedException.class, () -> systemSpecUtil.getDeviceAbi(SERIAL));
  }

  @Test
  public void getDeviceImei() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 4)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 0000000f 00360038 00390037 '........8.6.7.9.'\n"
                + "  0x00000010: 00310038 00320030 00380031 00330039 '8.1.0.2.1.8.9.3.'\n"
                + "  0x00000020: 00390038 00000039                   '8.9.9...        ')\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    Optional<String> optImei = systemSpecUtil.getDeviceImei(SERIAL, 29);
    assertThat(optImei).hasValue("867981021893899");

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> systemSpecUtil.getDeviceImei(SERIAL, 29))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_IMEI_ERROR);
  }

  @Test
  public void getDeviceImeiSdkVersion20() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 1)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 0000000f 00360038 00390037 '........8.6.7.9.'\n"
                + "  0x00000010: 00310038 00320030 00380031 00330039 '8.1.0.2.1.8.9.3.'\n"
                + "  0x00000020: 00390038 00000039                   '8.9.9...        ')\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    Optional<String> optImei = systemSpecUtil.getDeviceImei(SERIAL, 20);
    assertThat(optImei).hasValue("867981021893899");

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> systemSpecUtil.getDeviceImei(SERIAL, 20))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_IMEI_ERROR);
  }

  @Test
  public void getDeviceImeiSdkVersion30() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 5)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 0000000f 00360038 00390037 '........8.6.7.9.'\n"
                + "  0x00000010: 00310038 00320030 00380031 00330039 '8.1.0.2.1.8.9.3.'\n"
                + "  0x00000020: 00390038 00000039                   '8.9.9...        ')\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    Optional<String> optImei = systemSpecUtil.getDeviceImei(SERIAL, 30);
    assertThat(optImei).hasValue("867981021893899");

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> systemSpecUtil.getDeviceImei(SERIAL, 30))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_IMEI_ERROR);
  }

  @Test
  public void getDeviceImeiNoService() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 4)))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR,
                "service: Service iphonesubinfo does not exist"));

    assertThat(systemSpecUtil.getDeviceImei(SERIAL, 29)).isEmpty();
  }

  @Test
  public void getDeviceImeiNoImei() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 4)))
        .thenReturn("Result: Parcel(00000000 ffffffff   '........')\n");

    assertThat(systemSpecUtil.getDeviceImei(SERIAL, 29)).isEmpty();
  }

  @Test
  public void getDeviceImeiWrongLuhn() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 4)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 0000000f 00360038 00390037 '........8.6.7.9.'\n"
                + "  0x00000010: 00310038 00320030 00380031 00330039 '8.1.0.2.1.8.9.3.'\n"
                + "  0x00000020: 00390038 00000038                   '8.9.8...        ')\n");

    assertThat(systemSpecUtil.getDeviceImei(SERIAL, 29)).isEmpty();
  }

  @Test
  public void getDeviceImeiZeroIMEI() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 4)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 0000000f 00300030 00300030 '........0.0.0.0.'\n"
                + "  0x00000010: 00300030 00300030 00300030 00300030 '0.0.0.0.0.0.0.0.'\n"
                + "  0x00000020: 00300030 00000030                   '0.0.0...        ')\n");

    assertThat(systemSpecUtil.getDeviceImei(SERIAL, 29)).isEmpty();
  }

  @Test
  public void getDeviceIccid() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 11)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 00000013 00390038 00310030 '........8.9.0.1.'\n"
                + "  0x00000010: 00360032 00390030 00310037 00340031 '2.6.0.9.7.1.1.4.'\n"
                + "  0x00000020: 00340033 00310034 00320037 00000031 '3.4.4.1.7.2.1...')\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    Optional<String> optIccid = systemSpecUtil.getDeviceIccid(SERIAL, 29);
    assertThat(optIccid).hasValue("8901260971143441721");

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> systemSpecUtil.getDeviceIccid(SERIAL, 29))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_ICCID_ERROR);
  }

  @Test
  public void getDeviceIccidSdkVersion20() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 5)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 00000013 00390038 00310030 '........8.9.0.1.'\n"
                + "  0x00000010: 00360032 00390030 00310037 00340031 '2.6.0.9.7.1.1.4.'\n"
                + "  0x00000020: 00340033 00310034 00320037 00000031 '3.4.4.1.7.2.1...')\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    Optional<String> optIccid = systemSpecUtil.getDeviceIccid(SERIAL, 20);
    assertThat(optIccid).hasValue("8901260971143441721");

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> systemSpecUtil.getDeviceIccid(SERIAL, 20))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_ICCID_ERROR);
  }

  @Test
  public void getDeviceIccidSdkVersion21() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 12)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 00000013 00390038 00310030 '........8.9.0.1.'\n"
                + "  0x00000010: 00360032 00390030 00310037 00340031 '2.6.0.9.7.1.1.4.'\n"
                + "  0x00000020: 00340033 00310034 00320037 00000031 '3.4.4.1.7.2.1...')\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    Optional<String> optIccid = systemSpecUtil.getDeviceIccid(SERIAL, 21);
    assertThat(optIccid).hasValue("8901260971143441721");

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> systemSpecUtil.getDeviceIccid(SERIAL, 21))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_ICCID_ERROR);
  }

  @Test
  public void getDeviceIccidSdkVersion28() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 11)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 00000013 00390038 00310030 '........8.9.0.1.'\n"
                + "  0x00000010: 00360032 00390030 00310037 00340031 '2.6.0.9.7.1.1.4.'\n"
                + "  0x00000020: 00340033 00310034 00320037 00000031 '3.4.4.1.7.2.1...')\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    Optional<String> optIccid = systemSpecUtil.getDeviceIccid(SERIAL, 28);
    assertThat(optIccid).hasValue("8901260971143441721");

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> systemSpecUtil.getDeviceIccid(SERIAL, 28))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_ICCID_ERROR);
  }

  @Test
  public void getDeviceIccidSdkVersion30() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 14)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 00000013 00390038 00310030 '........8.9.0.1.'\n"
                + "  0x00000010: 00360032 00390030 00310037 00340031 '2.6.0.9.7.1.1.4.'\n"
                + "  0x00000020: 00340033 00310034 00320037 00000031 '3.4.4.1.7.2.1...')\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    Optional<String> optIccid = systemSpecUtil.getDeviceIccid(SERIAL, 30);
    assertThat(optIccid).hasValue("8901260971143441721");

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> systemSpecUtil.getDeviceIccid(SERIAL, 30))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_ICCID_ERROR);
  }

  @Test
  public void getDeviceIccidNoService() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 11)))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR,
                "service: Service iphonesubinfo does not exist"));

    assertThat(systemSpecUtil.getDeviceIccid(SERIAL, 29)).isEmpty();
  }

  @Test
  public void getDeviceIccidNoIccid() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 11)))
        .thenReturn("Result: Parcel(00000000 ffffffff   '........')\n");

    assertThat(systemSpecUtil.getDeviceIccid(SERIAL, 29)).isEmpty();
  }

  @Test
  public void getDeviceIccidWrongLuhn() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 11)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 00000013 00390038 00310030 '........8.9.0.1.'\n"
                + "  0x00000010: 00360032 00390030 00310037 00340031 '2.6.0.9.7.1.1.4.'\n"
                + "  0x00000020: 00340033 00310034 00320037 00000031 '3.4.4.1.7.2.2...')\n");

    assertThat(systemSpecUtil.getDeviceIccid(SERIAL, 29)).isEmpty();
  }

  @Test
  public void getDeviceIccidZeroIccid() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 11)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 00000000 00000000 00000000 '........0.0.0.0.'\n"
                + "  0x00000010: 00000000 00000000 00000000 00000000 '0.0.0.0.0.0.0.0.'\n"
                + "  0x00000020: 00000000 00000000 00000000 00000000 '0.0.0.0.0.0.0...')\n");

    assertThat(systemSpecUtil.getDeviceIccid(SERIAL, 29)).isEmpty();
  }

  @Test
  public void getDeviceIccidWrongPrefix() throws Exception {
    when(adb.runShell(
            SERIAL, String.format(AndroidSystemSpecUtil.ADB_SHELL_IPHONE_SUBINFO_TEMPLATE, 11)))
        .thenReturn(
            "Result: Parcel(\n"
                + "  0x00000000: 00000000 00000013 00390038 00310030 '........1.1.0.1.'\n"
                + "  0x00000010: 00360032 00390030 00310037 00340031 '2.6.0.9.7.1.1.4.'\n"
                + "  0x00000020: 00340033 00310034 00320037 00000031 '3.4.4.1.7.2.1...')\n");

    assertThat(systemSpecUtil.getDeviceIccid(SERIAL, 29)).isEmpty();
  }

  @Test
  public void getMacAddress() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_GET_WIFI_MAC_ADDRESS))
        .thenReturn("ac:cf:85:2a:3f:8a\r\n")
        .thenReturn("ac:cf:85:2a:3f:8b\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    assertThat(systemSpecUtil.getMacAddress(SERIAL)).isEqualTo("ac:cf:85:2a:3f:8a");
    assertThat(systemSpecUtil.getMacAddress(SERIAL)).isEqualTo("ac:cf:85:2a:3f:8b");
    assertThat(
            assertThrows(MobileHarnessException.class, () -> systemSpecUtil.getMacAddress(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_MAC_ADDRESS_ERROR);
  }

  @Test
  public void getBluetoothMacAddress() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_GET_BLUETOOTH_MAC_ADDRESS))
        .thenReturn("00:9a:cd:56:0e:88\r\n")
        .thenReturn("00:9a:cd:56:0e:99\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    assertThat(systemSpecUtil.getBluetoothMacAddress(SERIAL)).isEqualTo("00:9a:cd:56:0e:88");
    assertThat(systemSpecUtil.getBluetoothMacAddress(SERIAL)).isEqualTo("00:9a:cd:56:0e:99");
  }

  @Test
  public void getNumberOfCpus() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_GET_CPU_INFO))
        .thenReturn(
            "Processor       : ARMv7 Processor rev 0 (v7l)\n"
                + "processor       : 0\n"
                + "BogoMIPS        : 13.53\n\n"
                + "processor       : 1\n"
                + "BogoMIPS        : 13.53\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"))
        .thenReturn("");

    assertThat(systemSpecUtil.getNumberOfCpus(SERIAL)).isEqualTo(2);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> systemSpecUtil.getNumberOfCpus(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_CPU_INFO_ERROR);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> systemSpecUtil.getNumberOfCpus(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_NO_CPU_FOUND);
  }

  @Test
  public void getSystemFeatures_commaDelimitter() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("feature:foo,feature:bar")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"));

    assertThat(systemSpecUtil.getSystemFeatures(SERIAL))
        .containsExactly("feature:foo", "feature:bar");
    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> systemSpecUtil.getSystemFeatures(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_LIST_FEATURES_ERROR);
  }

  @Test
  public void getSystemFeatures_newlineDelimitter() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("feature:foo\nfeature:bar\n");

    assertThat(systemSpecUtil.getSystemFeatures(SERIAL))
        .containsExactly("feature:foo", "feature:bar");
  }

  @Test
  public void getTotalMem() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_GET_TOTAL_MEM))
        .thenReturn(
            "MemTotal:         742868 kB\n"
                + "MemFree:          278656 kB\n"
                + "Buffers:           93824 kB\n")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "Error"))
        .thenReturn("")
        .thenReturn(
            "MemTotal:         ### kB\n"
                + "MemFree:          ### kB\n"
                + "Buffers:           ### kB\n")
        .thenReturn(
            "MemTotal:         -9999 kB\n"
                + "MemFree:          -9999 kB\n"
                + "Buffers:           0 kB\n");

    assertThat(systemSpecUtil.getTotalMem(SERIAL)).isEqualTo(742868);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> systemSpecUtil.getTotalMem(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_GET_TOTAL_MEM_ERROR);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> systemSpecUtil.getTotalMem(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_TOTAL_MEM_VALUE_NOT_FOUND);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> systemSpecUtil.getTotalMem(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_INVALID_TOTAL_MEM_VALUE);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> systemSpecUtil.getTotalMem(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_INVALID_TOTAL_MEM_VALUE);
  }

  @Test
  public void isAndroidEmulator_serialStartsWithLocalhost_true() {
    assertThat(AndroidSystemSpecUtil.isAndroidEmulator("localhost:8091")).isTrue();
  }

  @Test
  public void isAndroidEmulator_serialStartsWithEmulator_true() {
    assertThat(AndroidSystemSpecUtil.isAndroidEmulator("emulator-5554")).isTrue();
  }

  @Test
  public void isAndroidEmulator_serialStartsWith127dot0dot0dot1_true() {
    assertThat(AndroidSystemSpecUtil.isAndroidEmulator("127.0.0.1")).isTrue();
  }

  @Test
  public void isAutomotiveDevice_byHardwareType() throws Exception {
    when(adbUtil.getProperty(SERIAL, AndroidProperty.HARDWARE_TYPE))
        .thenReturn(AndroidSystemSpecUtil.AUTOMOTIVE_TYPE);
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("");

    assertThat(systemSpecUtil.isAutomotiveDevice(SERIAL)).isTrue();
  }

  @Test
  public void isAutomotiveDevice_byFeature() throws Exception {
    when(adbUtil.getProperty(SERIAL, AndroidProperty.HARDWARE_TYPE)).thenReturn("");
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("feature:android.hardware.type.automotive");

    assertThat(systemSpecUtil.isAutomotiveDevice(SERIAL)).isTrue();
  }

  @Test
  public void isAutomotiveDevice_false() throws Exception {
    when(adbUtil.getProperty(SERIAL, AndroidProperty.HARDWARE_TYPE)).thenReturn("");
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("");

    assertThat(systemSpecUtil.isAutomotiveDevice(SERIAL)).isFalse();
  }

  @Test
  public void isPixelExperience_true() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("feature:com.google.android.feature.PIXEL_EXPERIENCE");

    assertThat(systemSpecUtil.isPixelExperience(SERIAL)).isTrue();
  }

  @Test
  public void isPixelExperience_false() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("");

    assertThat(systemSpecUtil.isPixelExperience(SERIAL)).isFalse();
  }

  @Test
  public void isPixelExperience_emulator() throws Exception {
    when(adb.runShellWithRetry(EMULATOR_SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("feature:com.google.android.feature.GOOGLE_EXPERIENCE")
        .thenReturn("");

    assertThat(systemSpecUtil.isPixelExperience(EMULATOR_SERIAL)).isTrue();
    assertThat(systemSpecUtil.isPixelExperience(EMULATOR_SERIAL)).isFalse();
  }

  @Test
  public void isGoDevice_true() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn(AndroidSystemSpecUtil.FEATURE_LOW_RAM);

    assertThat(systemSpecUtil.isGoDevice(SERIAL)).isTrue();
  }

  @Test
  public void isGoDevice_falseOnWearable() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn(
            AndroidSystemSpecUtil.FEATURE_LOW_RAM + "\n" + AndroidSystemSpecUtil.FEATURE_WEARABLE);

    assertThat(systemSpecUtil.isGoDevice(SERIAL)).isFalse();
  }

  @Test
  public void isGoDevice_false() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("");

    assertThat(systemSpecUtil.isGoDevice(SERIAL)).isFalse();
  }

  @Test
  public void isTvDevice_true() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn(AndroidSystemSpecUtil.FEATURE_TV);

    assertThat(systemSpecUtil.isAndroidTvDevice(SERIAL)).isTrue();
  }

  @Test
  public void isTvDevice_false() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("");

    assertThat(systemSpecUtil.isAndroidTvDevice(SERIAL)).isFalse();
  }

  @Test
  public void isWearableDevice_byFeature_true() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn(AndroidSystemSpecUtil.FEATURE_WEARABLE);
    when(adbUtil.getProperty(SERIAL, AndroidProperty.CHARACTERISTICS))
        .thenReturn("other characterristics");

    assertThat(systemSpecUtil.isWearableDevice(SERIAL)).isTrue();
  }

  @Test
  public void isWearableDevice_byCharacteristics_true() throws Exception {
    when(adbUtil.getProperty(SERIAL, AndroidProperty.CHARACTERISTICS))
        .thenReturn(AndroidSystemSpecUtil.CHARACTERISTIC_WEARABLE);
    assertThat(systemSpecUtil.isWearableDevice(SERIAL)).isTrue();
  }

  @Test
  public void isWearableDevice_false() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_LIST_FEATURES))
        .thenReturn("");
    when(adbUtil.getProperty(SERIAL, AndroidProperty.CHARACTERISTICS)).thenReturn("nosdcard");
    assertThat(systemSpecUtil.isWearableDevice(SERIAL)).isFalse();
  }

  @Test
  public void getIccids_noSims_returnsEmptyList() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn("No result.");

    assertThat(systemSpecUtil.getIccids(SERIAL)).isEmpty();
  }

  @Test
  public void getIccids_oneValidSim_returnsListWithOneIccid() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(VALID_SIM_ADB_OUTPUT);

    assertThat(systemSpecUtil.getIccids(SERIAL)).containsExactly("89010005475451640413");
  }

  @Test
  public void getIccids_twoValidSims_returnsListWithTwoIccids() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(VALID_SIM_ADB_OUTPUT + VALID_SIM_ADB_OUTPUT);

    assertThat(systemSpecUtil.getIccids(SERIAL))
        .containsExactly("89010005475451640413", "89010005475451640413");
  }

  @Test
  public void getIccids_physicalLocalSimWithInvalidSlot_returnsEmptyList() throws Exception {
    String adbOutput =
        "Row: 0 _id=1, icc_id=89010005475451640413, sim_id=-1, display_name=中華電信,"
            + " carrier_name=沒有服務, name_source=3, color=-16746133, number=NULL,"
            + " display_number_format=1, data_roaming=0, mcc=466, mnc=92, mcc_string=466,"
            + " mnc_string=92, ehplmns=NULL, hplmns=NULL, sim_provisioning_status=0, is_embedded=0,"
            + " card_id=89033023427100000000000001719763, access_rules=NULL,"
            + " access_rules_from_carrier_configs=NULL, is_removable=0,"
            + " enable_cmas_extreme_threat_alerts=1, enable_cmas_severe_threat_alerts=1,"
            + " enable_cmas_amber_alerts=1, enable_emergency_alerts=1, alert_sound_duration=4,"
            + " alert_reminder_interval=0, enable_alert_vibrate=1, enable_alert_speech=1,"
            + " enable_etws_test_alerts=0, enable_channel_50_alerts=1, enable_cmas_test_alerts=0,"
            + " show_cmas_opt_out_dialog=1, volte_vt_enabled=-1, vt_ims_enabled=-1,"
            + " wfc_ims_enabled=-1, wfc_ims_mode=-1, wfc_ims_roaming_mode=-1,"
            + " wfc_ims_roaming_enabled=-1, is_opportunistic=0, group_uuid=NULL, is_metered=1,"
            + " iso_country_code=tw, carrier_id=1884, profile_class=2, subscription_type=0,"
            + " group_owner=NULL, data_enabled_override_rules=NULL, imsi=466920123456789,"
            + " uicc_applications_enabled=1, allowed_network_types=-1, ims_rcs_uce_enabled=0,"
            + " cross_sim_calling_enabled=0, rcs_config=NULL,"
            + " allowed_network_types_for_reasons=user=850943,carrier=588799, d2d_sharing_status=0,"
            + " voims_opt_in_status=0, d2d_sharing_contacts=NULL, nr_advanced_calling_enabled=-1,"
            + " phone_number_source_carrier=NULL, phone_number_source_ims=NULL, port_index=0,"
            + " usage_setting=0\n";
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(adbOutput);

    assertThat(systemSpecUtil.getIccids(SERIAL)).isEmpty();
  }

  @Test
  public void getIccids_physicalRemoteSimWithInvalidSlot_returnsListWithIccid() throws Exception {
    String adbOutput =
        "Row: 0 _id=1, icc_id=89010005475451640413, sim_id=-1, display_name=中華電信,"
            + " carrier_name=沒有服務, name_source=3, color=-16746133, number=NULL,"
            + " display_number_format=1, data_roaming=0, mcc=466, mnc=92, mcc_string=466,"
            + " mnc_string=92, ehplmns=NULL, hplmns=NULL, sim_provisioning_status=0, is_embedded=0,"
            + " card_id=89033023427100000000000001719763, access_rules=NULL,"
            + " access_rules_from_carrier_configs=NULL, is_removable=0,"
            + " enable_cmas_extreme_threat_alerts=1, enable_cmas_severe_threat_alerts=1,"
            + " enable_cmas_amber_alerts=1, enable_emergency_alerts=1, alert_sound_duration=4,"
            + " alert_reminder_interval=0, enable_alert_vibrate=1, enable_alert_speech=1,"
            + " enable_etws_test_alerts=0, enable_channel_50_alerts=1, enable_cmas_test_alerts=0,"
            + " show_cmas_opt_out_dialog=1, volte_vt_enabled=-1, vt_ims_enabled=-1,"
            + " wfc_ims_enabled=-1, wfc_ims_mode=-1, wfc_ims_roaming_mode=-1,"
            + " wfc_ims_roaming_enabled=-1, is_opportunistic=0, group_uuid=NULL, is_metered=1,"
            + " iso_country_code=tw, carrier_id=1884, profile_class=2, subscription_type=1,"
            + " group_owner=NULL, data_enabled_override_rules=NULL, imsi=466920123456789,"
            + " uicc_applications_enabled=1, allowed_network_types=-1, ims_rcs_uce_enabled=0,"
            + " cross_sim_calling_enabled=0, rcs_config=NULL,"
            + " allowed_network_types_for_reasons=user=850943,carrier=588799, d2d_sharing_status=0,"
            + " voims_opt_in_status=0, d2d_sharing_contacts=NULL, nr_advanced_calling_enabled=-1,"
            + " phone_number_source_carrier=NULL, phone_number_source_ims=NULL, port_index=0,"
            + " usage_setting=0\n";
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(adbOutput);

    assertThat(systemSpecUtil.getIccids(SERIAL)).containsExactly("89010005475451640413");
  }

  @Test
  public void getIccids_embeddedLocalSimWithInvalidSlot_returnsListWithIccid() throws Exception {
    String adbOutput =
        "Row: 0 _id=1, icc_id=89010005475451640413, sim_id=-1, display_name=中華電信,"
            + " carrier_name=沒有服務, name_source=3, color=-16746133, number=NULL,"
            + " display_number_format=1, data_roaming=0, mcc=466, mnc=92, mcc_string=466,"
            + " mnc_string=92, ehplmns=NULL, hplmns=NULL, sim_provisioning_status=0, is_embedded=1,"
            + " card_id=89033023427100000000000001719763, access_rules=NULL,"
            + " access_rules_from_carrier_configs=NULL, is_removable=0,"
            + " enable_cmas_extreme_threat_alerts=1, enable_cmas_severe_threat_alerts=1,"
            + " enable_cmas_amber_alerts=1, enable_emergency_alerts=1, alert_sound_duration=4,"
            + " alert_reminder_interval=0, enable_alert_vibrate=1, enable_alert_speech=1,"
            + " enable_etws_test_alerts=0, enable_channel_50_alerts=1, enable_cmas_test_alerts=0,"
            + " show_cmas_opt_out_dialog=1, volte_vt_enabled=-1, vt_ims_enabled=-1,"
            + " wfc_ims_enabled=-1, wfc_ims_mode=-1, wfc_ims_roaming_mode=-1,"
            + " wfc_ims_roaming_enabled=-1, is_opportunistic=0, group_uuid=NULL, is_metered=1,"
            + " iso_country_code=tw, carrier_id=1884, profile_class=2, subscription_type=0,"
            + " group_owner=NULL, data_enabled_override_rules=NULL, imsi=466920123456789,"
            + " uicc_applications_enabled=1, allowed_network_types=-1, ims_rcs_uce_enabled=0,"
            + " cross_sim_calling_enabled=0, rcs_config=NULL,"
            + " allowed_network_types_for_reasons=user=850943,carrier=588799, d2d_sharing_status=0,"
            + " voims_opt_in_status=0, d2d_sharing_contacts=NULL, nr_advanced_calling_enabled=-1,"
            + " phone_number_source_carrier=NULL, phone_number_source_ims=NULL, port_index=0,"
            + " usage_setting=0\n";
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(adbOutput);

    assertThat(systemSpecUtil.getIccids(SERIAL)).containsExactly("89010005475451640413");
  }

  @Test
  public void getIccids_physicalLocalSimWithValidSlot_returnsListWithIccid() throws Exception {
    String adbOutput =
        "Row: 0 _id=1, icc_id=89010005475451640413, sim_id=0, display_name=中華電信,"
            + " carrier_name=沒有服務, name_source=3, color=-16746133, number=NULL,"
            + " display_number_format=1, data_roaming=0, mcc=466, mnc=92, mcc_string=466,"
            + " mnc_string=92, ehplmns=NULL, hplmns=NULL, sim_provisioning_status=0, is_embedded=0,"
            + " card_id=89033023427100000000000001719763, access_rules=NULL,"
            + " access_rules_from_carrier_configs=NULL, is_removable=0,"
            + " enable_cmas_extreme_threat_alerts=1, enable_cmas_severe_threat_alerts=1,"
            + " enable_cmas_amber_alerts=1, enable_emergency_alerts=1, alert_sound_duration=4,"
            + " alert_reminder_interval=0, enable_alert_vibrate=1, enable_alert_speech=1,"
            + " enable_etws_test_alerts=0, enable_channel_50_alerts=1, enable_cmas_test_alerts=0,"
            + " show_cmas_opt_out_dialog=1, volte_vt_enabled=-1, vt_ims_enabled=-1,"
            + " wfc_ims_enabled=-1, wfc_ims_mode=-1, wfc_ims_roaming_mode=-1,"
            + " wfc_ims_roaming_enabled=-1, is_opportunistic=0, group_uuid=NULL, is_metered=1,"
            + " iso_country_code=tw, carrier_id=1884, profile_class=2, subscription_type=0,"
            + " group_owner=NULL, data_enabled_override_rules=NULL, imsi=466920123456789,"
            + " uicc_applications_enabled=1, allowed_network_types=-1, ims_rcs_uce_enabled=0,"
            + " cross_sim_calling_enabled=0, rcs_config=NULL,"
            + " allowed_network_types_for_reasons=user=850943,carrier=588799, d2d_sharing_status=0,"
            + " voims_opt_in_status=0, d2d_sharing_contacts=NULL, nr_advanced_calling_enabled=-1,"
            + " phone_number_source_carrier=NULL, phone_number_source_ims=NULL, port_index=0,"
            + " usage_setting=0\n";
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(adbOutput);

    assertThat(systemSpecUtil.getIccids(SERIAL)).containsExactly("89010005475451640413");
  }

  @Test
  public void getIccids_oneValidSimAndOneInvalidSim_returnsListWithValidIccid() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(VALID_SIM_ADB_OUTPUT + INVALID_SIM_ADB_OUTPUT);

    assertThat(systemSpecUtil.getIccids(SERIAL)).containsExactly("89010005475451640413");
  }

  @Test
  public void getHingeAngle_isFoldable() throws Exception {
    when(adb.runShell(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_GET_HINGE_ANGLE))
        .thenReturn(
            "sensor_test version 99\n"
                + "Sampling from sensor type 36.0: Hinge Angle (wake-up).\n"
                + "Sensor: 36.0 TS: 88584228852 Data: 180.000000 0.000000 0.000000\n"
                + "Collected 1 samples in 0.004998 seconds (200.086288 samples per second).\n"
                + "Sensor 36.0 Collected 1 samples in 0.004998 seconds (200.086288 samples per"
                + " second).\n");
    assertThat(systemSpecUtil.getHingeAngle(SERIAL)).isEqualTo("180.000000");
  }

  @Test
  public void getHingeAngle_isNonFoldable() throws Exception {
    when(adb.runShell(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_GET_HINGE_ANGLE))
        .thenReturn(
            "Unable to find sensor id 36.0.\n"
                + "ParseArgs returned with code (7).\n"
                + "sensor_test version 99\n");
    assertThat(
            assertThrows(MobileHarnessException.class, () -> systemSpecUtil.getHingeAngle(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SPEC_SENSOR_SAMPLE_ERROR);
  }

  @Test
  public void getCarrierIds_noSims_returnsEmptyList() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn("No result.");

    assertThat(systemSpecUtil.getCarrierIds(SERIAL)).isEmpty();
  }

  @Test
  public void getCarrierIds_oneValidSim_returnsOneId() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(VALID_SIM_ADB_OUTPUT);

    assertThat(systemSpecUtil.getCarrierIds(SERIAL)).containsExactly("1884");
  }

  @Test
  public void getCarrierIds_multipleValidSims_returnsMultipleIds() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(VALID_SIM_ADB_OUTPUT + VALID_SIM_ADB_OUTPUT);

    assertThat(systemSpecUtil.getCarrierIds(SERIAL)).containsExactly("1884", "1884");
  }

  @Test
  public void getCarrierIds_invalidSim_returnsEmptyList() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(INVALID_SIM_ADB_OUTPUT);

    assertThat(systemSpecUtil.getCarrierIds(SERIAL)).isEmpty();
  }

  @Test
  public void getCarrierIds_invalidCarrierId_returnsEmptyList() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(VALID_SIM_ADB_OUTPUT.replace("carrier_id=1884", "carrier_id=-1"));

    assertThat(systemSpecUtil.getCarrierIds(SERIAL)).isEmpty();
  }

  @Test
  public void getCarrierIds_noCarrierId_returnsEmptyList() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidSystemSpecUtil.ADB_SHELL_QUERY_SIM_INFO))
        .thenReturn(VALID_SIM_ADB_OUTPUT.replace("carrier_id=1884", "carrier_id="));

    assertThat(systemSpecUtil.getCarrierIds(SERIAL)).isEmpty();
  }

  @Test
  public void getRadioVersion_hasValue_success() throws Exception {
    String baseband = "g5123b-130914-240205-B-11405587";
    when(adbUtil.getProperty(SERIAL, AndroidProperty.BASEBAND_VERSION)).thenReturn(baseband);

    assertThat(systemSpecUtil.getRadioVersion(SERIAL)).isEqualTo(baseband);
  }

  @Test
  public void getRadioVersion_noValue_success() throws Exception {
    when(adbUtil.getProperty(SERIAL, AndroidProperty.BASEBAND_VERSION)).thenReturn("");

    assertThat(systemSpecUtil.getRadioVersion(SERIAL)).isEmpty();
  }
}
