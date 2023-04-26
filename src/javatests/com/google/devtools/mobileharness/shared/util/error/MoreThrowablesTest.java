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

package com.google.devtools.mobileharness.shared.util.error;

import static com.google.common.truth.Correspondence.transforming;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MoreThrowablesTest {

  @Test
  public void shortDebugCurrentStackTrace() throws Exception {
    String currentStackTrace = f1();

    assertThat(
            // Splits by "<--" and removes the "[]" prefix/suffix.
            Splitter.onPattern("<--|[\\[\\]]")
                .omitEmptyStrings()
                .trimResults()
                .splitToList(currentStackTrace))
        .comparingElementsUsing(
            transforming(
                (Function<String, String>)
                    input -> {
                      // Removes the "(...)" suffix.
                      int endIndex = input.indexOf('(');
                      endIndex = endIndex == -1 ? input.length() : endIndex;
                      return input.substring(0, endIndex);
                    },
                "has a prefix of"))
        .containsExactly(
            "com.google.devtools.mobileharness.shared.util.error.MoreThrowablesTest.f4",
            "com.google.devtools.mobileharness.shared.util.error.MoreThrowablesTest.f3",
            "com.google.devtools.mobileharness.shared.util.error.MoreThrowablesTest.f2",
            "com.google.devtools.mobileharness.shared.util.error.MoreThrowablesTest.f1",
            "com.google.devtools.mobileharness.shared.util.error.MoreThrowablesTest.shortDebugCurrentStackTrace")
        .inOrder();
  }

  private String f1() {
    return f2();
  }

  private String f2() {
    return f3();
  }

  private String f3() {
    return f4();
  }

  private String f4() {
    return MoreThrowables.shortDebugCurrentStackTrace(5L);
  }
}
