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

package com.google.wireless.qa.mobileharness.shared.log;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class InfoLogImportanceScopeTest {

  @Test
  public void close_withNestedTryCloseable() {
    try (InfoLogImportanceScope scope1 = new InfoLogImportanceScope()) {
      try (InfoLogImportanceScope scope2 = new InfoLogImportanceScope()) {
        assertThat(InfoLogImportanceScope.inScope()).isTrue();
      }
      assertThat(InfoLogImportanceScope.inScope()).isTrue();
    }
    assertThat(InfoLogImportanceScope.inScope()).isFalse();
  }

  @SuppressWarnings("OpenAndCloseNoTryCatch_Close") // specifically testing close()
  @Test
  public void close_withoutTryCloseable_closeEarlyScopeFirst() {
    InfoLogImportanceScope scope1 = new InfoLogImportanceScope();
    assertThat(InfoLogImportanceScope.inScope()).isTrue();

    InfoLogImportanceScope unusedScope2 = new InfoLogImportanceScope();
    assertThat(InfoLogImportanceScope.inScope()).isTrue();

    scope1.close();
    assertThat(InfoLogImportanceScope.inScope()).isFalse();
  }

  @SuppressWarnings("OpenAndCloseNoTryCatch_Close") // specifically testing close()
  @Test
  public void close_withoutTryCloseable_closeLastScopeFirst() {
    InfoLogImportanceScope scope1 = new InfoLogImportanceScope();
    assertThat(InfoLogImportanceScope.inScope()).isTrue();

    InfoLogImportanceScope scope2 = new InfoLogImportanceScope();
    assertThat(InfoLogImportanceScope.inScope()).isTrue();

    scope2.close();
    assertThat(InfoLogImportanceScope.inScope()).isTrue();

    scope1.close();
    assertThat(InfoLogImportanceScope.inScope()).isFalse();
  }
}
