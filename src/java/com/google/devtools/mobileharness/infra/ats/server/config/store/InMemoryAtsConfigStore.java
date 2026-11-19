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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.protobuf.ByteString;
import java.util.Optional;
import javax.inject.Singleton;

/**
 * {@link AtsConfigStore} which keeps the entities in memory, for servers without a database and for
 * tests. The entities are lost when the server exits.
 */
@Singleton
public class InMemoryAtsConfigStore implements AtsConfigStore {

  /** Entity data whose row keys are entity kinds and whose column keys are entity IDs. */
  private final Table<String, String, ByteString> entities =
      Tables.synchronizedTable(HashBasedTable.create());

  @Override
  public Optional<ByteString> get(String kind, String id) {
    return Optional.ofNullable(entities.get(kind, id));
  }

  @Override
  public void put(String kind, String id, ByteString data) {
    entities.put(kind, id, data);
  }

  @Override
  public void delete(String kind, String id) {
    entities.remove(kind, id);
  }
}
