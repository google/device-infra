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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigPusherHelper;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UnlockHostPropertiesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.UpdateError;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class UnlockHostPropertiesHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ConfigurationProvider configurationProvider;
  @Bind @Mock private ConfigPusherHelper configPusherHelper;
  @Bind private ListeningExecutorService executorService = newDirectExecutorService();

  private static final UniverseScope SELF_UNIVERSE = new UniverseScope.SelfUniverse();

  @Inject private UnlockHostPropertiesHandler handler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void unlockHostProperties_hostNotFound_returnsValidationError() throws Exception {
    String hostName = "test_host";
    when(configurationProvider.getLabConfig(hostName, SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.empty())));

    UnlockHostPropertiesRequest request =
        UnlockHostPropertiesRequest.newBuilder().setHostName(hostName).setUniverse("self").build();

    UnlockHostPropertiesResponse response =
        handler.unlockHostProperties(request, SELF_UNIVERSE).get();

    assertThat(response.getSuccess()).isFalse();
    assertThat(response.getError().getCode()).isEqualTo(UpdateError.Code.VALIDATION_ERROR);
    assertThat(response.getError().getMessage()).contains("Host config not found");
  }

  @Test
  public void unlockHostProperties_lockedHost_removesKeysAndSaves() throws Exception {
    String hostName = "test_host";
    LabConfig existingConfig =
        LabConfig.newBuilder()
            .setHostName(hostName)
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder().setKey("some_locked_key").setValue("some_val"))
                    .addHostProperty(
                        HostProperty.newBuilder().setKey("other_key").setValue("other_val"))
                    .build())
            .build();

    when(configurationProvider.getLabConfig(hostName, SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(existingConfig))));
    when(configurationProvider.updateLabConfig(eq(hostName), any(), eq(SELF_UNIVERSE)))
        .thenReturn(immediateVoidFuture());
    when(configPusherHelper.unlockHostProperties(any(LabConfig.Builder.class)))
        .thenAnswer(
            invocation -> {
              LabConfig.Builder builder = invocation.getArgument(0);
              builder.setHostProperties(
                  HostProperties.newBuilder()
                      .addHostProperty(
                          HostProperty.newBuilder().setKey("other_key").setValue("other_val"))
                      .build());
              return true;
            });

    UnlockHostPropertiesRequest request =
        UnlockHostPropertiesRequest.newBuilder().setHostName(hostName).setUniverse("self").build();

    UnlockHostPropertiesResponse response =
        handler.unlockHostProperties(request, SELF_UNIVERSE).get();

    assertThat(response.getSuccess()).isTrue();

    ArgumentCaptor<LabConfig> captor = ArgumentCaptor.forClass(LabConfig.class);
    verify(configurationProvider)
        .updateLabConfig(eq(hostName), captor.capture(), eq(SELF_UNIVERSE));
    LabConfig updatedConfig = captor.getValue();

    // Verify keys are removed (by the mock helper)
    assertThat(updatedConfig.getHostProperties().getHostPropertyList())
        .containsExactly(
            HostProperty.newBuilder().setKey("other_key").setValue("other_val").build());
  }

  @Test
  public void unlockHostProperties_notLockedHost_doesNothing() throws Exception {
    String hostName = "test_host";
    LabConfig existingConfig =
        LabConfig.newBuilder()
            .setHostName(hostName)
            .setHostProperties(
                HostProperties.newBuilder()
                    .addHostProperty(
                        HostProperty.newBuilder().setKey("other_key").setValue("other_val"))
                    .build())
            .build();

    when(configurationProvider.getLabConfig(hostName, SELF_UNIVERSE))
        .thenReturn(immediateFuture(ConfigResult.available(Optional.of(existingConfig))));
    when(configPusherHelper.unlockHostProperties(any(LabConfig.Builder.class))).thenReturn(false);

    UnlockHostPropertiesRequest request =
        UnlockHostPropertiesRequest.newBuilder().setHostName(hostName).setUniverse("self").build();

    UnlockHostPropertiesResponse response =
        handler.unlockHostProperties(request, SELF_UNIVERSE).get();

    assertThat(response.getSuccess()).isTrue();
    verify(configurationProvider, never()).updateLabConfig(anyString(), any(), any());
  }
}
