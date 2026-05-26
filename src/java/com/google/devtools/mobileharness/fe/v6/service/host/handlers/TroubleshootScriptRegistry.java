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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ.RunTroubleshootScriptRequest.TroubleshootScript;

/** Registry for all troubleshoot scripts, defining their display metadata. */
public final class TroubleshootScriptRegistry {

  /** Metadata defining display name and description for a troubleshooting action. */
  public static final class ScriptMetadata {
    private final String displayName;
    private final String description;

    private ScriptMetadata(String displayName, String description) {
      this.displayName = displayName;
      this.description = description;
    }

    public String displayName() {
      return displayName;
    }

    public String description() {
      return description;
    }
  }

  private static final ImmutableMap<TroubleshootScript, ScriptMetadata> REGISTRY =
      ImmutableMap.<TroubleshootScript, ScriptMetadata>builder()
          .put(
              TroubleshootScript.RESET_USB_HUB,
              new ScriptMetadata(
                  /* displayName= */ "Reset USB Hub",
                  /* description= */ "Power cycle smart USB hub ports to recover missing devices."))
          .buildOrThrow();

  private TroubleshootScriptRegistry() {}

  /** Returns the metadata for a specific troubleshoot script. */
  public static ScriptMetadata getMetadata(TroubleshootScript script) {
    return REGISTRY.get(script);
  }

  /** Returns all registered troubleshoot scripts. */
  public static ImmutableSet<TroubleshootScript> getRegisteredScripts() {
    return REGISTRY.keySet();
  }
}
