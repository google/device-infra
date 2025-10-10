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

/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.shared.util.base;

import com.google.common.annotations.GwtIncompatible;

/**
 * A BinaryPrefix is a prefix for a unit of digital information (usually a bit or a byte) to
 * indicate multiplication by a power of 1024. Due to historical ambiguity (powers of 1000 vs.
 * powers of 1024), the prefixes follow the IEC approved standards (unambiguously powers of 1024).
 *
 * <p>This enum also provides utility methods to convert across units. See <a
 * href="http://en.wikipedia.org/wiki/Binary_prefix">Binary prefix</a>.
 *
 * @author Kurt Alfred Kluever
 */
@GwtIncompatible
public enum BinaryPrefix {
  /** Represents no prefix (aka, 1024^0 units). */
  UNIT(0),
  /** The prefix that represents 1024^1 units. */
  KIBI(1),
  /** The prefix that represents 1024^2 units. */
  MEBI(2),
  /** The prefix that represents 1024^3 units. */
  GIBI(3),
  /** The prefix that represents 1024^4 units. */
  TEBI(4),
  /** The prefix that represents 1024^5 units. */
  PEBI(5),
  /** The prefix that represents 1024^6 units. */
  EXBI(6);

  private static final long[] multipliers = {
    1L, // Not used: should we leave it or just do -1 when indexing?
    1L << 10, 1L << 20, 1L << 30, 1L << 40, 1L << 50, 1L << 60,
  };

  private static final long[] overflows = {
    0L, // Not used: should we leave it or just do -1 when indexing?
    Long.MAX_VALUE / (1L << 10),
    Long.MAX_VALUE / (1L << 20),
    Long.MAX_VALUE / (1L << 30),
    Long.MAX_VALUE / (1L << 40),
    Long.MAX_VALUE / (1L << 50),
    Long.MAX_VALUE / (1L << 60),
  };

  private final int exponent;

  private BinaryPrefix(int exponent) {
    this.exponent = exponent;
  }

  /**
   * Convert the given value with the given prefix to a value with this prefix. Conversions from
   * smaller to larger magnitudes truncate, so lose precision. For example converting 999 "unit" to
   * "kibi" results in 0. Conversions from larger to smaller magnitudes with arguments that would
   * numerically overflow throw {@link ArithmeticException}.
   *
   * <p>For example, to convert 10 "mebi" to "kibi", use: {@code BinaryPrefix.KIBI.convert(10L,
   * BinaryPrefix.MEBI)}
   */
  public long convert(long sourceValue, BinaryPrefix sourcePrefix) {
    return doConvert(sourcePrefix.exponent - exponent, sourceValue);
  }

  private long doConvert(int delta, long value) {
    if (delta == 0) {
      return value;
    } else if (delta < 0) {
      return value / multipliers[-delta];
    } else if (value > overflows[delta]) {
      throw new ArithmeticException();
    } else if (value < -overflows[delta]) {
      throw new ArithmeticException();
    }
    return value * multipliers[delta];
  }

  /**
   * Returns the prefix of greatest magnitude that can be applied to {@code value} while remaining a
   * whole number.
   */
  public static BinaryPrefix getLargestPrefix(long value) {
    if (value == 0) {
      return UNIT;
    } else if (Long.MIN_VALUE == value) {
      return EXBI;
    } else {
      long absValue = Math.abs(value);
      for (int i = 0; i < multipliers.length; i++) {
        if (multipliers[i] > absValue) {
          return values()[i - 1];
        }
      }
      return EXBI;
    }
  }
}
