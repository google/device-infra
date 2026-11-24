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

package com.google.devtools.mobileharness.platform.android.app;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ActivityManagerTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Adb adb;
  private ActivityManager am;
  private static final String ADB_SHELL_GET_CONFIG = "am get-config";
  private static final String SERIAL = "FA77N0302705";

  @Before
  public void setUp() {
    am = new ActivityManager(adb);
  }

  @Test
  public void parsingLocale_ok() throws MobileHarnessException {
    String text1 =
        "config:"
            + " en-rUS-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-420dpi-finger-keysexposed-nokeys-navhidden-nonav-1794x1080-v28";
    assertThat(ActivityManager.parseLocale(text1).locale()).isEqualTo("en-US");

    String text2 =
        "config:"
            + " mcc321-en-rUS-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-420dpi-finger-keysexposed-nokeys-navhidden-nonav-1794x1080-v28";
    assertThat(ActivityManager.parseLocale(text2).locale()).isEqualTo("en-US");

    String text3 =
        "config:"
            + " mcc321-mnc333-en-rUS-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-420dpi-finger-keysexposed-nokeys-navhidden-nonav-1794x1080-v28";
    assertThat(ActivityManager.parseLocale(text3).locale()).isEqualTo("en-US");
  }

  @Test
  public void parsingLocale_exception() throws MobileHarnessException {
    String emptyText = "";
    Exception thrown0 =
        assertThrows(MobileHarnessException.class, () -> ActivityManager.parseLocale(emptyText));
    assertThat(thrown0)
        .hasCauseThat()
        .hasMessageThat()
        .contains("contains no valid config information");

    String illegalText1 = "config: no-";
    Exception thrown1 =
        assertThrows(MobileHarnessException.class, () -> ActivityManager.parseLocale(illegalText1));
    assertThat(thrown1)
        .hasCauseThat()
        .hasMessageThat()
        .contains("contains no valid config information");

    String illegalText2 = "This is not legal";
    Exception thrown2 =
        assertThrows(MobileHarnessException.class, () -> ActivityManager.parseLocale(illegalText2));
    assertThat(thrown2)
        .hasCauseThat()
        .hasMessageThat()
        .contains("contains no valid config information");
  }

  @Test
  public void getLocale_ok() throws MobileHarnessException, InterruptedException {
    String expectOutput =
        "config:"
            + " en-rUS-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-420dpi-finger-keysexposed-nokeys-navhidden-nonav-1794x1080-v28";
    when(adb.runShellWithRetry(eq(SERIAL), eq(ADB_SHELL_GET_CONFIG), any(Duration.class)))
        .thenReturn(expectOutput);
    assertThat(am.getLocale(SERIAL).locale()).isEqualTo("en-US");
  }

  @Test
  public void getLocale_emptyException() throws MobileHarnessException, InterruptedException {
    String emptyOutput = "";
    when(adb.runShellWithRetry(eq(SERIAL), eq(ADB_SHELL_GET_CONFIG), any(Duration.class)))
        .thenReturn(emptyOutput);
    Exception thrown = assertThrows(MobileHarnessException.class, () -> am.getLocale(SERIAL));
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void getLocale_missingException() throws MobileHarnessException, InterruptedException {
    String missingOutput = "config: ";
    when(adb.runShellWithRetry(eq(SERIAL), eq(ADB_SHELL_GET_CONFIG), any(Duration.class)))
        .thenReturn(missingOutput);
    Exception thrown = assertThrows(MobileHarnessException.class, () -> am.getLocale(SERIAL));
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void getLocale_invalidException() throws MobileHarnessException, InterruptedException {
    String invalidOutput = "config: invalid";
    when(adb.runShellWithRetry(eq(SERIAL), eq(ADB_SHELL_GET_CONFIG), any(Duration.class)))
        .thenReturn(invalidOutput);
    Exception thrown = assertThrows(MobileHarnessException.class, () -> am.getLocale(SERIAL));
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void getLocale_notFoundException() throws MobileHarnessException, InterruptedException {
    String notFoundOutput = "not found";
    when(adb.runShellWithRetry(eq(SERIAL), eq(ADB_SHELL_GET_CONFIG), any(Duration.class)))
        .thenReturn(notFoundOutput);
    Exception thrown = assertThrows(MobileHarnessException.class, () -> am.getLocale(SERIAL));
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_languageOnlyWithQualifiers_returnsLanguageWithoutRegion()
      throws MobileHarnessException {
    ActivityManager.Locale locale =
        ActivityManager.parseLocale(
            "config: ja-ldltr-sw411dp-w411dp-h659dp-normal-notlong-notround");

    assertThat(locale.locale()).isEqualTo("ja");
    assertThat(locale.language()).isEqualTo("ja");
    assertThat(locale.region()).isEmpty();
  }

  @Test
  public void parseLocale_languageOnlyWithRoundQualifier_returnsLanguageOnly()
      throws MobileHarnessException {
    ActivityManager.Locale locale = ActivityManager.parseLocale("config: en-round-notnight");

    assertThat(locale.locale()).isEqualTo("en");
    assertThat(locale.language()).isEqualTo("en");
    assertThat(locale.region()).isEmpty();
  }

  @Test
  public void parseLocale_bcp47ThreeLetterLanguageAndRegion_returnsNormalizedLocale()
      throws MobileHarnessException {
    ActivityManager.Locale locale =
        ActivityManager.parseLocale("config: b+fil+PH-ldltr-sw411dp-normal");

    assertThat(locale.locale()).isEqualTo("fil-PH");
    assertThat(locale.language()).isEqualTo("fil");
    assertThat(locale.region()).isEqualTo("PH");
  }

  @Test
  public void parseLocale_bcp47ThreeLetterLanguageOnly_returnsLocaleWithoutRegion()
      throws MobileHarnessException {
    ActivityManager.Locale locale = ActivityManager.parseLocale("config: b+fil-ldltr-sw411dp");

    assertThat(locale.locale()).isEqualTo("fil");
    assertThat(locale.language()).isEqualTo("fil");
    assertThat(locale.region()).isEmpty();
  }

  @Test
  public void parseLocale_bcp47NumericRegion_returnsLocale() throws MobileHarnessException {
    ActivityManager.Locale locale = ActivityManager.parseLocale("config: b+es+419-ldltr-sw411dp");

    assertThat(locale.locale()).isEqualTo("es-419");
    assertThat(locale.language()).isEqualTo("es");
    assertThat(locale.region()).isEqualTo("419");
  }

  @Test
  public void parseLocale_bcp47ScriptQualified_returnsLocaleWithLanguageAndRegion()
      throws MobileHarnessException {
    ActivityManager.Locale locale =
        ActivityManager.parseLocale("config: b+zh+Hans+CN-ldltr-sw411dp");

    assertThat(locale.locale()).isEqualTo("zh-Hans-CN");
    assertThat(locale.language()).isEqualTo("zh");
    assertThat(locale.region()).isEqualTo("CN");
  }

  @Test
  public void parseLocale_bcp47ScriptOnly_returnsLocaleWithoutRegion()
      throws MobileHarnessException {
    ActivityManager.Locale locale = ActivityManager.parseLocale("config: b+sr+Latn-ldltr-sw411dp");

    assertThat(locale.locale()).isEqualTo("sr-Latn");
    assertThat(locale.language()).isEqualTo("sr");
    assertThat(locale.region()).isEmpty();
  }

  @Test
  public void parseLocale_bcp47WithMccMncAndMultiLocaleList_returnsPrimaryLocale()
      throws MobileHarnessException {
    ActivityManager.Locale locale =
        ActivityManager.parseLocale(
            "config: mcc310-mnc410-b+zh+Hans+CN,en-rUS-ldltr-sw411dp\r\nabi: arm64-v8a");

    assertThat(locale.locale()).isEqualTo("zh-Hans-CN");
    assertThat(locale.language()).isEqualTo("zh");
    assertThat(locale.region()).isEqualTo("CN");
  }

  @Test
  public void parseLocale_traditionalMultiLocaleList_returnsPrimaryLocale()
      throws MobileHarnessException {
    ActivityManager.Locale locale = ActivityManager.parseLocale("config: en-rUS,ja-ldltr-sw411dp");

    assertThat(locale.locale()).isEqualTo("en-US");
    assertThat(locale.language()).isEqualTo("en");
    assertThat(locale.region()).isEqualTo("US");
  }

  @Test
  public void parseLocale_legacyHebrewCode_normalizesToHe() throws MobileHarnessException {
    ActivityManager.Locale locale = ActivityManager.parseLocale("config: iw-rIL-ldltr-sw411dp");

    assertThat(locale.locale()).isEqualTo("he-IL");
    assertThat(locale.language()).isEqualTo("he");
    assertThat(locale.region()).isEqualTo("IL");
  }

  @Test
  public void parseLocale_legacyIndonesianCode_normalizesToId() throws MobileHarnessException {
    ActivityManager.Locale locale = ActivityManager.parseLocale("config: in-rID-ldltr-sw411dp");

    assertThat(locale.locale()).isEqualTo("id-ID");
    assertThat(locale.language()).isEqualTo("id");
    assertThat(locale.region()).isEqualTo("ID");
  }

  @Test
  public void parseLocale_legacyYiddishCode_normalizesToYi() throws MobileHarnessException {
    ActivityManager.Locale locale = ActivityManager.parseLocale("config: ji-ldltr-sw411dp");

    assertThat(locale.locale()).isEqualTo("yi");
    assertThat(locale.language()).isEqualTo("yi");
    assertThat(locale.region()).isEmpty();
  }

  @Test
  public void parseLocale_nonLocaleConfigQualifiers_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () -> ActivityManager.parseLocale("config: ldltr-sw411dp"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_carUiModeQualifier_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () -> ActivityManager.parseLocale("config: car-ldltr-sw411dp"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_mccMncOnlyWithoutLocale_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () -> ActivityManager.parseLocale("config: mcc310-mnc410"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_emptyBcp47Tag_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> ActivityManager.parseLocale("config: b+-ldltr"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_invalidBcp47Subtag_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> ActivityManager.parseLocale("config: b+123-ldltr"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_undeterminedBcp47Language_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> ActivityManager.parseLocale("config: b+und-ldltr"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_malformedTraditionalRegion_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> ActivityManager.parseLocale("config: en-rUSA"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_emptyTraditionalRegionBeforeQualifier_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> ActivityManager.parseLocale("config: en-r-ldltr"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_numericTraditionalRegionBeforeQualifier_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> ActivityManager.parseLocale("config: en-r1-ldltr"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_incompleteTraditionalRegionBeforeComma_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> ActivityManager.parseLocale("config: en-r,ja"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void parseLocale_trailingCommaAfterLocale_throwsParseError() {
    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class, () -> ActivityManager.parseLocale("config: en-rUS,"));

    assertThat(thrown.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_AM_PARSE_LOCALE_ERROR);
    assertThat(thrown).hasMessageThat().contains("Failed to parse locale from am");
  }

  @Test
  public void localeMatches_caseInsensitiveAndOmittedCountry_returnsTrue() {
    ActivityManager.Locale locale = ActivityManager.Locale.create("en-US");

    assertThat(locale.matches("EN", "us")).isTrue();
    assertThat(locale.matches("en", "")).isTrue();
    assertThat(locale.matches("en", /* targetCountry= */ null)).isTrue();
  }

  @Test
  public void localeMatches_scriptQualifiedLocale_matchesLanguageAndRegion() {
    ActivityManager.Locale locale = ActivityManager.Locale.create("zh-Hans-CN");

    assertThat(locale.matches("zh", "CN")).isTrue();
    assertThat(locale.matches("zh", "TW")).isFalse();
  }

  @Test
  public void localeMatches_legacyAndModernIso639Codes_matchAcrossForms() {
    assertThat(ActivityManager.Locale.create("iw-IL").matches("he", "IL")).isTrue();
    assertThat(ActivityManager.Locale.create("he-IL").matches("iw", "IL")).isTrue();
    assertThat(ActivityManager.Locale.create("in-ID").matches("id", "ID")).isTrue();
    assertThat(ActivityManager.Locale.create("ji").matches("yi", "")).isTrue();
  }

  @Test
  public void localeMatches_countryRequestedWhenRegionEmpty_returnsFalse() {
    ActivityManager.Locale locale = ActivityManager.Locale.create("ja");

    assertThat(locale.matches("ja", "JP")).isFalse();
    assertThat(locale.matches("ja", "")).isTrue();
  }
}
