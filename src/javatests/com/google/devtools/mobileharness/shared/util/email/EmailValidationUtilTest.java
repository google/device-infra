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

package com.google.devtools.mobileharness.shared.util.email;

import junit.framework.TestCase;

/** Tests for {@code EmailValidationUtil}. */
public class EmailValidationUtilTest extends TestCase {

  // Test array values are as follows:
  // TESTS[][0] - Input email
  // TESTS[][1] - Short email (default)
  // TESTS[][2] - Username (default)
  // TESTS[][3] - Opt: Short email with plus stripped (if this doesn't exist, assume equal to [1])
  // TESTS[][3] - Opt: Username with plus stripped (if this doesn't exist, assume equal to [2])
  static final String[][] TESTS = {
    {"msamuel@google.com", "msamuel@google.com", "msamuel"},
    {
      "msamuel+test@google.com",
      "msamuel+test@google.com",
      "msamuel+test",
      "msamuel@google.com",
      "msamuel"
    },
    {
      "msamuel+test+test@google.com",
      "msamuel+test+test@google.com",
      "msamuel+test+test",
      "msamuel@google.com",
      "msamuel"
    },
    {"+test@google.com", "+test@google.com", "+test", "@google.com", null},
    {"foo@example+com", "foo@example+com", "foo"},
    {"foo@bar.com", "foo@bar.com", "foo"},
    {"foo@tu", "foo@tu", "foo"},
    {"foo@co.uk", "foo@co.uk", "foo"},
    {"foo", null, null},
    {"foo blah@blah", null, null},
    {"Mike Samuel <msamuel@google.com>", "msamuel@google.com", "msamuel"},
    {
      "Mike Samuel <msamuel+test@google.com>",
      "msamuel+test@google.com",
      "msamuel+test",
      "msamuel@google.com",
      "msamuel"
    },
    {"\"Samuel, Mike\" <mikesamuel@gmail.com>", "mikesamuel@gmail.com", "mikesamuel"},
    {"\"A \\\"quoted\\\" @string\" <foo@bar>", "foo@bar", "foo"},
    {"\"Another quoted string\\\\\" <slash@before.quote>", "slash@before.quote", "slash"},
    {"NotmuchSpace<inthis@email.address>", "inthis@email.address", "inthis"},
    {"bad1@@bad", null, null},
    {"@bad2", null, null},
    {"bad3@", null, null},
    {"bad4@bad@badness.bad", null, null},
    {"bad5@bad..bad", null, null},
    {"bad6@.bad", null, null},
    {"bad7@.", null, null},
    {"bad8@bad.", null, null},
    {"bad9.@bad", null, null},
    {"KSecy@Other-Host", "KSecy@Other-Host", "KSecy"},
    {"Sam.Irving@Reg.Organization", "Sam.Irving@Reg.Organization", "Sam.Irving"},
    {"George Jones <Group@Some-Reg.An-Org>", "Group@Some-Reg.An-Org", "Group"},
    {"Al.Neuman@MAD.Publisher", "Al.Neuman@MAD.Publisher", "Al.Neuman"},
    {"Tom Softwood <Balsa@Tree.Root>", "Balsa@Tree.Root", "Balsa"},
    {"\"Sam Irving\"@Other-Host", "\"Sam Irving\"@Other-Host", "\"Sam Irving\""},
    {"\"Quoted name\"@host.tld", "\"Quoted name\"@host.tld", "\"Quoted name\""},
    {"<\"No real name\"@host.tld>", null, null},
    {"cron-daemon@127.0.0.1", "cron-daemon@127.0.0.1", "cron-daemon"},
  };

  public void testValidate() throws Exception {
    for (String[] test : TESTS) {
      boolean pass = test[1] != null;

      if (!pass) {
        assertTrue(test[0], !EmailValidationUtil.isValidShortEmail(test[0]));
      }
    }
  }
}
