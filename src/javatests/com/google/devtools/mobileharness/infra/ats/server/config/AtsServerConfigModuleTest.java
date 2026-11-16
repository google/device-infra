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

package com.google.devtools.mobileharness.infra.ats.server.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.ats.server.config.store.AtsConfigStore;
import com.google.devtools.mobileharness.infra.ats.server.config.store.InMemoryAtsConfigStore;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.EnableDatabase;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.OlcDatabaseConnections;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.OlcServicesForLocalApi;
import com.google.devtools.mobileharness.shared.util.database.DatabaseConnections;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import io.grpc.BindableService;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AtsServerConfigModuleTest {

  @Test
  public void providesLocalApiServices() {
    Injector injector = createInjector(/* enableDatabase= */ false);

    Set<BindableService> services =
        injector.getInstance(new Key<>(OlcServicesForLocalApi.class) {});

    assertThat(services).hasSize(1);
    assertThat(services.iterator().next()).isInstanceOf(FileCleanerService.class);
  }

  @Test
  public void providesInMemoryStore_databaseDisabled() {
    Injector injector = createInjector(/* enableDatabase= */ false);

    assertThat(injector.getInstance(AtsConfigStore.class))
        .isInstanceOf(InMemoryAtsConfigStore.class);
  }

  @Test
  public void providesJdbcStore_databaseEnabled() {
    Injector injector = createInjector(/* enableDatabase= */ true);

    assertThat(injector.getInstance(AtsConfigStore.class))
        .isNotInstanceOf(InMemoryAtsConfigStore.class);
  }

  /** Creates an injector with the module and the bindings it expects from OLC server. */
  private static Injector createInjector(boolean enableDatabase) {
    return Guice.createInjector(
        new AtsServerConfigModule(),
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(boolean.class).annotatedWith(EnableDatabase.class).toInstance(enableDatabase);
            bind(DatabaseConnections.class)
                .annotatedWith(OlcDatabaseConnections.class)
                .toInstance(new DatabaseConnections());
          }
        });
  }
}
