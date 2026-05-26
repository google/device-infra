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

package com.google.devtools.mobileharness.fe.v6.service.host.provider;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NoOpHostLatestVersionProviderImplTest {

  private static final String HOST_NAME = "test-host";
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  private HostLatestVersionProvider provider;

  @Before
  public void setUp() {
    provider = new NoOpHostLatestVersionProviderImpl();
  }

  @Test
  public void getLatestVersion_returnsEmpty() throws Exception {
    ListenableFuture<Optional<String>> future = provider.getLatestVersion(HOST_NAME, UNIVERSE);

    assertThat(future.get()).isEmpty();
  }
}
