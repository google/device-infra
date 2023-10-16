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

package com.google.devtools.deviceinfra.shared.subprocess.agent;

import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import java.io.IOException;
import java.time.Duration;

public class FakeProgram {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void main(String[] args) throws IOException, InterruptedException {
    for (String arg : args) {
      ImmutableList<String> command = ImmutableList.of("echo", arg);
      logger.atInfo().log("Starting command: %s", command);

      new ProcessBuilder(command).start().waitFor();

      logger.atInfo().log("Command %s ended", command);

      Sleeper.defaultSleeper().sleep(Duration.ofMillis(500L));
    }
    assertThrows(IOException.class, () -> new ProcessBuilder("wrong_command").start().waitFor());
  }

  private FakeProgram() {}
}
