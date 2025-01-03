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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/** The FileResolver interface. */
@ThreadSafe
public interface FileResolver {

  /** Resolve multiple files synchronously. */
  List<Optional<ResolveResult>> resolve(List<ResolveSource> resolveSources)
      throws MobileHarnessException, InterruptedException;

  /**
   * Resolves one file synchronously.
   *
   * @param resolveSource the resolve source, including file and parameters.
   * @return the resolved files and properties, or Optional.Empty() if the file is not supported by
   *     this resolver.
   * @throws MobileHarnessException if meet exception when resolving the file.
   */
  Optional<ResolveResult> resolve(ResolveSource resolveSource)
      throws MobileHarnessException, InterruptedException;

  /** Resolves one file asynchronously. */
  ListenableFuture<Optional<ResolveResult>> resolveAsync(ResolveSource resolveSource);

  /** Resolves multiple files asynchronously. */
  ListenableFuture<List<Optional<ResolveResult>>> resolveAsync(List<ResolveSource> resolveSources);

  /** Resolved file. */
  @AutoValue
  abstract class ResolvedFile {

    public static ResolvedFile create(String path, @Nullable String checksum) {
      return new AutoValue_FileResolver_ResolvedFile(path, Optional.ofNullable(checksum));
    }

    public abstract String path();

    public abstract Optional<String> checksum();
  }

  /** Result of resolved file. */
  @AutoValue
  abstract class ResolveResult {

    public static ResolveResult of(
        ImmutableList<ResolvedFile> resolvedFiles,
        ImmutableMap<String, String> properties,
        ResolveSource source) {
      return new AutoValue_FileResolver_ResolveResult(resolvedFiles, properties, source);
    }

    public ImmutableList<String> paths() {
      return resolvedFiles().stream().map(ResolvedFile::path).collect(toImmutableList());
    }

    public abstract ImmutableList<ResolvedFile> resolvedFiles();

    public abstract ImmutableMap<String, String> properties();

    public abstract ResolveSource resolveSource();
  }

  /** Source of files and parameters to be resolved. */
  @AutoValue
  abstract class ResolveSource {
    public static ResolveSource create(
        String path, ImmutableMap<String, String> parameters, String targetDir, String tmpDir) {
      return new AutoValue_FileResolver_ResolveSource(path, "", parameters, targetDir, tmpDir);
    }

    public static ResolveSource create(
        String path,
        String tag,
        ImmutableMap<String, String> parameters,
        String targetDir,
        String tmpDir) {
      return new AutoValue_FileResolver_ResolveSource(path, tag, parameters, targetDir, tmpDir);
    }

    public abstract String path();

    public abstract String tag();

    public abstract ImmutableMap<String, String> parameters();

    public abstract String targetDir();

    public abstract String tmpDir();
  }
}
