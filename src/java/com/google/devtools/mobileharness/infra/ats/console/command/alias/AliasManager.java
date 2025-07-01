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

package com.google.devtools.mobileharness.infra.ats.console.command.alias;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Manages command aliases in memory. */
@Singleton
public class AliasManager {

  private final Map<String, String> aliases = new HashMap<>();

  @Inject
  AliasManager() {}

  /** Defines or redefines an alias. */
  public void addAlias(String name, String value) {
    aliases.put(name, value);
  }

  /**
   * Gets the expansion of an alias.
   *
   * @param name the name of the alias
   * @return the expansion of the alias, or {@link Optional#empty()} if the alias is not found
   */
  public Optional<String> getAlias(String name) {
    return Optional.ofNullable(aliases.get(name));
  }

  /**
   * Gets all aliases.
   *
   * @return all aliases
   */
  public ImmutableMap<String, String> getAll() {
    return ImmutableMap.copyOf(aliases);
  }
}
