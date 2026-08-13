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

package com.google.devtools.mobileharness.shared.version;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version of Fusion Lab server. Format: [prefix]-[date].[counter]-RC[rc] (e.g.,
 * omnilab-lab-server-remoteandroid-20260727.02-RC02)
 */
public final class FusionLabVersion implements Comparable<FusionLabVersion> {
  private final String prefix;
  private final int date;
  private final int counter;
  private final int rc;

  private FusionLabVersion(String prefix, int date, int counter, int rc) {
    this.prefix = prefix;
    this.date = date;
    this.counter = counter;
    this.rc = rc;
  }

  public static Optional<FusionLabVersion> parse(String versionStr) {
    // Regex to match prefix-YYYYMMDD.XX-RCYY
    Matcher matcher = VERSION_STR_PATTERN.matcher(versionStr);
    if (matcher.matches()) {
      String prefix = matcher.group(1);
      int date = Integer.parseInt(matcher.group(2));
      int counter = Integer.parseInt(matcher.group(3));
      int rc = Integer.parseInt(matcher.group(4));
      return Optional.of(new FusionLabVersion(prefix, date, counter, rc));
    }
    return Optional.empty();
  }

  private static final Pattern VERSION_STR_PATTERN =
      Pattern.compile("^(.*)-(\\d{8})\\.(\\d{2})-RC(\\d{2})$");

  @Override
  public int compareTo(FusionLabVersion other) {
    int result = Integer.compare(this.date, other.date);
    if (result != 0) {
      return result;
    }
    result = Integer.compare(this.counter, other.counter);
    if (result != 0) {
      return result;
    }
    return Integer.compare(this.rc, other.rc);
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof FusionLabVersion fusionLabVersion) {
      return this.compareTo(fusionLabVersion) == 0;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return (date * 31 + counter) * 31 + rc;
  }

  @Override
  public String toString() {
    return String.format("%s-%08d.%02d-RC%02d", prefix, date, counter, rc);
  }
}
