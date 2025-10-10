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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DataSize}. */
@RunWith(JUnit4.class)
public final class DataSizeTest {

  @Test
  public void ofBits() {
    assertThat(DataSize.ofBits(8L).toBits()).isEqualTo(8L);
  }

  @Test
  public void ofBytes() {
    assertThat(DataSize.ofBytes(1L).toBits()).isEqualTo(8L);
  }

  @Test
  public void of() {
    assertThat(DataSize.of(1L, BinaryPrefix.KIBI, SizeUnit.BYTES).toBytes()).isEqualTo(1024L);
    assertThat(DataSize.of(1L, BinaryPrefix.KIBI, SizeUnit.BYTES).toBits()).isEqualTo(8192L);
    assertThat(DataSize.of(-1L, BinaryPrefix.KIBI, SizeUnit.BYTES).toBits()).isEqualTo(-8192L);
  }

  @Test
  public void toBits() {
    assertThat(DataSize.ofBytes(1L).toBits()).isEqualTo(8L);
  }

  @Test
  public void toBytes() {
    assertThat(DataSize.ofBits(8L).toBytes()).isEqualTo(1L);
  }

  @Test
  public void to() {
    assertThat(DataSize.ofBytes(1024L).to(BinaryPrefix.KIBI, SizeUnit.BYTES)).isEqualTo(1L);
  }

  @Test
  public void plus() {
    assertThat(DataSize.ofBytes(1L).plus(DataSize.ofBytes(2L))).isEqualTo(DataSize.ofBytes(3L));
    assertThat(DataSize.of(1L, BinaryPrefix.KIBI, SizeUnit.BYTES).plus(DataSize.ofBytes(1L)))
        .isEqualTo(DataSize.ofBytes(1025L));
  }

  @Test
  public void minus() {
    assertThat(DataSize.ofBytes(1L).minus(DataSize.ofBytes(2L))).isEqualTo(DataSize.ofBytes(-1L));
    assertThat(DataSize.of(1L, BinaryPrefix.KIBI, SizeUnit.BYTES).minus(DataSize.ofBytes(1L)))
        .isEqualTo(DataSize.ofBytes(1023L));
  }

  @Test
  public void multipliedBy() {
    assertThat(DataSize.ofBytes(1L).multipliedBy(2L)).isEqualTo(DataSize.ofBytes(2L));
    assertThat(DataSize.of(1L, BinaryPrefix.KIBI, SizeUnit.BYTES).multipliedBy(0.5))
        .isEqualTo(DataSize.ofBytes(512L));
    assertThat(DataSize.of(1L, BinaryPrefix.KIBI, SizeUnit.BYTES).multipliedBy(0.3333).toBits())
        .isEqualTo(2730L);
    assertThat(DataSize.ofBits(1L).multipliedBy(0.3333)).isEqualTo(DataSize.ZERO);
  }

  @Test
  public void isZero() {
    assertThat(DataSize.ZERO.isZero()).isTrue();
    assertThat(DataSize.ofBytes(1L).isZero()).isFalse();
  }

  @Test
  public void toString_expectedResult() {
    assertThat(DataSize.ZERO.toString()).isEqualTo("0B");
    assertThat(DataSize.ofBytes(100L).toString()).isEqualTo("100B");
    assertThat(DataSize.of(1L, BinaryPrefix.KIBI, SizeUnit.BYTES).toString()).isEqualTo("1.0KiB");
    assertThat(DataSize.of(1536L, BinaryPrefix.UNIT, SizeUnit.BYTES).toString())
        .isEqualTo("1.5KiB");
    assertThat(DataSize.ofBytes(9657224008L).toString()).isEqualTo("9.0GiB");
    assertThat(DataSize.ofBytes(9997224008L).toString()).isEqualTo("9.3GiB");
    assertThat(DataSize.of(888L, BinaryPrefix.PEBI, SizeUnit.BYTES).toString())
        .isEqualTo("888.0PiB");
  }

  @Test
  public void compareTo_expectedResult() {
    DataSize oneKibiByte = DataSize.of(1L, BinaryPrefix.KIBI, SizeUnit.BYTES);
    DataSize tenKibiBits = DataSize.of(10L, BinaryPrefix.KIBI, SizeUnit.BITS);
    assertThat(oneKibiByte.compareTo(tenKibiBits)).isEqualTo(-1);
    assertThat(tenKibiBits.compareTo(oneKibiByte)).isEqualTo(1);
    assertThat(oneKibiByte)
        .isEquivalentAccordingToCompareTo(DataSize.of(1024L, BinaryPrefix.UNIT, SizeUnit.BYTES));
  }

  @Test
  public void equals_expectedResult() {
    DataSize oneKibiByte = DataSize.of(1L, BinaryPrefix.KIBI, SizeUnit.BYTES);
    DataSize alsoOneKibiByte = DataSize.of(1024L, BinaryPrefix.UNIT, SizeUnit.BYTES);
    DataSize tenKibiBits = DataSize.of(10L, BinaryPrefix.KIBI, SizeUnit.BITS);
    assertThat(oneKibiByte).isEqualTo(alsoOneKibiByte);
    assertThat(oneKibiByte).isNotEqualTo(tenKibiBits);
  }

  @Test
  public void overflow() {
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MAX_VALUE, BinaryPrefix.EXBI, SizeUnit.BYTES));
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MIN_VALUE, BinaryPrefix.EXBI, SizeUnit.BYTES));
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MAX_VALUE, BinaryPrefix.PEBI, SizeUnit.BYTES));
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MIN_VALUE, BinaryPrefix.PEBI, SizeUnit.BYTES));
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MAX_VALUE, BinaryPrefix.TEBI, SizeUnit.BYTES));
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MIN_VALUE, BinaryPrefix.TEBI, SizeUnit.BYTES));
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MAX_VALUE, BinaryPrefix.GIBI, SizeUnit.BYTES));
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MIN_VALUE, BinaryPrefix.GIBI, SizeUnit.BYTES));
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MAX_VALUE, BinaryPrefix.MEBI, SizeUnit.BYTES));
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MIN_VALUE, BinaryPrefix.MEBI, SizeUnit.BYTES));
    assertThrows(
        ArithmeticException.class,
        () -> DataSize.of(Long.MAX_VALUE, BinaryPrefix.KIBI, SizeUnit.BYTES));
    DataSize max = DataSize.of(Long.MAX_VALUE, BinaryPrefix.UNIT, SizeUnit.BYTES);
    assertThrows(ArithmeticException.class, () -> max.toBits());
    DataSize min = DataSize.of(Long.MIN_VALUE, BinaryPrefix.UNIT, SizeUnit.BYTES);
    assertThrows(ArithmeticException.class, () -> min.toBits());
  }
}
