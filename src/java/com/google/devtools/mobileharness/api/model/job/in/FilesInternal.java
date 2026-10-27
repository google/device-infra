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

package com.google.devtools.mobileharness.api.model.job.in;

import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Gives MH infra access to the internal parts of {@link Files}. Its BUILD target keeps it from
 * anything else.
 */
public final class FilesInternal {

  private FilesInternal() {}

  /** Registers the hook {@link Files#setOnGetTag} describes, or clears it with {@code null}. */
  public static void setOnGetTag(Files files, @Nullable Consumer<String> onGetTag) {
    files.setOnGetTag(onGetTag);
  }
}
