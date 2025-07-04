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

import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MoreSuppliersTest {

  static class CountingSupplier implements ThrowingSupplier<Integer> {
    int callsCount = 0;

    @Override
    public Integer get() {
      callsCount++;
      return callsCount * 10;
    }

    @Override
    public String toString() {
      return "CountingSupplier";
    }
  }

  @Test
  public void memoizeWithExpiration_durationNegative() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> MoreSuppliers.memoizeWithExpiration(() -> "", Duration.ZERO));

    assertThrows(
        IllegalArgumentException.class,
        () -> MoreSuppliers.memoizeWithExpiration(() -> "", Duration.ofMillis(-1)));
  }

  @Test
  public void memoizeWithExpiration_duration() throws Exception {
    CountingSupplier countingSupplier = new CountingSupplier();

    ThrowingSupplier<Integer> memoizedSupplier =
        MoreSuppliers.memoizeWithExpiration(countingSupplier, Duration.ofMillis(75));

    checkExpiration(countingSupplier, memoizedSupplier);
  }

  private void checkExpiration(
      CountingSupplier countingSupplier, ThrowingSupplier<Integer> memoizedSupplier)
      throws Exception {
    // the underlying supplier hasn't executed yet
    assertThat(countingSupplier.callsCount).isEqualTo(0);

    assertThat((int) memoizedSupplier.get()).isEqualTo(10);
    // now it has
    assertThat(countingSupplier.callsCount).isEqualTo(1);

    assertThat((int) memoizedSupplier.get()).isEqualTo(10);
    // it still should only have executed once due to memoization
    assertThat(countingSupplier.callsCount).isEqualTo(1);

    Thread.sleep(150);

    assertThat((int) memoizedSupplier.get()).isEqualTo(20);
    // old value expired
    assertThat(countingSupplier.callsCount).isEqualTo(2);

    assertThat((int) memoizedSupplier.get()).isEqualTo(20);
    // it still should only have executed twice due to memoization
    assertThat(countingSupplier.callsCount).isEqualTo(2);
  }
}
