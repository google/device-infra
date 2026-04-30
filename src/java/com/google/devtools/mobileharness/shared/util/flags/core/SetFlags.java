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

package com.google.devtools.mobileharness.shared.util.flags.core;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** JUnit rule for overriding flag values during tests and restoring them automatically. */
public class SetFlags implements TestRule {

  private final Map<Flag<?>, FlagBackup> backups = new HashMap<>();

  /**
   * Overrides the value of a flag by its name.
   *
   * @throws IllegalStateException if the flag name is unknown
   */
  @CanIgnoreReturnValue
  public SetFlags set(String name, String value) {
    FlagsManager.FlagEntry entry = FlagsManager.getFlagByName(name);
    saveBackup(entry.flag());
    FlagsManager.setFromString(entry, value);
    return this;
  }

  /**
   * Resets the value of a flag to its default value by its name.
   *
   * @throws IllegalStateException if the flag name is unknown
   */
  @CanIgnoreReturnValue
  public SetFlags reset(String name) {
    FlagsManager.FlagEntry entry = FlagsManager.getFlagByName(name);
    saveBackup(entry.flag());
    entry.flag().resetForTest();
    return this;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new SetFlagsStatement(base);
  }

  private void saveBackup(Flag<?> flag) {
    backups.computeIfAbsent(
        flag, (Flag<?> key) -> new FlagBackup(key, key.get(), key.wasSetFromString()));
  }

  /** Immediately restores all flags modified by this rule to their state before modification. */
  private void restoreFlags() {
    for (FlagBackup backup : backups.values()) {
      backup.restore();
    }
    backups.clear();
  }

  private class SetFlagsStatement extends Statement {

    private final Statement base;

    private SetFlagsStatement(Statement base) {
      this.base = base;
    }

    @Override
    public void evaluate() throws Throwable {
      try {
        base.evaluate();
      } finally {
        restoreFlags();
      }
    }
  }

  private record FlagBackup(
      Flag<?> flag, @Nullable Object originalValue, boolean originalSetFromString) {

    void restore() {
      if (!originalSetFromString) {
        flag.resetForTest();
      } else {
        @SuppressWarnings("unchecked") // Type check is in saveBackup().
        Flag<Object> flag = (Flag<Object>) this.flag;
        flag.setValue(originalValue);
      }
    }
  }
}
