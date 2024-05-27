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

package com.google.devtools.mobileharness.shared.constant;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.LogImportanceScope;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LogRecordImportanceTest {

  @Test
  public void logImportanceScope() {
    assertThat(LogImportanceScope.getCurrentImportance()).isEmpty();

    try (var ignored1 = new LogImportanceScope(Importance.DEBUG)) {
      assertThat(LogImportanceScope.getCurrentImportance()).hasValue(Importance.DEBUG);

      try (var ignored2 = new LogImportanceScope(Importance.IMPORTANT)) {
        assertThat(LogImportanceScope.getCurrentImportance()).hasValue(Importance.IMPORTANT);
      }

      assertThat(LogImportanceScope.getCurrentImportance()).hasValue(Importance.DEBUG);
    }

    assertThat(LogImportanceScope.getCurrentImportance()).isEmpty();

    LogImportanceScope scope1 = new LogImportanceScope(Importance.DEBUG);
    LogImportanceScope scope2 = new LogImportanceScope(Importance.IMPORTANT);
    assertThat(LogImportanceScope.getCurrentImportance()).hasValue(Importance.IMPORTANT);

    scope1.close();
    assertThat(LogImportanceScope.getCurrentImportance()).isEmpty();

    scope2.close();
    assertThat(LogImportanceScope.getCurrentImportance()).isEmpty();
  }
}
