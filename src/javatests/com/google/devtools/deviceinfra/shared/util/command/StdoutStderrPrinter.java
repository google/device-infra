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

package com.google.devtools.deviceinfra.shared.util.command;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class StdoutStderrPrinter {

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    CompletableFuture<Void> future1 =
        CompletableFuture.runAsync(
            () -> {
              for (int i = 0; i < 100; i++) {
                System.out.println("OOOOOOOOOOOOOOOOOOOOOOOOOOOOOO");
              }
            });
    CompletableFuture<Void> future2 =
        CompletableFuture.runAsync(
            () -> {
              for (int i = 0; i < 100; i++) {
                System.err.println("EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE");
              }
            });
    future1.get();
    future2.get();
  }

  private StdoutStderrPrinter() {}
}
