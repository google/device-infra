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

package com.google.devtools.mobileharness.shared.util.file.local;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class UnzipFileNameTest {
  @Test
  // no special characters
  @TestParameters("{arg: 'foo_bar', expected: false}")
  // regex wildcard but not unzip wildcard
  @TestParameters("{arg: 'foo.bar', expected: false}")
  // unzip wildcards
  @TestParameters("{arg: 'foo*bar', expected: true}")
  @TestParameters("{arg: 'foo?bar', expected: true}")
  @TestParameters("{arg: 'foo[a-z]bar', expected: true}")
  // escaped wildcards
  @TestParameters("{arg: 'foo\\*bar', expected: false}")
  @TestParameters("{arg: 'foo\\?bar', expected: false}")
  @TestParameters("{arg: 'foo\\[a-z]bar', expected: false}")
  // escaped backslash
  @TestParameters("{arg: 'foo\\\\*bar', expected: true}")
  public void testHasWildcard(String arg, boolean expected) {
    UnzipFileName name = new UnzipFileName(arg);
    assertThat(name.hasWildcard()).isEqualTo(expected);
  }

  @Test
  @TestParameters("{arg: 'foo.bar', expected: 'foo.bar'}")
  @TestParameters("{arg: 'foo*bar', expected: 'foo*bar'}")
  @TestParameters("{arg: 'foo\\*bar', expected: 'foo*bar'}")
  @TestParameters("{arg: 'foo\\\\*bar', expected: 'foo\\*bar'}")
  public void testUnescape(String arg, String expected) {
    UnzipFileName name = new UnzipFileName(arg);
    assertThat(name.path()).isEqualTo(expected);
  }

  @Test
  // no wildcards
  @TestParameters("{arg: 'foo_bar', expected: '^\\Qfoo_bar\\E$'}")
  @TestParameters("{arg: 'foo.bar', expected: '^\\Qfoo.bar\\E$'}")
  // wildcards
  @TestParameters("{arg: 'foo*bar', expected: '^\\Qfoo\\E.*\\Qbar\\E$'}")
  @TestParameters("{arg: 'foo?bar', expected: '^\\Qfoo\\E.\\Qbar\\E$'}")
  @TestParameters("{arg: 'foo[a-z]bar', expected: '^\\Qfoo\\E[a-z]\\Qbar\\E$'}")
  // escaped wildcards
  @TestParameters("{arg: 'foo\\*bar', expected: '^\\Qfoo*bar\\E$'}")
  @TestParameters("{arg: 'foo\\?bar', expected: '^\\Qfoo?bar\\E$'}")
  @TestParameters("{arg: 'foo\\[a-z]bar', expected: '^\\Qfoo[a-z]bar\\E$'}")
  // escaped backslash
  @TestParameters("{arg: 'foo\\\\*bar', expected: '^\\Qfoo\\\\E.*\\Qbar\\E$'}")
  @TestParameters("{arg: '\\\\E', expected: '^\\Q\\\\E\\QE\\E$'}")
  // brackets
  @TestParameters("{arg: '[]]', expected: '^[]\\Q]\\E$'}")
  @TestParameters("{arg: '[\\]]', expected: '^[\\]]$'}")
  @TestParameters("{arg: '[\\\\]', expected: '^[\\\\]$'}")
  @TestParameters("{arg: '[!!]', expected: '^[^!]$'}")
  @TestParameters("{arg: '[^^]', expected: '^[^\\^]$'}")
  @TestParameters("{arg: '[\\!]', expected: '^[!]$'}")
  @TestParameters("{arg: '[\\^]', expected: '^[\\^]$'}")
  @TestParameters("{arg: '[abc', expected: '^[abc$'}") // unclosed bracket
  public void testRegex(String arg, String expected) {
    UnzipFileName name = new UnzipFileName(arg);
    assertThat(name.regex()).isEqualTo(expected);
  }
}
