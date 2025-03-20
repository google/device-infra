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

package com.google.devtools.mobileharness.shared.util.concurrent;

import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.util.Providers;
import java.util.concurrent.Executor;
import junit.framework.TestCase;

/** Tests for {@link ServiceModule}. */
public class ServiceModuleTest extends TestCase {
  private static final class TestExecutor implements Executor {
    Runnable command;

    @Override
    public void execute(Runnable command) {
      this.command = command;
      command.run();
    }
  }

  private static final class Listener extends ServiceManager.Listener {
    int healthyCalled;

    @Override
    public void healthy() {
      ++healthyCalled;
    }
  }

  private final Listener listener = new Listener();
  private final TestExecutor executor = new TestExecutor();

  public void testMultipleServiceModulesDoesntThrowDuplicateBindingError() {
    Guice.createInjector(new ServiceModule(), new ServiceModule())
        .getInstance(Key.get(ServiceManager.class, ExternalServiceManager.class));
  }

  public void testServiceManagerListener() {
    ServiceManager manager =
        Guice.createInjector(
                new ServiceModule() {
                  @Override
                  protected void configureServices() {
                    bindServiceManagerListener().toInstance(listener);
                  }
                })
            .getInstance(Key.get(ServiceManager.class, ExternalServiceManager.class));
    manager.startAsync().awaitHealthy();
    assertEquals(1, listener.healthyCalled);
  }

  public void testServiceManagerListener_withCustomExecutor() {
    ServiceManager manager =
        Guice.createInjector(
                new ServiceModule() {
                  @Override
                  protected void configureServices() {
                    bindServiceManagerListener().withExecutor(executor).toInstance(listener);
                  }
                })
            .getInstance(Key.get(ServiceManager.class, ExternalServiceManager.class));
    manager.startAsync().awaitHealthy();
    assertEquals(1, listener.healthyCalled);
    assertNotNull(executor.command);
  }

  public void testServiceManagerListener_duplicateListeners() {
    ServiceManager manager =
        Guice.createInjector(
                new ServiceModule() {
                  @Override
                  protected void configureServices() {
                    bindServiceManagerListener().toInstance(listener);
                    bindServiceManagerListener().withExecutor(executor).toInstance(listener);
                  }
                })
            .getInstance(Key.get(ServiceManager.class, ExternalServiceManager.class));
    manager.startAsync().awaitHealthy();
    assertEquals(2, listener.healthyCalled);
  }

  public void testServiceManagerListener_multipleModules() {
    ServiceManager manager =
        Guice.createInjector(
                new ServiceModule() {
                  @Override
                  protected void configureServices() {
                    bindServiceManagerListener().toInstance(listener);
                  }
                },
                new ServiceModule() {
                  @Override
                  protected void configureServices() {
                    bindServiceManagerListener().withExecutor(executor).toInstance(listener);
                  }
                })
            .getInstance(Key.get(ServiceManager.class, ExternalServiceManager.class));
    manager.startAsync().awaitHealthy();
    assertEquals(2, listener.healthyCalled);
  }

  public void testServiceManagerListener_explicitBinding() {
    Guice.createInjector(
        new ServiceModule() {
          @Override
          protected void configureServices() {
            binder().requireExplicitBindings();
            bindServiceManagerListener().to(Listener.class);
          }
        });
  }

  public void testServiceManagerListener_explicitKeyBinding() {
    Guice.createInjector(
        new ServiceModule() {
          @Override
          protected void configureServices() {
            binder().requireExplicitBindings();
            bindServiceManagerListener().to(new Key<Listener>() {});
          }
        });
  }

  public void testServiceManagerListener_explicitProviderBinding() {
    Guice.createInjector(
        new ServiceModule() {
          @Override
          protected void configureServices() {
            binder().requireExplicitBindings();
            bindServiceManagerListener().toProvider(Providers.of(listener));
          }
        });
  }

  public void testServiceManagerListener_explicitBindingForExecutor() {
    Guice.createInjector(
        new ServiceModule() {
          @Override
          protected void configureServices() {
            binder().requireExplicitBindings();
            bindServiceManagerListener()
                .withExecutor(new Key<TestExecutor>() {})
                .toInstance(listener);
          }
        });
  }
}
