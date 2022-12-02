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

package com.google.wireless.qa.mobileharness.shared.util;

/** Utility class to perform Luhn algorithm checks. */
public final class LuhnUtil {
  /** Counts correct check digit got the sequence. */
  public static int computeCheckDigit(CharSequence s) {
    int sum = 0;
    // Iterate the string from right to left.
    for (int i = s.length() - 1; i >= 0; i--) {
      int digit = s.charAt(i) - '0';
      // Double each second digit.
      if ((s.length() - i) % 2 != 0) {
        int doubled = digit * 2;
        // Sum of two-digit numbers' digits can be computer subtracting 9 from the number.
        sum += doubled > 9 ? doubled - 9 : doubled;
      } else {
        sum += digit;
      }
    }
    return sum * 9 % 10;
  }

  /** Checks the sequence using Luhn algorithm. */
  public static boolean checkSequence(CharSequence s) {
    return computeCheckDigit(s.subSequence(0, s.length() - 1)) == s.charAt(s.length() - 1) - '0';
  }

  private LuhnUtil() {}
}
