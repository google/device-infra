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

package com.google.devtools.mobileharness.shared.util.comm.dualconduit.proxy;

import com.google.auto.value.AutoValue;

/** Configuration for establishing a DualConduit proxy session. */
@AutoValue
public abstract class DualConduitProxyConfig {

  public abstract String dconInstanceId();

  public abstract String dconHostname();

  public abstract int dconReverseConduitCount();

  public static Builder builder() {
    return new AutoValue_DualConduitProxyConfig.Builder()
        .setDconHostname("localhost")
        .setDconReverseConduitCount(1);
  }

  public static DualConduitProxyConfig of(
      String dconInstanceId, String dconHostname, int dconReverseConduitCount) {
    return builder()
        .setDconInstanceId(dconInstanceId)
        .setDconHostname(dconHostname)
        .setDconReverseConduitCount(dconReverseConduitCount)
        .build();
  }

  public static DualConduitProxyConfig of(String dconInstanceId, String dconHostname) {
    return builder().setDconInstanceId(dconInstanceId).setDconHostname(dconHostname).build();
  }

  /** Builder for {@link DualConduitProxyConfig}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setDconInstanceId(String dconInstanceId);

    public abstract Builder setDconHostname(String dconHostname);

    public abstract Builder setDconReverseConduitCount(int dconReverseConduitCount);

    public abstract DualConduitProxyConfig build();
  }
}
