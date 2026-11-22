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

package com.google.devtools.mobileharness.infra.ats.server.config.store;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.protobuf.ByteString;
import java.util.Optional;

/**
 * Storage of the configuration entities of ATS server.
 *
 * <p>An entity is identified by its kind and its ID, and its data is opaque to the store.
 */
public interface AtsConfigStore {

  /** Gets the data of an entity, or empty if the entity does not exist. */
  Optional<ByteString> get(String kind, String id) throws MobileHarnessException;

  /** Creates an entity, or replaces its data if it exists. */
  void put(String kind, String id, ByteString data) throws MobileHarnessException;

  /** Deletes an entity if it exists. */
  void delete(String kind, String id) throws MobileHarnessException;
}
