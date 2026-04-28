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
    assertThat(FlagsForTesting.foo.get()).isEqualTo(123);
  }

  @Test
  public void testParseArgs_string_withEquals() {
    String[] args = {"--bar=new_val"};
    assertThat(FlagsForTesting.bar.wasSetFromString()).isFalse();

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.bar.get()).isEqualTo("new_val");
    assertThat(FlagsForTesting.bar.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_integer_withSpace() {
    String[] args = {"--foo", "456"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.foo.get()).isEqualTo(456);
  }

  @Test
  public void testParseArgs_integer_invalidValue_throwsException() {
    String[] args = {"--foo=abc"};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_boolean_noValue() {
    String[] args = {"--baz"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isTrue();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_true() {
    String[] args = {"--baz=true"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isTrue();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_false() {
    String[] args = {"--baz=false"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isFalse();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_yes() {
    String[] args = {"--baz=yes"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isTrue();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_no() {
    String[] args = {"--baz=no"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isFalse();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_y() {
    String[] args = {"--baz=Y"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isTrue();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_n() {
    String[] args = {"--baz=N"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isFalse();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_one() {
    String[] args = {"--baz=1"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isTrue();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_zero() {
    String[] args = {"--baz=0"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isFalse();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_empty() {
    String[] args = {"--baz="};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isFalse();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withSpace_false() {
    String[] args = {"--baz", "false"};

    FlagsManager.parse(args);

    // Flag is STILL set to true, "false" is ignored/positional.
    assertThat(FlagsForTesting.baz.get()).isTrue();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withSpace_emptyString() {
    String[] args = {"--baz", ""};

    FlagsManager.parse(args);

    // Flag is STILL set to true, "" is ignored/positional.
    assertThat(FlagsForTesting.baz.get()).isTrue();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_noPrefixed() {
    String[] args = {"--nobaz"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.baz.get()).isFalse();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_noPrefixed_withEquals_empty() {
    String[] args = {"--nobaz="};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_boolean_noPrefixed_withEquals_true() {
    String[] args = {"--nobaz=true"};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_boolean_noPrefixed_withEquals_false() {
    String[] args = {"--nobaz=false"};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_duration() {
    String[] args = {"--timeout=1h30m"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.timeout.get()).isEqualTo(Duration.ofHours(1).plusMinutes(30));
  }

  // ===============================================================================================
  // Collection Flags (List and Set)
  // ===============================================================================================

  @Test
  public void testParseArgs_listString_commaSeparated() {
    String[] args = {"--names=a,b,c"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.names.get()).containsExactly("a", "b", "c").inOrder();
  }

  @Test
  public void testParseArgs_listString_multipleOccurrences() {
    String[] args = {"--names=a", "--names=b"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.names.get()).containsExactly("b");
  }

  @Test
  public void testParseArgs_listString_spaceSeparated() {
    String[] args = {"--names", "a", "b"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.names.get()).containsExactly("a");
  }

  @Test
  public void testParseArgs_listString_combined() {
    String[] args = {"--names=a,b", "--names=c,d"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.names.get()).containsExactly("c", "d").inOrder();
  }

  @Test
  public void testParseArgs_listString_emptyValue() {
    String[] args = {"--names="};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.names.get()).isEmpty();
  }

  @Test
  public void testParseArgs_listInteger_commaSeparated() {
    String[] args = {"--ids=1,2,3"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.ids.get()).containsExactly(1, 2, 3).inOrder();
  }

  @Test
  public void testParseArgs_listInteger_multipleOccurrences() {
    String[] args = {"--ids=1", "--ids=2"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.ids.get()).containsExactly(2);
  }

  @Test
  public void testParseArgs_listInteger_combined() {
    String[] args = {"--ids=1,2", "--ids=3,4"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.ids.get()).containsExactly(3, 4).inOrder();
  }

  @Test
  public void testParseArgs_listInteger_emptyValue() {
    String[] args = {"--ids="};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.ids.get()).isEmpty();
  }

  @Test
  public void testParseArgs_listInteger_invalidValue_throwsException() {
    String[] args = {"--ids=1,a,2"};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_listBoolean_commaSeparated() {
    String[] args = {"--boolList=true,yes,1,false"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.boolList.get()).containsExactly(true, true, true, false).inOrder();
  }

  @Test
  public void testParseArgs_setInteger_duplicates() {
    String[] args = {"--idsSet=1,2,1"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.idsSet.get()).containsExactly(1, 2).inOrder();
  }

  @Test
  public void testParseArgs_setInteger_ordering() {
    String[] args = {"--idsSet=2,1,3"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.idsSet.get()).containsExactly(2, 1, 3).inOrder();
  }

  @Test
  public void testParseArgs_setInteger_duplicates_ordering() {
    String[] args = {"--idsSet=2,1,2"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.idsSet.get()).containsExactly(2, 1).inOrder();
  }

  // ===============================================================================================
  // Edge Cases & Error Handling
  // ===============================================================================================

  @Test
  public void testGetNonNull_whenNull_throwsException() {
    Flag<String> nullFlag = Flag.value(null);

    assertThrows(IllegalStateException.class, nullFlag::getNonNull);
  }

  @Test
  public void testUnknownFlagIgnored() {
    String[] args = {"--foo=20", "--unknown=abc"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.foo.get()).isEqualTo(20);
  }

  // ===============================================================================================
  // SetFlags Rule
  // ===============================================================================================

  @Test
  public void testSetFlagsRule() {
    setFlags.set("bar", "value_from_rule");

    assertThat(FlagsForTesting.bar.get()).isEqualTo("value_from_rule");
    assertThat(FlagsForTesting.bar.wasSetFromString()).isTrue();

    setFlags.reset();

    assertThat(FlagsForTesting.bar.get()).isEqualTo("default_bar");
    assertThat(FlagsForTesting.bar.wasSetFromString()).isFalse();
  }

  @Test
  public void testSetFlagsRule_restoresToCommandLineValue() {
    FlagsManager.parse(new String[] {"--bar=global_val"});

    assertThat(FlagsForTesting.bar.get()).isEqualTo("global_val");

    setFlags.set("bar", "rule_val");

    assertThat(FlagsForTesting.bar.get()).isEqualTo("rule_val");

    setFlags.reset();

    assertThat(FlagsForTesting.bar.get()).isEqualTo("global_val");
  }

  @Test
  public void testSetFlagsRule_multipleOverrides_restoresToOriginal() {
    String originalVal = FlagsForTesting.bar.get();

    setFlags.set("bar", "value_1");
    setFlags.set("bar", "value_2");

    assertThat(FlagsForTesting.bar.get()).isEqualTo("value_2");

    setFlags.reset();

    assertThat(FlagsForTesting.bar.get()).isEqualTo(originalVal);
  }

  @Test
  public void testSetFlagsRule_setNull() {
    setFlags.set("bar", null);

    assertThat(FlagsForTesting.bar.get()).isNull();
    assertThat(FlagsForTesting.bar.wasSetFromString()).isTrue();

    setFlags.reset();

    assertThat(FlagsForTesting.bar.get()).isEqualTo("default_bar");
    assertThat(FlagsForTesting.bar.wasSetFromString()).isFalse();
  }

  @Test
  public void testSetFlagsRule_nonExistentFlag_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> setFlags.set("non_existent_flag", "value"));
  }

  @Test
  public void testSetFlagsRule_invalidType_throwsException() {
    assertThrows(RuntimeException.class, () -> setFlags.set("foo", "not_a_number"));
  }

  @Test
  public void testSetFlagsRule_noPrefixed() {
    setFlags.set("nobaz", null);

    assertThat(FlagsForTesting.baz.get()).isFalse();
    assertThat(FlagsForTesting.baz.wasSetFromString()).isTrue();
  }

  @Test
  public void testSetFlagsRule_noPrefixed_withValue_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> setFlags.set("nobaz", "true"));
  }
}
