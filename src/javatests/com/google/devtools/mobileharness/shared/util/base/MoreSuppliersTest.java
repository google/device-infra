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

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
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

  static class FailingCountingSupplier implements ThrowingSupplier<Integer> {
    int callsCount = 0;
    boolean fail = false;

    @Override
    public Integer get() throws MobileHarnessException {
      callsCount++;
      if (fail) {
        throw new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "simulated error");
      }
      return callsCount * 10;
    }
  }

  @Test
  public void memoizeWithExpiration_refreshIfLastRetrievalFailed_firstCallFails() throws Exception {
    FailingCountingSupplier countingSupplier = new FailingCountingSupplier();
    countingSupplier.fail = true;

    ThrowingSupplier<Integer> memoizedSupplier =
        MoreSuppliers.memoizeWithExpiration(
            countingSupplier, Duration.ofMillis(75), /* refreshIfLastRetrievalFailed= */ true);

    // Call 1: fails
    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> memoizedSupplier.get());
    assertThat(exception).hasMessageThat().contains("simulated error");
    assertThat(countingSupplier.callsCount).isEqualTo(1);

    // Call 2: fails again (not cached, retries!)
    assertThrows(MobileHarnessException.class, () -> memoizedSupplier.get());
    assertThat(countingSupplier.callsCount).isEqualTo(2);

    // Stop failing
    countingSupplier.fail = false;

    // Call 3: succeeds
    assertThat((int) memoizedSupplier.get()).isEqualTo(30);
    assertThat(countingSupplier.callsCount).isEqualTo(3);

    // Call 4: cached (within 75ms)
    assertThat((int) memoizedSupplier.get()).isEqualTo(30);
    assertThat(countingSupplier.callsCount).isEqualTo(3);
  }

  @Test
  public void memoizeWithExpiration_refreshIfLastRetrievalFailed_reloadFails() throws Exception {
    FailingCountingSupplier countingSupplier = new FailingCountingSupplier();

    ThrowingSupplier<Integer> memoizedSupplier =
        MoreSuppliers.memoizeWithExpiration(
            countingSupplier, Duration.ofMillis(75), /* refreshIfLastRetrievalFailed= */ true);

    // Call 1: succeeds, returns 10
    assertThat((int) memoizedSupplier.get()).isEqualTo(10);
    assertThat(countingSupplier.callsCount).isEqualTo(1);

    // Call 2: cached
    assertThat((int) memoizedSupplier.get()).isEqualTo(10);
    assertThat(countingSupplier.callsCount).isEqualTo(1);

    // Wait until expired
    Thread.sleep(150);

    // Let the supplier fail on the next call
    countingSupplier.fail = true;

    // Call 3: expires, tries delegate, fails and throws the exception
    assertThrows(MobileHarnessException.class, () -> memoizedSupplier.get());
    assertThat(countingSupplier.callsCount).isEqualTo(2);

    // Call 4: since last time failed, it will retry delegate because refreshIfLastRetrievalFailed
    // is true.
    // Tries delegate again, still fails and throws the exception
    assertThrows(MobileHarnessException.class, () -> memoizedSupplier.get());
    assertThat(countingSupplier.callsCount).isEqualTo(3);

    // Stop failing
    countingSupplier.fail = false;

    // Call 5: tries delegate again because last retrieval failed, succeeds, returns 40!
    assertThat((int) memoizedSupplier.get()).isEqualTo(40);
    assertThat(countingSupplier.callsCount).isEqualTo(4);

    // Call 6: cached since last retrieval succeeded
    assertThat((int) memoizedSupplier.get()).isEqualTo(40);
    assertThat(countingSupplier.callsCount).isEqualTo(4);
  }

  @Test
  public void memoize_refreshIfLastRetrievalFailed_firstCallFails() throws Exception {
    FailingCountingSupplier countingSupplier = new FailingCountingSupplier();
    countingSupplier.fail = true;

    ThrowingSupplier<Integer> memoizedSupplier =
        MoreSuppliers.memoize(countingSupplier, /* refreshIfLastRetrievalFailed= */ true);

    // Call 1: fails
    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> memoizedSupplier.get());
    assertThat(exception).hasMessageThat().contains("simulated error");
    assertThat(countingSupplier.callsCount).isEqualTo(1);

    // Call 2: fails again (not cached, retries!)
    assertThrows(MobileHarnessException.class, () -> memoizedSupplier.get());
    assertThat(countingSupplier.callsCount).isEqualTo(2);

    // Stop failing
    countingSupplier.fail = false;

    // Call 3: succeeds
    assertThat((int) memoizedSupplier.get()).isEqualTo(30);
    assertThat(countingSupplier.callsCount).isEqualTo(3);

    // Call 4: cached
    assertThat((int) memoizedSupplier.get()).isEqualTo(30);
    assertThat(countingSupplier.callsCount).isEqualTo(3);
  }

  @Test
  public void memoize_withoutRefreshIfLastRetrievalFailed_firstCallFails() throws Exception {
    FailingCountingSupplier countingSupplier = new FailingCountingSupplier();
    countingSupplier.fail = true;

    ThrowingSupplier<Integer> memoizedSupplier =
        MoreSuppliers.memoize(countingSupplier, /* refreshIfLastRetrievalFailed= */ false);

    // Call 1: fails
    MobileHarnessException exception =
        assertThrows(MobileHarnessException.class, () -> memoizedSupplier.get());
    assertThat(exception).hasMessageThat().contains("simulated error");
    assertThat(countingSupplier.callsCount).isEqualTo(1);

    // Call 2: fails again but is cached (callsCount is still 1)
    assertThrows(MobileHarnessException.class, () -> memoizedSupplier.get());
    assertThat(countingSupplier.callsCount).isEqualTo(1);
  }
}
