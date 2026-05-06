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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Sets flags for testing.
 *
 * <p>Similar to standard JUnit {@code SetFlags} rules, but supporting both Mobile Harness
 * open-source flags and Google internal flags.
 */
public class SetFlagsOss implements TestRule {

  private final SetFlags setFlags = new SetFlags();

  /** Sets all flags for a test. */
  public void setAllFlags(ImmutableMap<String, String> flags) {
    flags.forEach(setFlags::set);
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return setFlags.apply(base, description);
  }
}
