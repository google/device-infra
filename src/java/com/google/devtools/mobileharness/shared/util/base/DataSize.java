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

import com.google.errorprone.annotations.Immutable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A data size, such as '34.5 mebibytes'.
 *
 * <p>This class models a quantity or amount of data in terms of bits. It can be accessed using
 * other size-based units, such as bytes, kibibytes, mebibytes, etc.
 *
 * <p>The range of a data size requires the storage of a number larger than a {@code long}. To
 * achieve this, the class stores a {@code BigInteger} representing the total number of bits.
 *
 * <p>This class is immutable and thread-safe.
 */
@Immutable
public final class DataSize implements Comparable<DataSize> {

  /** Constant for a data size of zero. */
  public static final DataSize ZERO = new DataSize(BigInteger.ZERO);

  /** The total number of bits in the data size. */
  private final BigInteger bits;

  private DataSize(BigInteger bits) {
    this.bits = bits;
  }

  /**
   * Obtains a {@code DataSize} representing a number of bits.
   *
   * @param bits the number of bits, positive or negative
   * @return a {@code DataSize}, not null
   */
  public static DataSize ofBits(long bits) {
    return new DataSize(BigInteger.valueOf(bits));
  }

  /**
   * Obtains a {@code DataSize} representing a number of bytes.
   *
   * @param bytes the number of bytes, positive or negative
   * @return a {@code DataSize}, not null
   */
  public static DataSize ofBytes(long bytes) {
    return of(bytes, BinaryPrefix.UNIT, SizeUnit.BYTES);
  }

  /**
   * Obtains a {@code DataSize} representing an amount in the specified unit and prefix.
   *
   * @param amount the amount of the data size, measured in terms of the unit and prefix, positive
   *     or negative
   * @param prefix the binary prefix for the unit
   * @param unit the unit that the data size is measured in
   * @return a {@code DataSize}, not null
   */
  public static DataSize of(long amount, BinaryPrefix prefix, SizeUnit unit) {
    return new DataSize(unit.toBits(BigInteger.valueOf(BinaryPrefix.UNIT.convert(amount, prefix))));
  }

  /**
   * Returns the total number of bits in this data size.
   *
   * @return the total number of bits
   */
  public long toBits() {
    return bits.longValueExact();
  }

  /**
   * Returns the total number of bytes in this data size.
   *
   * @return the total number of bytes
   */
  public long toBytes() {
    return to(BinaryPrefix.UNIT, SizeUnit.BYTES);
  }

  /**
   * Returns the value of this data size in the given units, truncating if necessary.
   *
   * @param prefix the binary prefix for the unit
   * @param unit the unit to convert to
   * @return the value of this data size in the given units
   */
  public long to(BinaryPrefix prefix, SizeUnit unit) {
    return prefix.convert(unit.fromBits(bits).longValueExact(), BinaryPrefix.UNIT);
  }

  /**
   * Returns a copy of this data size with the specified data size added.
   *
   * <p>This instance is immutable and unaffected by this method call.
   *
   * @param other the data size to add, positive or negative, not null
   * @return a {@code DataSize} based on this data size with the specified data size added, not null
   */
  public DataSize plus(DataSize other) {
    if (other.isZero()) {
      return this;
    }
    return new DataSize(this.bits.add(other.bits));
  }

  /**
   * Returns a copy of this data size with the specified data size subtracted.
   *
   * <p>This instance is immutable and unaffected by this method call.
   *
   * @param other the data size to subtract, positive or negative, not null
   * @return a {@code DataSize} based on this data size with the specified data size subtracted, not
   *     null
   */
  public DataSize minus(DataSize other) {
    if (other.isZero()) {
      return this;
    }
    return new DataSize(this.bits.subtract(other.bits));
  }

  /**
   * Returns a copy of this duration multiplied by the scalar.
   *
   * <p>This instance is immutable and unaffected by this method call.
   *
   * @param multiplicand the value to multiply the duration by, positive or negative
   * @return a {@code DataSize} based on this duration multiplied by the specified scalar, not null
   * @throws ArithmeticException if numeric overflow occurs
   */
  public DataSize multipliedBy(double multiplicand) {
    if (multiplicand == 0) {
      return ZERO;
    }
    if (multiplicand == 1) {
      return this;
    }
    BigDecimal newBits =
        new BigDecimal(bits)
            .multiply(BigDecimal.valueOf(multiplicand))
            .setScale(0, RoundingMode.DOWN);
    return new DataSize(newBits.toBigIntegerExact());
  }

  /**
   * Checks if this data size is zero length.
   *
   * @return true if this data size has a total length equal to zero
   */
  public boolean isZero() {
    return this.bits.signum() == 0;
  }

  @Override
  public int compareTo(DataSize other) {
    return this.bits.compareTo(other.bits);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof DataSize other) {
      return Objects.equals(this.bits, other.bits);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(bits);
  }

  @Override
  public String toString() {
    if (bits.equals(BigInteger.ZERO)) {
      return "0B";
    }
    BinaryPrefix largestPrefix = BinaryPrefix.getLargestPrefix(bytes());
    if (largestPrefix == BinaryPrefix.UNIT) {
      return String.format("%dB", bytes());
    }
    double value = (double) bytes() / BinaryPrefix.UNIT.convert(1L, largestPrefix);
    return String.format("%.1f%ciB", value, largestPrefix.name().charAt(0));
  }

  private long bytes() {
    return toBytes();
  }
}
