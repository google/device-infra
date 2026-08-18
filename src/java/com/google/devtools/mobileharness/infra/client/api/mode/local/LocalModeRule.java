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

package com.google.devtools.mobileharness.infra.client.api.mode.local;

import com.google.devtools.mobileharness.infra.client.api.mode.ExecMode;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import org.junit.rules.ExternalResource;

/**
 * JUnit rule for creating and managing an isolated {@link LocalMode} environment in tests.
 *
 * <p>Each test case using this rule receives a dedicated {@link LocalMode} environment with its own
 * device manager, local scheduler, and thread pools. When the test finishes, {@link #after()}
 * automatically shuts down the thread pools and schedulers to avoid resource leaks and state
 * contamination across tests.
 *
 * <p><b>Example (Non-Guice test):</b>
 *
 * <pre>{@code
 * public class MyTest {
 *   @Rule public final LocalModeRule localModeRule = new LocalModeRule();
 *
 *   @Test
 *   public void testSomething() {
 *     clientApi.startJob(jobInfo, localModeRule.getLocalMode());
 *   }
 * }
 * }</pre>
 *
 * <p><b>Example (Guice test):</b>
 *
 * <pre>{@code
 * public class MyGuiceTest {
 *   @Rule public final LocalModeRule localModeRule = new LocalModeRule();
 *
 *   @Before
 *   public void setUp() {
 *     Guice.createInjector(..., localModeRule.getModule(), ...).injectMembers(this);
 *   }
 * }
 * }</pre>
 */
public class LocalModeRule extends ExternalResource {

  private final LocalModeEnvironment env = LocalModeEnvironment.createForTest();
  private final LocalMode localMode = new LocalMode(env);
  private final Module module = new LocalModeTestModule();

  /** Gets the pre-created {@link LocalMode} instance for this test. */
  public LocalMode getLocalMode() {
    return localMode;
  }

  /**
   * Gets a Guice module binding the test's {@link LocalModeEnvironment}, {@link LocalMode}, and
   * {@link ExecMode}.
   */
  public Module getModule() {
    return module;
  }

  @Override
  protected void after() {
    env.tearDownForTest();
  }

  private class LocalModeTestModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(LocalModeEnvironment.class).toInstance(env);
      bind(LocalMode.class).toInstance(localMode);
      bind(ExecMode.class).to(LocalMode.class);
    }
  }
}
