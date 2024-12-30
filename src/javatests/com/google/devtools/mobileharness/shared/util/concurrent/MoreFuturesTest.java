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

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MoreFuturesTest {

  @Test
  public void allAsMap_allSucceed() throws Exception {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();

    ListenableFuture<ImmutableMap<Integer, String>> result =
        MoreFutures.allAsMap(ImmutableMap.of(1, future1, 2, future2));

    future1.set("1");
    assertThat(result.isDone()).isFalse();
    future2.set("2");
    assertThat(result.isDone()).isTrue();

    assertThat(result.get()).containsExactly(1, "1", 2, "2");
  }

  @Test
  public void allAsMap_oneFail() {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();

    ListenableFuture<ImmutableMap<Integer, String>> result =
        MoreFutures.allAsMap(ImmutableMap.of(1, future1, 2, future2));

    IllegalArgumentException exception = new IllegalArgumentException();
    future1.setException(exception);
    assertThat(result.isDone()).isTrue();

    assertThat(assertThrows(ExecutionException.class, result::get))
        .hasCauseThat()
        .isSameInstanceAs(exception);

    assertThat(future2.isCancelled()).isTrue();
  }

  @Test
  public void allAsMap_cancelResult() {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();

    ListenableFuture<ImmutableMap<Integer, String>> result =
        MoreFutures.allAsMap(ImmutableMap.of(1, future1, 2, future2));
    result.cancel(/* mayInterruptIfRunning= */ false);

    assertThat(future1.isCancelled()).isTrue();
    assertThat(future2.isCancelled()).isTrue();
  }

  @Test
  public void allAsMap_cancelOneFuture() {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();

    ListenableFuture<ImmutableMap<Integer, String>> result =
        MoreFutures.allAsMap(ImmutableMap.of(1, future1, 2, future2));
    future1.cancel(/* mayInterruptIfRunning= */ false);

    assertThat(result.isCancelled()).isTrue();
    assertThat(future2.isCancelled()).isTrue();
  }
}
