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

package com.google.devtools.mobileharness.shared.util.shell;

import static com.google.common.truth.Truth.assertThat;
import static com.google.devtools.mobileharness.shared.util.shell.ShellUtils.prettyPrintArgv;
import static com.google.devtools.mobileharness.shared.util.shell.ShellUtils.shellEscape;
import static com.google.devtools.mobileharness.shared.util.shell.ShellUtils.tokenize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/** Unit tests for the {@link ShellUtils} class. */
public class ShellUtilsTest extends TestCase {

  public void testShellEscape() throws Exception {
    assertEquals("''", shellEscape(""));
    assertEquals("foo", shellEscape("foo"));
    assertEquals("'foo bar'", shellEscape("foo bar"));
    assertEquals("''\\''foo'\\'''", shellEscape("'foo'"));
    assertEquals("'\\'\\''foo\\'\\'''", shellEscape("\\'foo\\'"));
    assertEquals("'${filename%.c}.o'", shellEscape("${filename%.c}.o"));
    assertEquals("'<html!>'", shellEscape("<html!>"));
  }

  public void testPrettyPrintArgv() throws Exception {
    assertEquals("echo '$US' 100", prettyPrintArgv(Arrays.asList("echo", "$US", "100")));
  }

  private void assertTokenize(String copts, String... expectedTokens) throws Exception {
    List<String> actualTokens = new ArrayList<String>();
    tokenize(actualTokens, copts);
    assertEquals(Arrays.asList(expectedTokens), actualTokens);
  }

  public void testTokenizeOnSimpleCopts() throws Exception {
    assertTokenize("-DASMV", "-DASMV");
    assertTokenize("-DNO_UNDERLINE", "-DNO_UNDERLINE");
  }

  public void testTokenizeOnZLIB_COPTS() throws Exception {
    assertTokenize("-DASMV -DNO_UNDERLINE", "-DASMV", "-DNO_UNDERLINE");
  }

  public void testTokenizeOnDES_COPTS() throws Exception {
    assertTokenize("-DDES_LONG=\"unsigned int\" -wd310", "-DDES_LONG=unsigned int", "-wd310");
  }

  public void testTokenizeOnCLEARSILVER_COPTS() throws Exception {
    assertTokenize(
        "-Wno-write-strings -Wno-pointer-sign " + "-Wno-unused-variable -Wno-pointer-to-int-cast",
        "-Wno-write-strings",
        "-Wno-pointer-sign",
        "-Wno-unused-variable",
        "-Wno-pointer-to-int-cast");
  }

  public void testTokenizeOnNestedQuotation() throws Exception {
    assertTokenize("-Dfoo='foo\"bar' -Dwiz", "-Dfoo=foo\"bar", "-Dwiz");
    assertTokenize("-Dfoo=\"foo'bar\" -Dwiz", "-Dfoo=foo'bar", "-Dwiz");
  }

  public void testTokenizeOnBackslashEscapes() throws Exception {
    // This would be easier to grok if we forked+exec'd a shell.

    assertTokenize(
        "-Dfoo=\\'foo -Dbar", // \' not quoted -> '
        "-Dfoo='foo",
        "-Dbar");
    assertTokenize(
        "-Dfoo=\\\"foo -Dbar", // \" not quoted -> "
        "-Dfoo=\"foo",
        "-Dbar");
    assertTokenize(
        "-Dfoo=\\\\foo -Dbar", // \\ not quoted -> \
        "-Dfoo=\\foo",
        "-Dbar");

    assertTokenize(
        "-Dfoo='\\'foo -Dbar", // \' single quoted -> \, close quote
        "-Dfoo=\\foo",
        "-Dbar");
    assertTokenize(
        "-Dfoo='\\\"foo' -Dbar", // \" single quoted -> \"
        "-Dfoo=\\\"foo",
        "-Dbar");
    assertTokenize(
        "-Dfoo='\\\\foo' -Dbar", // \\ single quoted -> \\
        "-Dfoo=\\\\foo",
        "-Dbar");

    assertTokenize(
        "-Dfoo=\"\\'foo\" -Dbar", // \' double quoted -> \'
        "-Dfoo=\\'foo",
        "-Dbar");
    assertTokenize(
        "-Dfoo=\"\\\"foo\" -Dbar", // \" double quoted -> "
        "-Dfoo=\"foo",
        "-Dbar");
    assertTokenize(
        "-Dfoo=\"\\\\foo\" -Dbar", // \\ double quoted -> \
        "-Dfoo=\\foo",
        "-Dbar");
  }

  private void assertTokenizeFails(String copts, String expectedError) {
    try {
      tokenize(new ArrayList<String>(), copts);
      fail();
    } catch (ShellUtils.TokenizationException e) {
      assertThat(e).hasMessageThat().isEqualTo(expectedError);
    }
  }

  public void testTokenizeEmptyString() throws Exception {
    assertTokenize("");
  }

  public void testTokenizeFailsOnUnterminatedQuotation() {
    assertTokenizeFails("-Dfoo=\"bar", "unterminated quotation");
    assertTokenizeFails("-Dfoo='bar", "unterminated quotation");
    assertTokenizeFails("-Dfoo=\"b'ar", "unterminated quotation");
  }
}
