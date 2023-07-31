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

package com.google.devtools.deviceaction.common.schemas;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Comparator.naturalOrder;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Comparators;
import com.google.common.collect.ComparisonChain;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Semantic version for device action.
 *
 * <p>The format of the version is "<major>.<minor>.<patch>[-<label>]", e.g. "1.1.0" or
 * "1.1.0-alpha". For details, see <a
 * href="https://semver.org/#semantic-versioning-200">semantic-versioning-200</a>.
 */
@Immutable
@AutoValue
@AutoValue.CopyAnnotations
public abstract class Version implements Comparable<Version> {
  private static final Pattern VERSION_REGEXP =
      Pattern.compile(
          "^"
              + "(?<major>\\d+?)\\."
              + "(?<minor>\\d+?)\\."
              + "(?<patch>\\d+?)"
              + "(-(?<label>.+))?"
              + "$");

  /** MAJOR version when you make incompatible API changes. */
  abstract int major();

  /** MINOR version when you add functionality in a backward compatible manner. */
  abstract int minor();

  /** PATCH version when you make backward compatible bug fixes. */
  abstract int patch();

  /**
   * Additional labels for pre-release and build metadata are available as extensions to the
   * <b><major>.<minor>.<patch></b> format.
   */
  abstract Optional<String> label();

  /** Full version of <b><major>.<minor>.<patch>[-<label>]</b> format. */
  public final String fullVersion() {
    String version = String.format("%d.%d.%d", major(), minor(), patch());
    return label().map(l -> String.format("%s-%s", version, l)).orElse(version);
  }

  @Override
  public final String toString() {
    return fullVersion();
  }

  /**
   * Similar to the <a href="https://semver.org/#spec-item-11">precedence</a>, but we use the
   * natural order of label for simplicity. Therefore, we have unconventional "1.0.0-beta.11" <
   * "1.0.0-beta.2". But we won't have such complicated labels and this is OK.
   */
  @Override
  public int compareTo(Version otherVersion) {
    return ComparisonChain.start()
        .compare(major(), otherVersion.major())
        .compare(minor(), otherVersion.minor())
        .compare(patch(), otherVersion.patch())
        .compare(label(), otherVersion.label(), Comparators.emptiesLast(naturalOrder()))
        .result();
  }

  public static Version of(String version) {
    Matcher matcher = VERSION_REGEXP.matcher(version);
    checkArgument(
        matcher.matches(),
        "Version '%s' doesn't match the format '<major>.<minor>.<patch>[-<label>]'.",
        version);
    return Version.builder()
        .setMajor(Integer.parseInt(matcher.group("major")))
        .setMinor(Integer.parseInt(matcher.group("minor")))
        .setPatch(Integer.parseInt(matcher.group("patch")))
        .setLabel(matcher.group("label"))
        .build();
  }

  private static Builder builder() {
    return new AutoValue_Version.Builder();
  }

  /** Builder for {@link Version}. */
  @AutoValue.Builder
  abstract static class Builder {

    abstract Builder setMajor(int value);

    abstract Builder setMinor(int value);

    abstract Builder setPatch(int value);

    abstract Builder setLabel(@Nullable String label);

    abstract Version build();
  }
}
