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

package com.google.devtools.mobileharness.shared.util.flags.core;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import picocli.CommandLine.ParameterException;

@RunWith(JUnit4.class)
public class FlagTest {

  @BeforeClass
  public static void setUpClass() {
    FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
  }

  @AfterClass
  public static void tearDownClass() {
    FlagsManager.setFlagsClassForTest(FlagsManager.FLAGS_CLASS);
  }

  @Rule public final SetFlags setFlags = new SetFlags();

  @Before
  public void setUp() {
    FlagsManager.resetAllFlagsForTest();
  }

  // ===============================================================================================
  // Basic Parsing (Single Value Flags)
  // ===============================================================================================

  @Test
  public void testDefaultValues() {
    // Verifies default values are correctly loaded.
    assertThat(FlagsForTesting.foo.get()).isEqualTo(123);
  }

  @Test
  public void testParseArgs_string_withEquals() {
    // Prepares arguments.
    String[] args = {"--bar=new_val"};
    assertThat(FlagsForTesting.bar.wasSetFromString()).isFalse();

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.bar.get()).isEqualTo("new_val");
    assertThat(FlagsForTesting.bar.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_integer_withSpace() {
    // Prepares arguments.
    String[] args = {"--foo", "456"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.foo.get()).isEqualTo(456);
  }

  @Test
  public void testParseArgs_integer_invalidValue_throwsException() {
    // Prepares arguments.
    String[] args = {"--foo=abc"};

    // Verifies that parsing throws ParameterException.
    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_boolean_noValue() {
    // Prepares arguments.
    String[] args = {"--baz"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.baz.get()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_true() {
    // Prepares arguments.
    String[] args = {"--baz=true"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.baz.get()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_false() {
    // Prepares arguments.
    String[] args = {"--baz=false"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.baz.get()).isFalse();
  }

  @Test
  public void testParseArgs_boolean_withSpace_false() {
    // Prepares arguments.
    String[] args = {"--baz", "false"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied (flag is STILL set to true, "false" is ignored/positional).
    assertThat(FlagsForTesting.baz.get()).isTrue();
  }

  @Test
  public void testParseArgs_duration() {
    // Prepares arguments.
    String[] args = {"--timeout=1h30m"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.timeout.get()).isEqualTo(Duration.ofHours(1).plusMinutes(30));
  }

  // ===============================================================================================
  // Collection Flags (List and Set)
  // ===============================================================================================

  @Test
  public void testParseArgs_listString_commaSeparated() {
    // Prepares arguments.
    String[] args = {"--names=a,b,c"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.names.get()).containsExactly("a", "b", "c").inOrder();
  }

  @Test
  public void testParseArgs_listString_multipleOccurrences() {
    // Prepares arguments.
    String[] args = {"--names=a", "--names=b"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.names.get()).containsExactly("b");
  }

  @Test
  public void testParseArgs_listString_spaceSeparated() {
    // Prepares arguments.
    String[] args = {"--names", "a", "b"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.names.get()).containsExactly("a");
  }

  @Test
  public void testParseArgs_listString_combined() {
    // Prepares arguments.
    String[] args = {"--names=a,b", "--names=c,d"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.names.get()).containsExactly("c", "d").inOrder();
  }

  @Test
  public void testParseArgs_listString_emptyValue() {
    // Prepares arguments.
    String[] args = {"--names="};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.names.get()).isEmpty();
  }

  @Test
  public void testParseArgs_listInteger_commaSeparated() {
    // Prepares arguments.
    String[] args = {"--ids=1,2,3"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.ids.get()).containsExactly(1, 2, 3).inOrder();
  }

  @Test
  public void testParseArgs_listInteger_multipleOccurrences() {
    // Prepares arguments.
    String[] args = {"--ids=1", "--ids=2"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.ids.get()).containsExactly(2);
  }

  @Test
  public void testParseArgs_listInteger_combined() {
    // Prepares arguments.
    String[] args = {"--ids=1,2", "--ids=3,4"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.ids.get()).containsExactly(3, 4).inOrder();
  }

  @Test
  public void testParseArgs_listInteger_emptyValue() {
    // Prepares arguments.
    String[] args = {"--ids="};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.ids.get()).isEmpty();
  }

  @Test
  public void testParseArgs_listInteger_invalidValue_throwsException() {
    // Prepares arguments.
    String[] args = {"--ids=1,a,2"};

    // Verifies that parsing throws ParameterException.
    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_setInteger_duplicates() {
    // Prepares arguments.
    String[] args = {"--idsSet=1,2,1"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.idsSet.get()).containsExactly(1, 2).inOrder();
  }

  @Test
  public void testParseArgs_setInteger_ordering() {
    // Prepares arguments.
    String[] args = {"--idsSet=2,1,3"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.idsSet.get()).containsExactly(2, 1, 3).inOrder();
  }

  @Test
  public void testParseArgs_setInteger_duplicates_ordering() {
    // Prepares arguments.
    String[] args = {"--idsSet=2,1,2"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies new values are applied.
    assertThat(FlagsForTesting.idsSet.get()).containsExactly(2, 1).inOrder();
  }

  // ===============================================================================================
  // Edge Cases & Error Handling
  // ===============================================================================================

  @Test
  public void testGetNonNull_whenNull_throwsException() {
    // Prepares a flag with null value.
    Flag<String> nullFlag = Flag.value(null);

    // Verifies that getNonNull throws IllegalStateException when value is null.
    assertThrows(IllegalStateException.class, nullFlag::getNonNull);
  }

  @Test
  public void testUnknownFlagIgnored() {
    // Prepares arguments with unknown flag.
    String[] args = {"--foo=20", "--unknown=abc"};

    // Parses arguments.
    FlagsManager.parse(args);

    // Verifies known flag is updated and unknown is ignored.
    assertThat(FlagsForTesting.foo.get()).isEqualTo(20);
  }

  // ===============================================================================================
  // SetFlags Rule
  // ===============================================================================================

  @Test
  public void testSetFlagsRule() {
    // Overrides flag value via rule.
    setFlags.set("bar", "value_from_rule");
    assertThat(FlagsForTesting.bar.get()).isEqualTo("value_from_rule");
    assertThat(FlagsForTesting.bar.wasSetFromString()).isTrue();

    // Resets rule.
    setFlags.reset();

    // Verifies value is restored to default.
    assertThat(FlagsForTesting.bar.get()).isEqualTo("default_bar");
    assertThat(FlagsForTesting.bar.wasSetFromString()).isFalse();
  }

  @Test
  public void testSetFlagsRule_restoresToCommandLineValue() {
    // Simulates global command line parsing.
    FlagsManager.parse(new String[] {"--bar=global_val"});
    assertThat(FlagsForTesting.bar.get()).isEqualTo("global_val");

    // Overrides flag value via rule.
    setFlags.set("bar", "rule_val");
    assertThat(FlagsForTesting.bar.get()).isEqualTo("rule_val");

    // Resets rule.
    setFlags.reset();

    // Verifies value is restored to the command line value.
    assertThat(FlagsForTesting.bar.get()).isEqualTo("global_val");
  }

  @Test
  public void testSetFlagsRule_multipleOverrides_restoresToOriginal() {
    // Captures original value before overrides.
    String originalVal = FlagsForTesting.bar.get();

    // Applies multiple overrides.
    setFlags.set("bar", "value_1");
    setFlags.set("bar", "value_2");
    assertThat(FlagsForTesting.bar.get()).isEqualTo("value_2");

    // Resets rule.
    setFlags.reset();

    // Verifies original value is restored.
    assertThat(FlagsForTesting.bar.get()).isEqualTo(originalVal);
  }

  @Test
  public void testSetFlagsRule_nonExistentFlag_throwsException() {
    // Verifies that setting a non-existent flag throws an exception.
    assertThrows(IllegalArgumentException.class, () -> setFlags.set("non_existent_flag", "value"));
  }

  @Test
  public void testSetFlagsRule_invalidType_throwsException() {
    // Verifies that setting an invalid value type via rule throws an exception.
    assertThrows(RuntimeException.class, () -> setFlags.set("foo", "not_a_number"));
  }
}
