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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StressTestProgram {

  /**
   * Runs N subprocesses ("sleep Xs") concurrently.
   *
   * <p>N = args[0], X = args[1].
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    int subprocessNum = Integer.parseInt(args[0]);
    String sleepTimeArgument = args[1];

    List<Process> processes = new ArrayList<>();
    for (int i = 0; i < subprocessNum; i++) {
      processes.add(new ProcessBuilder("sleep", sleepTimeArgument).start());
    }
    for (Process process : processes) {
      process.waitFor();
    }
  }

  private StressTestProgram() {}
}
