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

package com.google.wireless.qa.mobileharness.shared.log;

import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.base.StackSet;

/**
 * Logs from TestInfo/JobInfo log() in a thread after this scope is constructed and before it is
 * closed (in another word, when {@link #inScope()} returns {@code true}) will have a specific
 * importance.
 */
public class InfoLogImportanceScope implements NonThrowingAutoCloseable {

  private static final ThreadLocal<StackSet<InfoLogImportanceScope>> scopes =
      ThreadLocal.withInitial(StackSet::new);

  public InfoLogImportanceScope() {
    scopes.get().add(this);
  }

  @Override
  public void close() {
    scopes.get().removeUntilLast(this);
  }

  static boolean inScope() {
    return scopes.get().getLast() != null;
  }
}
