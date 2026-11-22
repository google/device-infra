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

import static com.google.common.truth.Truth.assertThat;

import com.google.protobuf.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class InMemoryAtsConfigStoreTest {

  private static final ByteString DATA = ByteString.copyFromUtf8("data");

  private final InMemoryAtsConfigStore store = new InMemoryAtsConfigStore();

  @Test
  public void get_entityDoesNotExist_returnsEmpty() {
    assertThat(store.get("kind", "id")).isEmpty();
  }

  @Test
  public void put_createsOrReplacesEntity() {
    store.put("kind", "id", DATA);
    assertThat(store.get("kind", "id")).hasValue(DATA);

    ByteString newData = ByteString.copyFromUtf8("new_data");
    store.put("kind", "id", newData);
    assertThat(store.get("kind", "id")).hasValue(newData);
  }

  @Test
  public void put_keepsEntitiesOfOtherKindsAndIds() {
    store.put("kind", "id", DATA);

    assertThat(store.get("other_kind", "id")).isEmpty();
    assertThat(store.get("kind", "other_id")).isEmpty();
  }

  @Test
  public void delete_removesEntity() {
    store.put("kind", "id", DATA);

    store.delete("kind", "id");

    assertThat(store.get("kind", "id")).isEmpty();
  }

  @Test
  public void delete_entityDoesNotExist_doesNothing() {
    store.delete("kind", "id");

    assertThat(store.get("kind", "id")).isEmpty();
  }
}
