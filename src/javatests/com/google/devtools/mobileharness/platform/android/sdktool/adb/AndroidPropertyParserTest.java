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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AndroidPropertyParser}. */
@RunWith(JUnit4.class)
public final class AndroidPropertyParserTest {

  @Test
  public void parse_nullOrEmptyInput_returnsEmptyMap() {
    assertThat(AndroidPropertyParser.parse(null)).isEmpty();
    assertThat(AndroidPropertyParser.parse("")).isEmpty();
    assertThat(AndroidPropertyParser.parse("   \n\t  ")).isEmpty();
  }

  @Test
  public void parse_singleLineProperties_success() {
    String raw =
        """
        [ro.product.model]: [Pixel 6]
        [ro.product.brand]: [Google]
        [ro.build.version.sdk]: [33]
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties)
        .containsExactly(
            "ro.product.model", "Pixel 6",
            "ro.product.brand", "Google",
            "ro.build.version.sdk", "33");
  }

  @Test
  public void parse_emptyPropertyValue_success() {
    String raw =
        """
        [persist.sys.test_harness]: []
        [ro.telephony.disable-call]: []
        [ro.product.model]: [Pixel 7]
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties)
        .containsExactly(
            "persist.sys.test_harness", "",
            "ro.telephony.disable-call", "",
            "ro.product.model", "Pixel 7");
  }

  @Test
  public void parse_multiLinePropertyValue_success() {
    String raw =
        """
        [ro.product.brand]: [Google]
        [persist.sys.timezone]: [America/
        Los_Angeles]
        [ro.product.model]: [Pixel 6]
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties)
        .containsExactly(
            "ro.product.brand", "Google",
            "persist.sys.timezone", "America/\nLos_Angeles",
            "ro.product.model", "Pixel 6");
  }

  @Test
  public void parse_multiLineProperty_multipleLines() {
    String raw =
        """
        [custom.multiline]: [line 1
          line 2 with spaces
        line 3
        line 4]
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties)
        .containsExactly("custom.multiline", "line 1\n  line 2 with spaces\nline 3\nline 4");
  }

  @Test
  public void parse_nestedBrackets_success() {
    String raw =
        """
        [vendor.display.config]: [display_1[0,0,1080,1920]]
        [custom.brackets]: [[nested_val]]
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties)
        .containsExactly(
            "vendor.display.config", "display_1[0,0,1080,1920]",
            "custom.brackets", "[nested_val]");
  }

  @Test
  @SuppressWarnings("StringConcatToTextBlock")
  public void parse_crlfSeparators_success() {
    // Explicit string concatenation with \r\n is required because Java text block compiler
    // automatically normalizes \r\n line endings to \n. CRLF line endings need to be preserved
    // and tested for compatibility with Android SDK <= 23 getprop output.
    String raw =
        "[ro.product.model]: [Pixel 8]\r\n"
            + "[ro.product.brand]: [Google]\r\n"
            + "[ro.build.id]: [UQ1A.240105.004]\r\n";

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties)
        .containsExactly(
            "ro.product.model", "Pixel 8",
            "ro.product.brand", "Google",
            "ro.build.id", "UQ1A.240105.004");
  }

  @Test
  public void parse_adbDaemonStartupMessages_filtersDaemonLines() {
    String raw =
        """
        * daemon not running; starting now at tcp:5037
        * daemon started successfully
        [ro.product.model]: [Pixel 6]
        * daemon message in middle
        [ro.product.brand]: [Google]
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties)
        .containsExactly(
            "ro.product.model", "Pixel 6",
            "ro.product.brand", "Google");
  }

  @Test
  public void parse_unclosedMultiLineAtEof_savesAccumulatedValue() {
    String raw =
        """
        [ro.product.brand]: [Google]
        [persist.unclosed]: [line1
        line2\
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties)
        .containsExactly(
            "ro.product.brand", "Google",
            "persist.unclosed", "line1\nline2");
  }

  @Test
  public void parse_unclosedMultiLineFollowedByNewProperty_recoversAndParsesBoth() {
    String raw =
        """
        [persist.unclosed]: [line1
        [ro.product.model]: [Pixel 6]
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties)
        .containsExactly(
            "persist.unclosed", "line1",
            "ro.product.model", "Pixel 6");
  }

  @Test
  public void parse_consecutiveMultiLineProperties_success() {
    String raw =
        """
        [prop.one]: [line 1a
        line 1b]
        [prop.two]: [line 2a
        line 2b]
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties)
        .containsExactly(
            "prop.one", "line 1a\nline 1b",
            "prop.two", "line 2a\nline 2b");
  }

  @Test
  public void parse_multiLinePropertyWithEmptyLines_preservesEmptyLines() {
    String raw =
        """
        [prop.multiline]: [line 1

        line 3]
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties).containsExactly("prop.multiline", "line 1\n\nline 3");
  }

  @Test
  public void parse_garbageLinesWithoutBrackets_ignored() {
    String raw =
        """
        some random warning header
        [ro.product.model]: [Pixel 6]
        random trailing line
        """;

    ImmutableMap<String, String> properties = AndroidPropertyParser.parse(raw);

    assertThat(properties).containsExactly("ro.product.model", "Pixel 6");
  }

  @Test
  public void getPropertyValue_nullPropertiesOrProperty_returnsEmpty() {
    assertThat(AndroidPropertyParser.getPropertyValue(null, AndroidProperty.ABI)).isEmpty();
    assertThat(AndroidPropertyParser.getPropertyValue(ImmutableMap.of(), null)).isEmpty();
  }

  @Test
  public void getPropertyValue_primaryKeyPresent_returnsPrimaryValue() {
    ImmutableMap<String, String> props =
        ImmutableMap.of(
            "ro.product.cpu.abi", "arm64-v8a",
            "ro.product.cpu.abi2", "armeabi-v7a");

    assertThat(AndroidPropertyParser.getPropertyValue(props, AndroidProperty.ABI))
        .isEqualTo("arm64-v8a");
  }

  @Test
  public void getPropertyValue_primaryKeyMissing_fallsBackToSecondaryKey() {
    ImmutableMap<String, String> props = ImmutableMap.of("ro.product.cpu.abi2", "armeabi-v7a");

    assertThat(AndroidPropertyParser.getPropertyValue(props, AndroidProperty.ABI))
        .isEqualTo("armeabi-v7a");
  }

  @Test
  public void getPropertyValue_primaryKeyEmpty_fallsBackToSecondaryKey() {
    Map<String, String> props = new HashMap<>();
    props.put("ro.product.cpu.abi", "");
    props.put("ro.product.cpu.abi2", "armeabi-v7a");

    assertThat(AndroidPropertyParser.getPropertyValue(props, AndroidProperty.ABI))
        .isEqualTo("armeabi-v7a");
  }

  @Test
  public void getPropertyValue_allFallbackKeysMissing_returnsEmpty() {
    ImmutableMap<String, String> props = ImmutableMap.of("other.prop", "value");

    assertThat(AndroidPropertyParser.getPropertyValue(props, AndroidProperty.ABI)).isEmpty();
  }

  @Test
  public void getPropertyValue_multiFallbackKeyResolution() {
    // AndroidProperty.DEVICE has keys: "ro.product.vendor.device", "ro.vendor.product.device",
    // "ro.product.device"
    ImmutableMap<String, String> props1 = ImmutableMap.of("ro.product.vendor.device", "device1");
    assertThat(AndroidPropertyParser.getPropertyValue(props1, AndroidProperty.DEVICE))
        .isEqualTo("device1");

    ImmutableMap<String, String> props2 = ImmutableMap.of("ro.vendor.product.device", "device2");
    assertThat(AndroidPropertyParser.getPropertyValue(props2, AndroidProperty.DEVICE))
        .isEqualTo("device2");

    ImmutableMap<String, String> props3 = ImmutableMap.of("ro.product.device", "device3");
    assertThat(AndroidPropertyParser.getPropertyValue(props3, AndroidProperty.DEVICE))
        .isEqualTo("device3");
  }
}
