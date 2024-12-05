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

import com.google.common.base.CharMatcher;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** utility functions for parsing email addresses. */
public class EmailValidationUtil {

  /**
   * validates emails like name@domain.tld.
   *
   * @param email non null.
   * @return true iff email matches the RFC 822 addr-spec production.
   */
  @CanIgnoreReturnValue
  public static boolean isValidShortEmail(String email) {
    // Avoid DOS by attackers who supply long crafted inputs.
    return email.length() < 256 && isAddrSpec(email);
  }

  private EmailValidationUtil() {
    // uninstantiable
  }

  ////// Below is a hand-coded single-forward-pass implementation of
  ////// ADDR_SPEC_PATTERN.matcher(s).matches().
  ////// Per bug 6124825 there are clients of this class that require fast
  ////// concurrent access, so memoizing regular expression matches is
  ////// problematic.

  //// CHARACTER CLASSES from 3.3.  Must be put inside [...]
  // specials    =  "(" / ")" / "<" / ">" / "@"  ; Must be in quoted-
  //             /  "," / ";" / ":" / "\" / <">  ;  string, to use
  //             /  "." / "[" / "]"              ;  within a word.
  private static final CharMatcher IS_SPECIAL = CharMatcher.anyOf("()<>@,;:\\\".[]");

  // CTL         =  <any ASCII control           ; (  0- 37,  0.- 31.)
  //                 character and DEL>          ; (    177,     127.)
  private static final CharMatcher IS_CTL =
      CharMatcher.inRange('\000', '\037').or(CharMatcher.is('\177'));

  // atom        =  1*<any CHAR except specials, SPACE and CTLs>
  private static final CharMatcher IS_ATOM =
      IS_SPECIAL.or(CharMatcher.is(' ')).or(IS_CTL).negate().precomputed();

  //// LEXICAL PRODUCTIONS from 3.3
  // The lexical productions are implemented as functions from a string and
  // the character index at which to start matching to the character index of
  // the end of the match or -1 if no match.

  // quoted-pair =  "\" CHAR                     ; may quote any char
  // qtext       =  <any CHAR excepting <">,     ; => may be folded
  //                 "\" & CR, and including linear-white-space>
  // quoted-string = <"> *(qtext/quoted-pair) <">; Regular qtext or
  //                                             ;   quoted chars.
  // msamuel: The grammar as described in 822, interpreted literally, would
  // lead to a regular expression that requires backtracking, so I modified
  // qtext to be any character but " or \ so that the \ case is always handled
  // by the quoted pair production.  This is functionally equivalent.
  private static int matchQuotedString(String s, int pos) {
    int n = s.length();
    if (pos >= n || '"' != s.charAt(pos)) {
      return -1;
    }
    int end = pos + 1;
    while (end < n) {
      char ch = s.charAt(end);
      ++end;
      if (ch == '\\') {
        ++end;
      } else if (ch == '"') {
        return end;
      }
    }
    return -1;
  }

  // atom        =  1*<any CHAR except specials, SPACE and CTLs>
  private static int matchAtom(String s, int pos) {
    int end = pos;
    int n = s.length();
    while (end < n && IS_ATOM.matches(s.charAt(end))) {
      ++end;
    }
    return end != pos ? end : -1; // 1* means match at least one.
  }

  // word        =  atom / quoted-string
  private static int matchWord(String s, int pos) {
    int end = matchAtom(s, pos);
    if (end == -1) {
      end = matchQuotedString(s, pos);
    }
    return end;
  }

  // local-part = word *("." word) ; uninterpreted
  //                               ; case-preserved
  private static int matchLocalPart(String s, int pos) {
    int end = matchWord(s, pos);
    if (end != -1) {
      int n = s.length();
      // Match . greedily since a dot cannot appear after a local part
      // unless followed by a word in an addr-spec.
      while (end < n && s.charAt(end) == '.') {
        end = matchWord(s, end + 1);
        if (end == -1) {
          return -1;
        }
      }
    }
    return end;
  }

  //// SYNTACTIC PRODUCTIONS from 6.1
  // domain-ref = atom ; symbolic reference
  // sub-domain = domain-ref / domain-literal
  // according to 6.2.3
  //   Note:  THE USE OF DOMAIN-LITERALS IS STRONGLY DISCOURAGED.  It
  //          is  permitted  only  as  a means of bypassing temporary
  //          system limitations, such as name tables which  are  not
  //          complete.
  // msamuel: and it introduces a second quoting mechanism which breaks the
  //          splitter
  private static int matchSubDomain(String s, int pos) {
    return matchAtom(s, pos);
  }

  // domain = sub-domain *("." sub-domain)
  private static int matchDomain(String s, int pos) {
    int end = matchSubDomain(s, pos);
    if (end != -1) {
      int n = s.length();
      // Match . greedily since a dot cannot appear after a sub-domain
      // unless followed by another sub-domain in an addr-spec.
      while (end < n && s.charAt(end) == '.') {
        end = matchSubDomain(s, end + 1);
        if (end == -1) {
          return -1;
        }
      }
    }
    return end;
  }

  // addr-spec = local-part "@" domain ; global address
  private static int matchAddrSpec(String s, int pos) {
    int end = matchLocalPart(s, pos);
    if (end != -1 && end < s.length() && s.charAt(end) == '@') {
      return matchDomain(s, end + 1);
    }
    return -1;
  }

  /**
   * Tests whether s matches the RFC 822 addr-spec production.
   *
   * @return the same as {@code ADDR_SPEC_PATTERN.matcher(s).matches()} but faster.
   */
  private static boolean isAddrSpec(String s) {
    return s.length() == matchAddrSpec(s, 0);
  }
}
