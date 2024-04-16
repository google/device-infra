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

package com.google.devtools.mobileharness.infra.ats.console.command.completer;

import static com.google.common.base.Ascii.equalsIgnoreCase;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.catching;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import java.util.List;
import java.util.logging.Level;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.ParsedLine;

/** {@link Completer} of ATS console. */
@Singleton
public class CommandCompleter {

  private final ListeningExecutorService threadPool;

  private final SettableFuture<ImmutableList<Candidate>> getTestPlansFuture =
      SettableFuture.create();

  @Inject
  CommandCompleter(ListeningExecutorService threadPool) {
    this.threadPool = threadPool;
  }

  public void startGettingTestPlans() {
    getTestPlansFuture.setFuture(
        catching(
            logFailure(
                threadPool.submit(threadRenaming(this::getTestPlans, () -> "test-plans-fetcher")),
                Level.WARNING,
                "Fatal error when getting test plans"),
            Throwable.class,
            error -> ImmutableList.of(),
            directExecutor()));
  }

  ImmutableList<Candidate> complete(ParsedLine line) {
    List<String> words = line.words();

    if (words.size() == 1) {
      // Inputting the first word or no input (there will be one empty string):
      return ImmutableList.of(new Candidate("run"));
    } else if (words.size() > 1
        && equalsIgnoreCase(words.get(0), "run")
        && getTestPlansFuture.isDone()) {
      // Inputting the following words of "run":
      return getUnchecked(getTestPlansFuture);
    }
    return ImmutableList.of();
  }

  private ImmutableList<Candidate> getTestPlans() {
    // TODO: Loads full test plan list.
    return ImmutableList.of("cts", "cts-camera", "empty").stream()
        .map(Candidate::new)
        .collect(toImmutableList());
  }
}
