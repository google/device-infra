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

package com.google.devtools.mobileharness.shared.util.comm.server;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EndUserServerInterceptorTest {

  @Test
  public void interceptCall_withHeader_setsEndUserInContext() {
    EndUserServerInterceptor interceptor = new EndUserServerInterceptor();
    Metadata headers = new Metadata();
    headers.put(EndUserServerInterceptor.END_USER_HEADER_KEY, "test_user");

    @SuppressWarnings("unchecked")
    ServerCall<String, String> call = mock(ServerCall.class);
    AtomicReference<String> capturedUser = new AtomicReference<>();

    ServerCallHandler<String, String> next =
        (call1, headers1) -> {
          capturedUser.set(GrpcContexts.endUser().orElse(null));
          return null;
        };

    var unused = interceptor.interceptCall(call, headers, next);

    assertThat(capturedUser.get()).isEqualTo("test_user");
  }

  @Test
  public void interceptCall_withoutHeader_contextEmpty() {
    EndUserServerInterceptor interceptor = new EndUserServerInterceptor();
    Metadata headers = new Metadata();

    @SuppressWarnings("unchecked")
    ServerCall<String, String> call = mock(ServerCall.class);
    AtomicReference<Boolean> isPresent = new AtomicReference<>();

    ServerCallHandler<String, String> next =
        (call1, headers1) -> {
          isPresent.set(GrpcContexts.endUser().isPresent());
          return null;
        };

    var unused = interceptor.interceptCall(call, headers, next);

    assertThat(isPresent.get()).isFalse();
  }
}
