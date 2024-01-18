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

package com.google.devtools.mobileharness.platform.android.xts.suite;

/** Representation of device foldable state as returned by "cmd device_state print-states". */
public class DeviceFoldableState implements Comparable<DeviceFoldableState> {

  private final long identifier;
  private final String name;

  public DeviceFoldableState(long identifier, String name) {
    this.identifier = identifier;
    this.name = name;
  }

  public long getIdentifier() {
    return identifier;
  }

  @Override
  public String toString() {
    return "foldable:" + identifier + ":" + name;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (int) (identifier ^ (identifier >>> 32));
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DeviceFoldableState)) {
      return false;
    }
    DeviceFoldableState other = (DeviceFoldableState) obj;
    if (identifier != other.identifier) {
      return false;
    }
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    return true;
  }

  @Override
  public int compareTo(DeviceFoldableState o) {
    return Long.compare(this.identifier, o.identifier);
  }
}
