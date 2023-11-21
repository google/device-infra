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

package com.google.devtools.mobileharness.infra.lab;

import com.google.devtools.mobileharness.infra.controller.test.manager.ProxyTestManager;
import com.google.devtools.mobileharness.infra.controller.test.manager.TestManager;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;

/** Base dependency for TestRunModule and UnifiedTestRunServer. */
public final class TestRunBaseModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(new Key<TestManager<?>>() {}).to(ProxyTestManager.class);
    bind(ProxyTestManager.class).in(Singleton.class);
    bind(LocalFileUtil.class).in(Singleton.class);
    bind(SystemUtil.class).in(Singleton.class);
    bind(NetUtil.class).in(Singleton.class);
  }
}
