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

package com.google.devtools.mobileharness.shared.util.junit.rule;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Sets flags for testing. */
public class SetFlagsOss extends TestWatcher {

  /**
   * Sets all flags for a test.
   *
   * <p>Note that this method should be called at most once per test.
   */
  public void setAllFlags(ImmutableMap<String, String> flags) {
    ImmutableList<String> flagsList =
        flags.entrySet().stream()
            .map(e -> String.format("--%s=%s", e.getKey(), e.getValue()))
            .collect(toImmutableList());
    Flags.parse(flagsList.toArray(new String[0]));
  }

  @Override
  protected void finished(Description description) {
    Flags.resetToDefault();
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return super.apply(base, description);
  }
}
