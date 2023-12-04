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

import com.google.common.base.Splitter;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.List;

/** The versions of the Mobile Harness components. This isn't GWT compatible. */
public final class Version implements Comparable<Version> {

  public static final Version CLIENT_VERSION = new Version(4, 30, 4);

  public static final Version MIN_CLIENT_VERSION = new Version(4, 29, 0);

  public static final Version LAB_VERSION = new Version(4, 254, 0);

  public static final Version MIN_LAB_VERSION = new Version(4, 25, 0);

  public static final Version MASTER_V5_VERSION = new Version(5, 28, 1);

  public static final Version MIN_MASTER_V5_VERSION = new Version(5, 0, 1);

  private final int major;
  private final int minor;
  private final int revision;

  @SuppressWarnings("unused")
  private Version() {
    this(-1, -1, -1);
  }

  /** Creates a new version. */
  public Version(int major, int minor, int revision) {
    this.major = major;
    this.minor = minor;
    this.revision = revision;
  }

  /**
   * Creates a version from the given string. The string format should be: x.y.z (x, y, z are
   * non-negative numbers).
   *
   * @throws MobileHarnessException if there is any format error with the given version string
   */
  public Version(String version) throws MobileHarnessException {
    String errMsg =
        "Version string format error, "
            + "expected format: x.y.z (x, y, z are non-negative numbers), got: "
            + version;
    List<String> strs = Splitter.on(".").splitToList(version);
    if (strs.size() != 3) {
      throw new MobileHarnessException(BasicErrorId.VERSION_SEQUENCE_LENGTH_ERROR, errMsg);
    }
    int[] nums = new int[strs.size()];
    for (int i = 0; i < strs.size(); i++) {
      try {
        nums[i] = Integer.parseInt(strs.get(i));
      } catch (NumberFormatException e) {
        throw new MobileHarnessException(BasicErrorId.VERSION_NUM_FORMAT_ERROR, errMsg, e);
      }
      if (nums[i] < 0) {
        throw new MobileHarnessException(BasicErrorId.VERSION_NUM_RANGE_ERROR, errMsg);
      }
    }
    this.major = nums[0];
    this.minor = nums[1];
    this.revision = nums[2];
  }

  @Override
  public int compareTo(Version other) {
    int result = major - other.major;
    if (result != 0) {
      return result;
    }
    result = minor - other.minor;
    if (result != 0) {
      return result;
    }
    return revision - other.revision;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    if (other instanceof Version) {
      return this.compareTo((Version) other) == 0;
    } else {
      return this.toString().equals(other.toString());
    }
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + revision;
  }
}
