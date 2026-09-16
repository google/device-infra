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

package com.google.devtools.common.metrics.stability.model;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MetadataProviderTest {

  private static class FakeMetadataProvider implements MetadataProvider {
    private final Map<String, String> metadata = new HashMap<>();

    @Override
    public void addMetadata(String key, String value) {
      metadata.put(key, value);
    }

    @Override
    public ImmutableMap<String, String> getMetadata() {
      return ImmutableMap.copyOf(metadata);
    }
  }

  @Test
  public void addMetadata_singleEntry() {
    FakeMetadataProvider provider = new FakeMetadataProvider();
    provider.addMetadata("key1", "value1");

    assertThat(provider.getMetadata()).containsExactly("key1", "value1");
    assertThat(provider.getMetadata("key1")).hasValue("value1");
    assertThat(provider.getMetadata("key2")).isEmpty();
  }

  @Test
  public void addMetadata_map() {
    FakeMetadataProvider provider = new FakeMetadataProvider();
    provider.addMetadata(ImmutableMap.of("key1", "value1", "key2", "value2"));

    assertThat(provider.getMetadata()).containsExactly("key1", "value1", "key2", "value2");
    assertThat(provider.getMetadata("key1")).hasValue("value1");
    assertThat(provider.getMetadata("key2")).hasValue("value2");
    assertThat(provider.getMetadata("key3")).isEmpty();
  }

  @Test
  public void getMetadata_emptyByDefault() {
    FakeMetadataProvider provider = new FakeMetadataProvider();

    assertThat(provider.getMetadata()).isEmpty();
    assertThat(provider.getMetadata("anyKey")).isEmpty();
  }
}
