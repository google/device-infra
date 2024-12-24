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

package com.google.devtools.mobileharness.shared.file.resolver;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.time.Clock;

/** Creates the chain of {@link FileResolver}. */
public class FileResolverBuilder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LocalFileUtil localFileUtil;
  private final Clock clock;
  private final boolean onlyResolveLocalFile;

  private ListeningExecutorService executorService;

  /** The factory to create FileResolverBuilder. */
  public interface Factory {
    FileResolverBuilder create(@Assisted("onlyResolveLocalFile") boolean onlyResolveLocalFile);
  }

  @Inject
  public FileResolverBuilder(
      LocalFileUtil localFileUtil,
      Clock clock,
      @Assisted("onlyResolveLocalFile") boolean onlyResolveLocalFile) {
    this.localFileUtil = localFileUtil;
    this.clock = clock;
    this.onlyResolveLocalFile = onlyResolveLocalFile;
  }

  /**
   * Creates the resolve work chain.
   *
   * @return the head of the chain
   */
  public FileResolver build() {
    return new LocalFileResolver(executorService, localFileUtil);
  }

  @CanIgnoreReturnValue
  public FileResolverBuilder setExecutorService(ListeningExecutorService executorService) {
    this.executorService = executorService;
    return this;
  }
}
