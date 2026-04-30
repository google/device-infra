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

import com.google.common.base.Throwables;
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
    assertThat(FlagsForTesting.integerFlag.get()).isEqualTo(123);
  }

  @Test
  public void testParseArgs_string_withEquals() {
    String[] args = {"--string_flag=new_val"};
    assertThat(FlagsForTesting.stringFlag.wasSetFromString()).isFalse();

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringFlag.get()).isEqualTo("new_val");
    assertThat(FlagsForTesting.stringFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_integer_withSpace() {
    String[] args = {"--integer_flag", "456"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.integerFlag.get()).isEqualTo(456);
  }

  @Test
  public void testParseArgs_integer_invalidValue_throwsException() {
    String[] args = {"--integer_flag=abc"};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_boolean_noValue() {
    String[] args = {"--boolean_flag"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isTrue();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_true() {
    String[] args = {"--boolean_flag=true"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isTrue();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_false() {
    String[] args = {"--boolean_flag=false"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isFalse();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_yes() {
    String[] args = {"--boolean_flag=yes"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isTrue();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_no() {
    String[] args = {"--boolean_flag=no"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isFalse();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_y() {
    String[] args = {"--boolean_flag=Y"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isTrue();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_n() {
    String[] args = {"--boolean_flag=N"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isFalse();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_one() {
    String[] args = {"--boolean_flag=1"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isTrue();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_zero() {
    String[] args = {"--boolean_flag=0"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isFalse();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withEquals_empty() {
    String[] args = {"--boolean_flag="};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isFalse();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withSpace_false() {
    String[] args = {"--boolean_flag", "false"};

    FlagsManager.parse(args);

    // Flag is STILL set to true, "false" is ignored/positional.
    assertThat(FlagsForTesting.booleanFlag.get()).isTrue();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_withSpace_emptyString() {
    String[] args = {"--boolean_flag", ""};

    FlagsManager.parse(args);

    // Flag is STILL set to true, "" is ignored/positional.
    assertThat(FlagsForTesting.booleanFlag.get()).isTrue();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_noPrefixed() {
    String[] args = {"--noboolean_flag"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanFlag.get()).isFalse();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testParseArgs_boolean_noPrefixed_withEquals_empty() {
    String[] args = {"--noboolean_flag="};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_boolean_noPrefixed_withEquals_true() {
    String[] args = {"--noboolean_flag=true"};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_boolean_noPrefixed_withEquals_false() {
    String[] args = {"--noboolean_flag=false"};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_duration() {
    String[] args = {"--duration_flag=1h30m"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.durationFlag.get()).isEqualTo(Duration.ofHours(1).plusMinutes(30));
  }

  @Test
  public void testParseArgs_enum() {
    String[] args = {"--enum_flag=VALUE_B"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.enumFlag.get()).isEqualTo(FlagsForTesting.TestEnum.VALUE_B);
  }

  @Test
  public void testParseArgs_enum_caseInsensitive_throwsException() {
    String[] args = {"--enum_flag=value_b"};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_enumList() {
    String[] args = {"--enum_list_flag=VALUE_A,VALUE_B"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.enumListFlag.get())
        .containsExactly(FlagsForTesting.TestEnum.VALUE_A, FlagsForTesting.TestEnum.VALUE_B)
        .inOrder();
  }

  @Test
  public void testParseArgs_validConstraints_success() {
    String[] args = {
      "--positive_integer_flag=5",
      "--nonnegative_integer_flag=0",
      "--positive_long_flag=15",
      "--nonnegative_long_flag=0"
    };

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.positiveIntegerFlag.get()).isEqualTo(5);
    assertThat(FlagsForTesting.nonnegativeIntegerFlag.get()).isEqualTo(0);
    assertThat(FlagsForTesting.positiveLongFlag.get()).isEqualTo(15L);
    assertThat(FlagsForTesting.nonnegativeLongFlag.get()).isEqualTo(0L);
  }

  @Test
  public void testParseArgs_positiveInt_invalidValue_throwsException() {
    String[] args = {"--positive_integer_flag=-5"};

    Exception e = assertThrows(Exception.class, () -> FlagsManager.parse(args));

    Throwable rootCause = Throwables.getRootCause(e);
    assertThat(rootCause).isInstanceOf(IllegalArgumentException.class);
    assertThat(rootCause).hasMessageThat().contains("must be greater than 0");
  }

  @Test
  public void testParseArgs_nonnegativeInt_invalidValue_throwsException() {
    String[] args = {"--nonnegative_integer_flag=-1"};

    Exception e = assertThrows(Exception.class, () -> FlagsManager.parse(args));

    Throwable rootCause = Throwables.getRootCause(e);
    assertThat(rootCause).isInstanceOf(IllegalArgumentException.class);
    assertThat(rootCause).hasMessageThat().contains("must be greater than or equal to 0");
  }

  @Test
  public void testParseArgs_nullConstraints_unassigned_success() {
    assertThat(FlagsForTesting.nullPositiveIntegerFlag.get()).isNull();
    assertThat(FlagsForTesting.nullNonnegativeIntegerFlag.get()).isNull();
  }

  @Test
  public void testParseArgs_nullPositiveInt_validValue_success() {
    String[] args = {"--null_positive_integer_flag=5"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.nullPositiveIntegerFlag.get()).isEqualTo(5);
  }

  @Test
  public void testParseArgs_nullPositiveInt_invalidValue_throwsException() {
    String[] args = {"--null_positive_integer_flag=-5"};

    Exception e = assertThrows(Exception.class, () -> FlagsManager.parse(args));

    Throwable rootCause = Throwables.getRootCause(e);
    assertThat(rootCause).isInstanceOf(IllegalArgumentException.class);
    assertThat(rootCause).hasMessageThat().contains("must be greater than 0");
  }

  // ===============================================================================================
  // Collection Flags (List and Set)
  // ===============================================================================================

  @Test
  public void testParseArgs_listString_commaSeparated() {
    String[] args = {"--string_list_flag=a,b,c"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringListFlag.get()).containsExactly("a", "b", "c").inOrder();
  }

  @Test
  public void testParseArgs_listString_multipleOccurrences() {
    String[] args = {"--string_list_flag=a", "--string_list_flag=b"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringListFlag.get()).containsExactly("b");
  }

  @Test
  public void testParseArgs_listString_spaceSeparated() {
    String[] args = {"--string_list_flag", "a", "b"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringListFlag.get()).containsExactly("a");
  }

  @Test
  public void testParseArgs_listString_combined() {
    String[] args = {"--string_list_flag=a,b", "--string_list_flag=c,d"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringListFlag.get()).containsExactly("c", "d").inOrder();
  }

  @Test
  public void testParseArgs_listString_emptyValue() {
    String[] args = {"--string_list_flag="};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringListFlag.get()).isEmpty();
  }

  @Test
  public void testParseArgs_listInteger_commaSeparated() {
    String[] args = {"--integer_list_flag=1,2,3"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.integerListFlag.get()).containsExactly(1, 2, 3).inOrder();
  }

  @Test
  public void testParseArgs_listInteger_multipleOccurrences() {
    String[] args = {"--integer_list_flag=1", "--integer_list_flag=2"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.integerListFlag.get()).containsExactly(2);
  }

  @Test
  public void testParseArgs_listInteger_combined() {
    String[] args = {"--integer_list_flag=1,2", "--integer_list_flag=3,4"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.integerListFlag.get()).containsExactly(3, 4).inOrder();
  }

  @Test
  public void testParseArgs_listInteger_emptyValue() {
    String[] args = {"--integer_list_flag="};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.integerListFlag.get()).isEmpty();
  }

  @Test
  public void testParseArgs_listInteger_invalidValue_throwsException() {
    String[] args = {"--integer_list_flag=1,a,2"};

    assertThrows(ParameterException.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_listBoolean_commaSeparated() {
    String[] args = {"--boolean_list_flag=true,yes,1,false"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.booleanListFlag.get())
        .containsExactly(true, true, true, false)
        .inOrder();
  }

  @Test
  public void testParseArgs_setInteger_duplicates() {
    String[] args = {"--integer_set_flag=1,2,1"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.integerSetFlag.get()).containsExactly(1, 2).inOrder();
  }

  @Test
  public void testParseArgs_setInteger_ordering() {
    String[] args = {"--integer_set_flag=2,1,3"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.integerSetFlag.get()).containsExactly(2, 1, 3).inOrder();
  }

  @Test
  public void testParseArgs_setInteger_duplicates_ordering() {
    String[] args = {"--integer_set_flag=2,1,2"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.integerSetFlag.get()).containsExactly(2, 1).inOrder();
  }

  // ===============================================================================================
  // Map Flags
  // ===============================================================================================

  @Test
  public void testParseArgs_mapString_commaSeparated() {
    String[] args = {"--string_string_map_flag=k1=v1,k2=v2"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringStringMapFlag.get()).containsExactly("k1", "v1", "k2", "v2");
  }

  @Test
  public void testParseArgs_mapNonStringKey_commaSeparated() {
    String[] args = {"--integer_string_map_flag=1=v1,2=v2"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.integerStringMapFlag.get()).containsExactly(1, "v1", 2, "v2");
  }

  @Test
  public void testParseArgs_mapString_multipleOccurrences_overwrites() {
    String[] args = {"--string_string_map_flag=k1=v1", "--string_string_map_flag=k2=v2"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringStringMapFlag.get()).containsExactly("k2", "v2");
  }

  @Test
  public void testParseArgs_mapString_emptyValue() {
    String[] args = {"--string_string_map_flag="};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringStringMapFlag.get()).isEmpty();
  }

  @Test
  public void testParseArgs_mapValueParsing_integerAndDuration() {
    String[] args = {
      "--string_integer_map_flag=k1=123,k2=456", "--string_duration_map_flag=t1=1h30m"
    };

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringIntegerMapFlag.get()).containsExactly("k1", 123, "k2", 456);
    assertThat(FlagsForTesting.stringDurationMapFlag.get())
        .containsExactly("t1", Duration.ofHours(1).plusMinutes(30));
  }

  @Test
  public void testParseArgs_mapString_duplicateKeysInSingleArg_throwsException() {
    String[] args = {"--string_string_map_flag=k1=v1,k1=v2"};

    assertThrows(Exception.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_mapString_entrySyntaxError_throwsException() {
    String[] args = {"--string_string_map_flag=k1v1"};

    assertThrows(Exception.class, () -> FlagsManager.parse(args));
  }

  @Test
  public void testParseArgs_mapString_whitespaceTrimming() {
    String[] args = {"--string_string_map_flag= k1 = v1 , k2 = v2 "};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.stringStringMapFlag.get()).containsExactly("k1", "v1", "k2", "v2");
  }

  // ===============================================================================================
  // Edge Cases & Error Handling
  // ===============================================================================================

  @Test
  public void testGetNonNull_whenNull_throwsException() {
    Flag<String> nullFlag = Flag.nullString();

    assertThrows(IllegalStateException.class, nullFlag::getNonNull);
  }

  @Test
  public void testUnknownFlagIgnored() {
    String[] args = {"--integer_flag=20", "--unknown=abc"};

    FlagsManager.parse(args);

    assertThat(FlagsForTesting.integerFlag.get()).isEqualTo(20);
  }

  // ===============================================================================================
  // SetFlags Rule
  // ===============================================================================================

  @Test
  public void testSetFlagsRule() {
    setFlags.set("string_flag", "value_from_rule");

    assertThat(FlagsForTesting.stringFlag.get()).isEqualTo("value_from_rule");
    assertThat(FlagsForTesting.stringFlag.wasSetFromString()).isTrue();

    setFlags.reset();

    assertThat(FlagsForTesting.stringFlag.get()).isEqualTo("default_bar");
    assertThat(FlagsForTesting.stringFlag.wasSetFromString()).isFalse();
  }

  @Test
  public void testSetFlagsRule_restoresToCommandLineValue() {
    FlagsManager.parse(new String[] {"--string_flag=global_val"});

    assertThat(FlagsForTesting.stringFlag.get()).isEqualTo("global_val");

    setFlags.set("string_flag", "rule_val");

    assertThat(FlagsForTesting.stringFlag.get()).isEqualTo("rule_val");

    setFlags.reset();

    assertThat(FlagsForTesting.stringFlag.get()).isEqualTo("global_val");
  }

  @Test
  public void testSetFlagsRule_multipleOverrides_restoresToOriginal() {
    String originalVal = FlagsForTesting.stringFlag.get();

    setFlags.set("string_flag", "value_1");
    setFlags.set("string_flag", "value_2");

    assertThat(FlagsForTesting.stringFlag.get()).isEqualTo("value_2");

    setFlags.reset();

    assertThat(FlagsForTesting.stringFlag.get()).isEqualTo(originalVal);
  }

  @Test
  public void testSetFlagsRule_setNull() {
    setFlags.set("nullable_string_flag", null);

    assertThat(FlagsForTesting.nullableStringFlag.get()).isNull();
    assertThat(FlagsForTesting.nullableStringFlag.wasSetFromString()).isTrue();

    setFlags.reset();

    assertThat(FlagsForTesting.nullableStringFlag.get()).isNull();
    assertThat(FlagsForTesting.nullableStringFlag.wasSetFromString()).isFalse();
  }

  @Test
  public void testSetFlagsRule_nonExistentFlag_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> setFlags.set("non_existent_flag", "value"));
  }

  @Test
  public void testSetFlagsRule_invalidType_throwsException() {
    assertThrows(RuntimeException.class, () -> setFlags.set("integer_flag", "not_a_number"));
  }

  @Test
  public void testSetFlagsRule_noPrefixed() {
    setFlags.set("noboolean_flag", null);

    assertThat(FlagsForTesting.booleanFlag.get()).isFalse();
    assertThat(FlagsForTesting.booleanFlag.wasSetFromString()).isTrue();
  }

  @Test
  public void testSetFlagsRule_noPrefixed_withValue_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> setFlags.set("noboolean_flag", "true"));
  }

  @Test
  public void testSetFlagsRule_positiveInt_invalidValue_throwsException() {
    Exception e = assertThrows(Exception.class, () -> setFlags.set("positive_integer_flag", "-5"));

    Throwable rootCause = Throwables.getRootCause(e);
    assertThat(rootCause).isInstanceOf(IllegalArgumentException.class);
    assertThat(rootCause).hasMessageThat().contains("must be greater than 0");
  }

  @Test
  public void testSetFlagsRule_nonnegativeInt_invalidValue_throwsException() {
    Exception e =
        assertThrows(Exception.class, () -> setFlags.set("nonnegative_integer_flag", "-1"));

    Throwable rootCause = Throwables.getRootCause(e);
    assertThat(rootCause).isInstanceOf(IllegalArgumentException.class);
    assertThat(rootCause).hasMessageThat().contains("must be greater than or equal to 0");
  }

  @Test
  public void testSetFlagsRule_nullPositiveInt_invalidValue_throwsException() {
    Exception e =
        assertThrows(Exception.class, () -> setFlags.set("null_positive_integer_flag", "-5"));

    Throwable rootCause = Throwables.getRootCause(e);
    assertThat(rootCause).isInstanceOf(IllegalArgumentException.class);
    assertThat(rootCause).hasMessageThat().contains("must be greater than 0");
  }

  // ===============================================================================================
  // Strict Collection Validation Tests
  // ===============================================================================================

  @Test
  public void testScanFlags_rawList_throwsException() {
    FlagsManager.setFlagsClassForTest(InvalidFlagsForTesting.RawListFlag.class);

    try {
      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasMessageThat().contains("must not contain raw types");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }

  @Test
  public void testScanFlags_rawMyList_throwsException() {
    FlagsManager.setFlagsClassForTest(InvalidFlagsForTesting.RawMyListFlag.class);

    try {
      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasMessageThat().contains("must not contain raw types");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }

  @Test
  public void testScanFlags_wildcardList_throwsException() {
    FlagsManager.setFlagsClassForTest(InvalidFlagsForTesting.WildcardListFlag.class);

    try {
      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasMessageThat().contains("must not contain raw types");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }

  @Test
  public void testScanFlags_wildcardInsideList_throwsException() {
    FlagsManager.setFlagsClassForTest(InvalidFlagsForTesting.WildcardInsideListFlag.class);

    try {
      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasMessageThat().contains("must not contain raw types");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }

  @Test
  public void testScanFlags_nonGenericRawList_throwsException() {
    FlagsManager.setFlagsClassForTest(InvalidFlagsForTesting.NonGenericRawListFlag.class);

    try {
      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasMessageThat().contains("must not contain raw types");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }

  @Test
  public void testScanFlags_deeplyNestedWildcard_throwsException() {
    FlagsManager.setFlagsClassForTest(InvalidFlagsForTesting.DeeplyNestedWildcardFlag.class);

    try {
      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasMessageThat().contains("must not contain raw types");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }

  @Test
  public void testScanFlags_listWithRawElement_throwsException() {
    FlagsManager.setFlagsClassForTest(InvalidFlagsForTesting.ListWithRawElementFlag.class);

    try {
      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasMessageThat().contains("must not contain raw types");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }

  @Test
  public void testScanFlags_wildcardFlag_throwsException() {
    FlagsManager.setFlagsClassForTest(InvalidFlagsForTesting.WildcardFlag.class);

    try {
      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasMessageThat().contains("must not contain raw types");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }

  @Test
  public void testScanFlags_rawClassFlag_throwsException() {
    FlagsManager.setFlagsClassForTest(InvalidFlagsForTesting.RawClassFlag.class);

    try {
      IllegalStateException e =
          assertThrows(IllegalStateException.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasMessageThat().contains("must not contain raw types");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }

  @Test
  public void testScanFlags_invalidPositiveIntegerDefaultValue_throwsException() {
    FlagsManager.setFlagsClassForTest(
        InvalidFlagsForTesting.InvalidPositiveIntegerDefaultValue.class);

    try {
      ExceptionInInitializerError e =
          assertThrows(ExceptionInInitializerError.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(e).hasCauseThat().hasMessageThat().contains("must be greater than 0");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }

  @Test
  public void testScanFlags_invalidNonnegativeIntegerDefaultValue_throwsException() {
    FlagsManager.setFlagsClassForTest(
        InvalidFlagsForTesting.InvalidNonnegativeIntegerDefaultValue.class);

    try {
      ExceptionInInitializerError e =
          assertThrows(ExceptionInInitializerError.class, () -> FlagsManager.parse(new String[0]));

      assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
      assertThat(e).hasCauseThat().hasMessageThat().contains("must be greater than or equal to 0");
    } finally {
      FlagsManager.setFlagsClassForTest(FlagsForTesting.class);
    }
  }
}
