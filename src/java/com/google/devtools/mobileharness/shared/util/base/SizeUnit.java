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

package com.google.devtools.mobileharness.shared.util.base;

import java.math.BigInteger;

/** Units for digital information size. */
public enum SizeUnit {
  BITS(1L),
  BYTES(8L);

  private final long bits;

  SizeUnit(long bits) {
    this.bits = bits;
  }

  /** Converts the given amount in this unit to bits. */
  public BigInteger toBits(BigInteger bigAmount) {
    return bigAmount.multiply(BigInteger.valueOf(bits));
  }

  /** Converts the given amount in bits to this unit. */
  public BigInteger fromBits(BigInteger bitAmount) {
    return bitAmount.divide(BigInteger.valueOf(bits));
  }
}
