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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/** Wrapper for arguments used for waiting device to satisfy given condition. */
@AutoValue
public abstract class WaitArgs {

  /**
   * Provider for the current value of "now". Defaults to use {@link java.time.SystemClock.UTC} if
   * not set.
   */
  public abstract Clock clock();

  /**
   * An object which accepts requests to put the current thread to sleep as specified. Defaults to
   * use {@link com.google.common.time.DefaultSleeper} if not set.
   */
  public abstract Sleeper sleeper();

  /** Interval of checking whether the Android device/emulator is ready. */
  public abstract Duration checkReadyInterval();

  /** Timeout to wait. */
  public abstract Duration checkReadyTimeout();

  public static Builder builder() {
    return new AutoValue_WaitArgs.Builder();
  }

  /** Auto value builder for {@link WaitArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setClock(Clock clock);

    public abstract Builder setSleeper(Sleeper sleeper);

    public abstract Builder setCheckReadyInterval(Duration checkReadyInterval);

    public abstract Builder setCheckReadyTimeout(Duration checkReadyTimeout);

    abstract Optional<Clock> clock();

    abstract Optional<Sleeper> sleeper();

    abstract WaitArgs autoBuild();

    public WaitArgs build() {
      if (clock().isEmpty()) {
        setClock(Clock.systemUTC());
      }
      if (sleeper().isEmpty()) {
        setSleeper(Sleeper.defaultSleeper());
      }
      return autoBuild();
    }
  }
}
