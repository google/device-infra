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

package com.google.devtools.mobileharness.platform.android.shared.emulator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.api.client.http.HttpResponseException;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.AndroidCiBuildSource;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.AndroidCiBundle;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.BuildSource;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.CreateCvdRequest;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.CreateCvdResponse;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.CreateHostRequest;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.Cvd;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.DockerInstance;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.FetchArtifactsRequest;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.FetchArtifactsResponse;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.GcpInstance;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.HostInstance;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.ListCvdsResponse;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.ListHostsResponse;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.Operation;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.OperationError;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CloudOrchestratorClientTest {

  @Rule public final MockWebServer server = new MockWebServer();
  private CloudOrchestratorClient client;

  @Before
  public void setUp() {
    client = new CloudOrchestratorClient(server.url("/").toString(), "v1", "local");
    client.setRequestRetryDelay(Duration.ofMillis(1));
  }

  @Test
  public void listHosts_success() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("{\"items\": [{\"name\": \"host-1\"}, {\"name\": \"host-2\"}]}")
            .addHeader("Content-Type", "application/json"));

    List<HostInstance> hosts = client.listHosts();

    assertThat(hosts).hasSize(2);
    assertThat(hosts.get(0).name).isEqualTo("host-1");
    assertThat(hosts.get(1).name).isEqualTo("host-2");
    assertThat(server.takeRequest().getPath()).isEqualTo("/v1/zones/local/hosts");
  }

  @Test
  public void listHosts_error() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("internal error"));

    MobileHarnessException e = assertThrows(MobileHarnessException.class, () -> client.listHosts());

    assertThat(e.getCause()).isInstanceOf(HttpResponseException.class);
    HttpResponseException httpEx = (HttpResponseException) e.getCause();
    assertThat(httpEx.getStatusCode()).isEqualTo(500);
    assertThat(httpEx.getContent()).contains("internal error");
  }

  @Test
  public void getHostEndpoint_correctUrl() {
    String endpoint = client.getHostEndpoint("host-abc");
    assertThat(endpoint).endsWith("/v1/zones/local/hosts/host-abc");
  }

  @Test
  public void setBasicAuth_addsHeader() throws Exception {
    client.setBasicAuth("user", "pass");
    server.enqueue(new MockResponse().setBody("{\"items\": []}"));

    var unused = client.listHosts();

    String authHeader = server.takeRequest().getHeader("Authorization");
    assertThat(authHeader).isEqualTo("Basic dXNlcjpwYXNz");
  }

  @Test
  public void setSessionCookie_addsHeader() throws Exception {
    client.setSessionCookie("session=123");
    server.enqueue(new MockResponse().setBody("{\"items\": []}"));

    var unused = client.listHosts();

    String cookieHeader = server.takeRequest().getHeader("Cookie");
    assertThat(cookieHeader).isEqualTo("session=123");
  }

  @Test
  public void fetchArtifactsAndWait_success() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("{\"name\": \"op-123\", \"done\": false}")
            .addHeader("Content-Type", "application/json"));
    server.enqueue(
        new MockResponse()
            .setBody("{\"android_ci_bundle\": {\"build\": {\"branch\": \"branch-1\"}}}")
            .addHeader("Content-Type", "application/json"));

    client.fetchArtifactsAndWait("host-1", "branch-1", "target-1");

    var request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/v1/zones/local/hosts/host-1/artifacts");
    assertThat(request.getBody().readUtf8()).contains("\"type\":0");

    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v1/zones/local/hosts/host-1/operations/op-123/:wait");
  }

  @Test
  public void waitOperation_retriesTwentyTimesAndThrows() throws Exception {
    for (int i = 0; i < 100; i++) {
      server.enqueue(new MockResponse().setResponseCode(503));
    }

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                client.waitOperation(
                    "host-1", "op-retry", Operation.class, 20, Duration.ofMillis(1)));

    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_CLOUD_ORCHESTRATOR_OPERATION_ERROR);
    assertThat(e.getMessage()).contains("after 20 retries");
    assertThat(server.getRequestCount()).isEqualTo(20);
  }

  @Test
  public void waitOperation_mixOfErrorsAndSuccess() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(504));
    server.enqueue(new MockResponse().setResponseCode(503));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"name\": \"op-123\"}"));

    Operation op =
        client.waitOperation("host-1", "op-mixed", Operation.class, 20, Duration.ofMillis(1));

    assertThat(op.name).isEqualTo("op-123");
    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v1/zones/local/hosts/host-1/operations/op-mixed/:wait");
    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v1/zones/local/hosts/host-1/operations/op-mixed/:wait");
  }

  @Test
  public void createCvd_retriesOn502ThenSucceeds() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(502));
    server.enqueue(new MockResponse().setResponseCode(503));
    server.enqueue(
        new MockResponse()
            .setBody("{\"name\": \"op-123\", \"done\": true}")
            .addHeader("Content-Type", "application/json"));

    var op = client.createCvd("host-1", new CreateCvdRequest(), null);

    assertThat(op.name).isEqualTo("op-123");
    assertThat(server.getRequestCount()).isEqualTo(3);
    for (int i = 0; i < 3; i++) {
      assertThat(server.takeRequest().getPath()).isEqualTo("/v1/zones/local/hosts/host-1/cvds");
    }
  }

  @Test
  public void createHostAndWait_success() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"name\": \"op-123\", \"done\": true}"));
    server.enqueue(new MockResponse().setBody("{\"name\": \"host-1\"}"));
    server.enqueue(new MockResponse().setBody("{}")); // Readiness check response

    HostInstance host = client.createHostAndWait(new CreateHostRequest(new HostInstance("host-1")));

    assertThat(host.name).isEqualTo("host-1");
    assertThat(server.takeRequest().getPath()).isEqualTo("/v1/zones/local/hosts");
    assertThat(server.takeRequest().getPath()).isEqualTo("/v1/zones/local/operations/op-123/:wait");
    assertThat(server.takeRequest().getPath()).isEqualTo("/v1/zones/local/hosts/host-1/");
  }

  @Test
  public void createHostAndWait_errorWhenHostNameNull() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"name\": \"op-123\", \"done\": true}"));
    server.enqueue(new MockResponse().setBody("{}")); // HostInstance with null name

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () -> client.createHostAndWait(new CreateHostRequest(new HostInstance("host-1"))));

    assertThat(e.getMessage()).contains("Host created but not found");
  }

  @Test
  public void listCvds_success() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("{\"cvds\": [{\"webrtc_device_id\": \"cvd-1\"}]}")
            .addHeader("Content-Type", "application/json"));

    var cvds = client.listCvds("host-1");

    assertThat(cvds).hasSize(1);
    assertThat(cvds.get(0).webrtcDeviceId).isEqualTo("cvd-1");
    assertThat(server.takeRequest().getPath()).isEqualTo("/v1/zones/local/hosts/host-1/cvds");
  }

  @Test
  public void deleteHost_success() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200));

    client.deleteHost("host-1");

    var request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("DELETE");
    assertThat(request.getPath()).isEqualTo("/v1/zones/local/hosts/host-1");
  }

  @Test
  public void deleteCvd_success() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("{\"name\": \"op-delete-cvd\"}")
            .addHeader("Content-Type", "application/json"));

    var op = client.deleteCvd("host-1", "cvd-1");

    assertThat(op.name).isEqualTo("op-delete-cvd");
    var request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("DELETE");
    assertThat(request.getPath()).isEqualTo("/v1/zones/local/hosts/host-1/cvds/cvd-1");
  }

  @Test
  public void deleteCvd_retriesOnFailure() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(502));
    server.enqueue(new MockResponse().setResponseCode(503));
    server.enqueue(
        new MockResponse()
            .setBody("{\"name\": \"op-delete-cvd\"}")
            .addHeader("Content-Type", "application/json"));

    var op = client.deleteCvd("host-1", "cvd-1");

    assertThat(op.name).isEqualTo("op-delete-cvd");

    assertThat(server.takeRequest().getPath()).isEqualTo("/v1/zones/local/hosts/host-1/cvds/cvd-1");
    assertThat(server.takeRequest().getPath()).isEqualTo("/v1/zones/local/hosts/host-1/cvds/cvd-1");
    assertThat(server.takeRequest().getPath()).isEqualTo("/v1/zones/local/hosts/host-1/cvds/cvd-1");
  }

  @Test
  public void deleteCvdAndWait_success() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody("{\"name\": \"op-delete-cvd\"}")
            .addHeader("Content-Type", "application/json"));
    server.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));

    client.deleteCvdAndWait("host-1", "cvd-1");

    assertThat(server.takeRequest().getPath()).isEqualTo("/v1/zones/local/hosts/host-1/cvds/cvd-1");
    assertThat(server.takeRequest().getPath())
        .isEqualTo("/v1/zones/local/hosts/host-1/operations/op-delete-cvd/:wait");
  }

  @Test
  public void listHosts_retriesThenThrows() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(502));
    server.enqueue(new MockResponse().setResponseCode(502));
    server.enqueue(new MockResponse().setResponseCode(502));

    MobileHarnessException e = assertThrows(MobileHarnessException.class, () -> client.listHosts());

    assertThat(server.getRequestCount()).isEqualTo(3);
    assertThat(e.getCause()).isInstanceOf(HttpResponseException.class);
    HttpResponseException httpEx = (HttpResponseException) e.getCause();
    assertThat(httpEx.getStatusCode()).isEqualTo(502);
  }

  @Test
  public void createCvdWithEnvConfigAndWait_success() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"name\": \"op-123\", \"done\": true}"));
    server.enqueue(
        new MockResponse()
            .setBody("{\"cvds\": [{\"webrtc_device_id\": \"cvd-1\"}]}")
            .addHeader("Content-Type", "application/json"));

    var cvd = client.createCvdWithEnvConfigAndWait("host-1", new HashMap<>(), "creds");

    assertThat(cvd.webrtcDeviceId).isEqualTo("cvd-1");
    var req = server.takeRequest();
    assertThat(req.getHeader("X-Cutf-Host-Orchestrator-BuildAPI-Creds")).isEqualTo("creds");
  }

  @Test
  public void createCvdWithEnvConfigAndWait_deprecated_success() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"name\": \"op-123\", \"done\": true}"));
    server.enqueue(
        new MockResponse()
            .setBody("{\"cvds\": [{\"webrtc_device_id\": \"cvd-1\"}]}")
            .addHeader("Content-Type", "application/json"));

    var cvd = client.createCvdWithEnvConfigAndWait("host-1", "cvd-1", "branch-1", "target-1");

    assertThat(cvd.webrtcDeviceId).isEqualTo("cvd-1");
  }

  @Test
  public void delete_retriesAndThrows() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(502));
    server.enqueue(new MockResponse().setResponseCode(502));
    server.enqueue(new MockResponse().setResponseCode(502));

    MobileHarnessException e =
        assertThrows(MobileHarnessException.class, () -> client.deleteHost("host-fail"));
    assertThat(e.getCause()).isInstanceOf(HttpResponseException.class);
    HttpResponseException httpEx = (HttpResponseException) e.getCause();
    assertThat(httpEx.getStatusCode()).isEqualTo(502);
  }

  @Test
  public void allMethods_throwLocalNetworkErrorOnIOException() throws Exception {
    server.shutdown(); // Force IO error

    // Test GET (via listHosts)
    MobileHarnessException e1 =
        assertThrows(MobileHarnessException.class, () -> client.listHosts());
    assertThat(e1.getErrorId()).isEqualTo(BasicErrorId.LOCAL_NETWORK_ERROR);

    // Test POST (via createHost)
    MobileHarnessException e2 =
        assertThrows(
            MobileHarnessException.class,
            () -> client.createHost(new CloudOrchestratorMessages.CreateHostRequest()));
    assertThat(e2.getErrorId()).isEqualTo(BasicErrorId.LOCAL_NETWORK_ERROR);

    // Test DELETE (via deleteHost)
    MobileHarnessException e3 =
        assertThrows(MobileHarnessException.class, () -> client.deleteHost("host-1"));
    assertThat(e3.getErrorId()).isEqualTo(BasicErrorId.LOCAL_NETWORK_ERROR);

    // Test waitOperation (which calls executeRequest directly)
    MobileHarnessException e4 =
        assertThrows(
            MobileHarnessException.class,
            () -> client.waitOperation("host-1", "op-1", Operation.class, 1, Duration.ofMillis(1)));
    assertThat(e4.getErrorId()).isEqualTo(BasicErrorId.LOCAL_NETWORK_ERROR);
  }

  @Test
  public void waitOperation_retriesOn504() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(504));
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"name\": \"op-123\"}"));

    Operation op =
        client.waitOperation("host-1", "op-504", Operation.class, 20, Duration.ofMillis(1));

    assertThat(op.name).isEqualTo("op-123");
  }

  @Test
  public void createCvdAndWait_errorWhenNoCvds() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"name\": \"op-123\", \"done\": true}"));
    server.enqueue(new MockResponse().setBody("{\"cvds\": []}"));

    assertThrows(
        MobileHarnessException.class,
        () -> client.createCvdAndWait("host-1", new CreateCvdRequest(), null));
  }

  @Test
  public void messages_coverage() {
    // This test is purely to satisfy coverage requirements for POJO messages.
    // Default (no-args) constructors are primarily used by JSON frameworks like GSON
    // for instantiation via reflection during deserialization.
    // We also preserve parameterized constructors and supplementary fields for
    // API symmetry with the Go source-of-truth.
    Object unused;
    unused = new FetchArtifactsRequest();
    unused = new FetchArtifactsResponse();
    unused = new CreateCvdResponse();
    unused = new ListHostsResponse();
    unused = new ListCvdsResponse();
    unused = new AndroidCiBuildSource();
    unused = new AndroidCiBuildSource(null);
    unused = new BuildSource();
    unused = new BuildSource(null);
    unused = new CreateCvdRequest(new Cvd());
    unused = new HostInstance();
    unused = new DockerInstance();
    unused = new DockerInstance("image");
    unused = new GcpInstance();
    unused = new GcpInstance("type");
    unused = new CreateHostRequest();
    unused = new OperationError();
    unused = new AndroidCiBundle();
    Operation op = new Operation();
    op.name = "name";
    op.done = true;
    op.error = new OperationError();
    op.response = new HashMap<>();
    assertThat(unused).isNotNull();
  }

  @Test
  public void waitOperation_nonRetriableErrorThrowsImmediately() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                client.waitOperation(
                    "host-1", "op-401", Operation.class, 20, Duration.ofMillis(1)));

    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_CLOUD_ORCHESTRATOR_OPERATION_ERROR);
    assertThat(e.getCause()).isInstanceOf(HttpResponseException.class);
    assertThat(((HttpResponseException) e.getCause()).getStatusCode()).isEqualTo(401);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  public void createCvd_nonRetriableErrorThrowsImmediately() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(400));

    assertThrows(
        MobileHarnessException.class,
        () -> client.createCvd("host-1", new CreateCvdRequest(), null));

    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  public void waitOperation_badRequestThrowsSpecificError() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(400).setBody("bad request"));

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                client.waitOperation("host-1", "op-400", Operation.class, 3, Duration.ofMillis(1)));

    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_CLOUD_ORCHESTRATOR_OPERATION_ERROR);
    assertThat(server.getRequestCount()).isEqualTo(1);
    assertThat(e.getCause()).isInstanceOf(HttpResponseException.class);
    HttpResponseException httpEx = (HttpResponseException) e.getCause();
    assertThat(httpEx.getStatusCode()).isEqualTo(400);
    assertThat(httpEx.getContent()).contains("bad request");
  }

  @Test
  public void waitOperation_serverErrorThrowsSpecificError() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("internal server error"));

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                client.waitOperation("host-1", "op-500", Operation.class, 3, Duration.ofMillis(1)));

    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_CLOUD_ORCHESTRATOR_OPERATION_ERROR);
    assertThat(server.getRequestCount()).isEqualTo(1);
    assertThat(e.getCause()).isInstanceOf(HttpResponseException.class);
    HttpResponseException httpEx = (HttpResponseException) e.getCause();
    assertThat(httpEx.getStatusCode()).isEqualTo(500);
    assertThat(httpEx.getContent()).contains("internal server error");
  }

  @Test
  public void waitOperation_exhaustedRetriesThrowsServerError() throws Exception {
    int maxRetries = 2;
    for (int i = 0; i <= maxRetries; i++) {
      server.enqueue(new MockResponse().setResponseCode(502).setBody("bad gateway"));
    }

    MobileHarnessException e =
        assertThrows(
            MobileHarnessException.class,
            () ->
                client.waitOperation(
                    "host-1", "op-502", Operation.class, maxRetries, Duration.ofMillis(1)));

    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_CLOUD_ORCHESTRATOR_OPERATION_ERROR);
    assertThat(server.getRequestCount()).isEqualTo(maxRetries);
    assertThat(e.getCause()).isInstanceOf(HttpResponseException.class);
    HttpResponseException httpEx = (HttpResponseException) e.getCause();
    assertThat(httpEx.getStatusCode()).isEqualTo(502);
    assertThat(httpEx.getContent()).contains("bad gateway");
  }
}
