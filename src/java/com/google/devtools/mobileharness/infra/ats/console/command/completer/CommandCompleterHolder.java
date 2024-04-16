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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/** Holder of {@link CommandCompleter}. */
public class CommandCompleterHolder implements Completer {

  private static final CommandCompleterHolder INSTANCE = new CommandCompleterHolder();

  public static CommandCompleterHolder getInstance() {
    return INSTANCE;
  }

  private final AtomicReference<CommandCompleter> completer = new AtomicReference<>();

  private CommandCompleterHolder() {}

  public void initialize(CommandCompleter completer) {
    this.completer.compareAndSet(/* expectedValue= */ null, checkNotNull(completer));
  }

  @Override
  public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    CommandCompleter completer = this.completer.get();
    if (completer != null) {
      ImmutableList<Candidate> result = completer.complete(line);
      candidates.addAll(result);
    }
  }
}
