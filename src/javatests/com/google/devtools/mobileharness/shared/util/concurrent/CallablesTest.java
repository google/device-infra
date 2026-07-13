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

package com.google.devtools.mobileharness.shared.util.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CallablesTest {

  @Test
  public void runAll_mobileHarnessRunnable_success() throws Exception {
    boolean[] executed = new boolean[2];
    Callables.runAll(
        (MobileHarnessRunnable) () -> executed[0] = true,
        (MobileHarnessRunnable) () -> executed[1] = true);
    assertThat(executed[0]).isTrue();
    assertThat(executed[1]).isTrue();
  }

  @Test
  public void runAll_mobileHarnessRunnable_exceptionSuppression() {
    MobileHarnessException ex1 = new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "ex1");
    RuntimeException ex2 = new RuntimeException("ex2");

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () ->
                Callables.runAll(
                    (MobileHarnessRunnable)
                        () -> {
                          throw ex1;
                        },
                    (MobileHarnessRunnable)
                        () -> {
                          throw ex2;
                        }));

    assertThat(thrown).isSameInstanceAs(ex1);
    assertThat(thrown.getSuppressed()).asList().containsExactly(ex2);
  }

  @Test
  public void runAll_mobileHarnessRunnable_interruptedExceptionRestored() {
    MobileHarnessException ex1 = new MobileHarnessException(BasicErrorId.NON_MH_EXCEPTION, "ex1");
    InterruptedException ex2 = new InterruptedException("interrupted");

    MobileHarnessException thrown =
        assertThrows(
            MobileHarnessException.class,
            () ->
                Callables.runAll(
                    (MobileHarnessRunnable)
                        () -> {
                          throw ex1;
                        },
                    (MobileHarnessRunnable)
                        () -> {
                          throw ex2;
                        }));

    assertThat(thrown).isSameInstanceAs(ex1);
    assertThat(thrown.getSuppressed()).asList().containsExactly(ex2);
    assertThat(Thread.interrupted()).isTrue();
  }
}
