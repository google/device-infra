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

package com.google.devtools.mobileharness.platform.android.lightning.bundletool;

import static com.google.common.base.StandardSystemProperty.JAVA_IO_TMPDIR;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/** JVM properties used when executing Bundletool. */
public record JavaSystemProperties(ImmutableList<String> jvmFlags) {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link JavaSystemProperties}. */
  public static final class Builder {
    private Optional<Path> javaTmpDir = Optional.empty();

    @CanIgnoreReturnValue
    public Builder setJavaTmpDir(Path javaTmpDir) {
      this.javaTmpDir = Optional.of(javaTmpDir);
      return this;
    }

    /** Resolves the JVM flags applying the 3-tier fallback exactly once. */
    public JavaSystemProperties build() {
      // Tier 1: Use the javaTmpDir if explicitly provided
      String tmpDir =
          javaTmpDir
              .map(Path::toString)
              .orElseGet(
                  () -> {
                    // Tier 2: fall back to the lab server's persistent NVMe temp dir
                    String labTmpDir = Flags.tmpDirRoot.get();
                    if (labTmpDir != null && !labTmpDir.isEmpty()) {
                      File labTmpDirFile = new File(labTmpDir);
                      if (labTmpDirFile.exists() && labTmpDirFile.isDirectory()) {
                        logger.atInfo().log("Bundletool falls back to lab temp dir %s", labTmpDir);
                        return labTmpDir;
                      } else {
                        logger.atInfo().log(
                            "Lab temp dir %s does not exist or is not a directory", labTmpDir);
                      }
                    }
                    // Tier 3: last resort fallback (e.g., for local workstation execution)
                    logger.atInfo().log(
                        "Bundletool falls back to system temp dir %s", JAVA_IO_TMPDIR.value());
                    return JAVA_IO_TMPDIR.value();
                  });
      ImmutableList<String> jvmFlags =
          ImmutableList.of(String.format("-Djava.io.tmpdir=%s", tmpDir));
      logger.atInfo().log("Bundletool is going to use temp dir %s", tmpDir);
      return new JavaSystemProperties(jvmFlags);
    }
  }
}
