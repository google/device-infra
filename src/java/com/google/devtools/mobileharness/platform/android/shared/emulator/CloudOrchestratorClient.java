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

import static com.google.common.base.Strings.nullToEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.AndroidCiBuild;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.AndroidCiBundle;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.CreateCvdRequest;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.CreateCvdResponse;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.CreateHostRequest;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.Cvd;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.FetchArtifactsRequest;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.FetchArtifactsResponse;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.HostInstance;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.ListCvdsResponse;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.ListHostsResponse;
import com.google.devtools.mobileharness.platform.android.shared.emulator.CloudOrchestratorMessages.Operation;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryException;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryStrategy;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryingCallable;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Java client for Cloud Orchestrator API. */
public class CloudOrchestratorClient {

  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration WAIT_OPERATION_MAX_TIMEOUT = Duration.ofMinutes(10);
  private static final Duration HOST_READINESS_MAX_TIMEOUT = Duration.ofMinutes(2);
  private static final int WAIT_OPERATION_MAX_RETRIES = 20;
  private static final Duration WAIT_OPERATION_RETRY_DELAY = Duration.ofSeconds(30);

  private final String rootEndpoint;
  private final HttpRequestFactory requestFactory;
  @Nullable private volatile String sessionCookie;
  @Nullable private volatile String basicAuth;
  private volatile Duration requestRetryDelay = Duration.ofSeconds(3);

  public CloudOrchestratorClient(String serviceUrl, String version, String zone) {
    this.rootEndpoint = buildRootEndpoint(serviceUrl, version, zone);
    this.requestFactory =
        HTTP_TRANSPORT.createRequestFactory(
            request -> {
              request.setParser(JSON_FACTORY.createJsonObjectParser());
              request.setReadTimeout((int) WAIT_OPERATION_MAX_TIMEOUT.toMillis());
            });
  }

  public void setSessionCookie(String sessionCookie) {
    this.sessionCookie = sessionCookie;
  }

  public void setBasicAuth(String username, String password) {
    String auth = username + ":" + password;
    this.basicAuth = Base64.getEncoder().encodeToString(auth.getBytes(UTF_8));
  }

  private String buildRootEndpoint(String serviceUrl, String version, String zone) {
    StringBuilder sb = new StringBuilder(serviceUrl);
    if (serviceUrl.endsWith("/")) {
      sb.setLength(sb.length() - 1);
    }
    if (!serviceUrl.endsWith("/" + version) && !serviceUrl.contains("/" + version + "/")) {
      sb.append("/").append(version);
    }
    if (zone != null && !zone.isEmpty()) {
      sb.append("/zones/").append(zone);
    }
    return sb.toString();
  }

  public String getHostEndpoint(String hostId) {
    return rootEndpoint + "/hosts/" + hostId;
  }

  @CanIgnoreReturnValue
  public Operation createHost(CreateHostRequest req) throws MobileHarnessException {
    return post("/hosts", req, Operation.class);
  }

  @CanIgnoreReturnValue
  public HostInstance createHostAndWait(CreateHostRequest req)
      throws MobileHarnessException, InterruptedException {
    Operation op = createHost(req);
    HostInstance host = waitOperation(null, op.name, HostInstance.class);
    if (host == null || host.name == null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CLOUD_ORCHESTRATOR_OPERATION_ERROR,
          "Host created but not found in operation response");
    }
    checkHostReadiness(host.name);
    return host;
  }

  private void checkHostReadiness(String hostId) throws MobileHarnessException {
    String hostPath = "/hosts/" + hostId + "/";
    get(hostPath, null, HOST_READINESS_MAX_TIMEOUT);
  }

  /** Lists all hosts currently known to the service. */
  public List<HostInstance> listHosts() throws MobileHarnessException {
    ListHostsResponse res = get("/hosts", ListHostsResponse.class, DEFAULT_REQUEST_TIMEOUT);
    return res.items;
  }

  public void deleteHost(String hostName) throws MobileHarnessException {
    delete("/hosts/" + hostName);
  }

  public void fetchArtifactsAndWait(String hostId, String branch, String target)
      throws MobileHarnessException, InterruptedException {
    fetchArtifactsAndWait(hostId, branch, "", target);
  }

  public void fetchArtifactsAndWait(String hostId, String branch, String buildId, String target)
      throws MobileHarnessException, InterruptedException {
    AndroidCiBuild build = new AndroidCiBuild(branch, nullToEmpty(buildId), target);
    FetchArtifactsRequest req = new FetchArtifactsRequest(new AndroidCiBundle(build, 0));
    Operation op = fetchArtifacts(hostId, req, null);
    waitOperation(hostId, op.name, FetchArtifactsResponse.class);
  }

  @CanIgnoreReturnValue
  public Operation fetchArtifacts(
      String hostId, FetchArtifactsRequest req, @Nullable String buildApiCreds)
      throws MobileHarnessException {
    return post("/hosts/" + hostId + "/artifacts", req, Operation.class, buildApiCreds);
  }

  @CanIgnoreReturnValue
  public Operation createCvd(String hostId, CreateCvdRequest req, @Nullable String buildApiCreds)
      throws MobileHarnessException {
    return post("/hosts/" + hostId + "/cvds", req, Operation.class, buildApiCreds);
  }

  /** Lists all CVDs available on a specific host. */
  public List<Cvd> listCvds(String hostId) throws MobileHarnessException {
    ListCvdsResponse res =
        get("/hosts/" + hostId + "/cvds", ListCvdsResponse.class, DEFAULT_REQUEST_TIMEOUT);
    return res.cvds;
  }

  @CanIgnoreReturnValue
  public Operation deleteCvd(String hostId, String cvdId) throws MobileHarnessException {
    return deleteWithResponse("/hosts/" + hostId + "/cvds/" + cvdId, Operation.class);
  }

  /**
   * Waits for a long-running operation to complete.
   *
   * @param hostId the host ID if the operation belongs to a host, null otherwise
   * @param operationName the name of the operation to wait for
   * @param responseClass the class of the expected response payload
   */
  @CanIgnoreReturnValue
  public <T> T waitOperation(@Nullable String hostId, String operationName, Class<T> responseClass)
      throws MobileHarnessException, InterruptedException {
    return waitOperation(
        hostId,
        operationName,
        responseClass,
        WAIT_OPERATION_MAX_RETRIES,
        WAIT_OPERATION_RETRY_DELAY);
  }

  @CanIgnoreReturnValue
  public <T> T waitOperation(
      @Nullable String hostId,
      String operationName,
      Class<T> responseClass,
      int maxRetries,
      Duration retryDelay)
      throws MobileHarnessException, InterruptedException {
    String path =
        (hostId != null ? "/hosts/" + hostId : "") + "/operations/" + operationName + "/:wait";
    try {
      return RetryingCallable.newBuilder(
              () -> executeRequest("POST", path, null, responseClass, null),
              RetryStrategy.uniformDelay(retryDelay, maxRetries))
          .setPredicate(this::isRetriable)
          .setThrowStrategy(RetryingCallable.ThrowStrategy.THROW_LAST)
          .build()
          .call();
    } catch (RetryException e) {
      if (e.getCause() instanceof InterruptedException) {
        Thread.currentThread().interrupt();
        throw (InterruptedException) e.getCause();
      }
      Throwable cause = e.getCause();
      if (cause instanceof HttpResponseException httpResponseException) {
        throw handleHttpException(
            String.format("Failed to wait for operation %s", operationName), httpResponseException);
      }
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_NETWORK_ERROR,
          String.format("Failed to wait for operation %s", operationName),
          e);
    }
  }

  /** Creates a CVD and waits for completion. Max timeout is 10 minutes. */
  @CanIgnoreReturnValue
  public Cvd createCvdWithEnvConfigAndWait(
      String hostId, String cvdId, String branch, String target)
      throws MobileHarnessException, InterruptedException {
    return createCvdWithEnvConfigAndWait(hostId, cvdId, branch, "", target);
  }

  @CanIgnoreReturnValue
  public Cvd createCvdWithEnvConfigAndWait(
      String hostId, String cvdId, String branch, String buildId, String target)
      throws MobileHarnessException, InterruptedException {
    Map<String, Object> envConfig = new HashMap<>();
    Map<String, Object> common = new HashMap<>();
    String buildStr =
        (buildId != null && !buildId.isEmpty())
            ? "@ab/" + buildId + "/" + target
            : "@ab/" + branch + "/" + target;
    common.put("host_package", buildStr);
    envConfig.put("common", common);

    Map<String, Object> instance = new HashMap<>();
    instance.put("@import", "phone");
    Map<String, Object> vm = new HashMap<>();

    // TODO: Consider making these VM parameters configurable.
    vm.put("memory_mb", 8192);
    vm.put("setupwizard_mode", "OPTIONAL");
    vm.put("cpus", 4);
    instance.put("vm", vm);
    Map<String, Object> disk = new HashMap<>();
    disk.put("default_build", buildStr);
    instance.put("disk", disk);
    Map<String, Object> streaming = new HashMap<>();
    streaming.put("device_id", cvdId);
    instance.put("streaming", streaming);

    envConfig.put("instances", ImmutableList.of(instance));
    return createCvdWithEnvConfigAndWait(hostId, envConfig, null);
  }

  /** Creates a CVD and waits for completion. Max timeout is 10 minutes. */
  public Cvd createCvdWithEnvConfigAndWait(
      String hostId, Map<String, Object> envConfig, @Nullable String buildApiCreds)
      throws MobileHarnessException, InterruptedException {
    CreateCvdRequest createReq = new CreateCvdRequest(envConfig);
    return createCvdAndWait(hostId, createReq, buildApiCreds);
  }

  /** Creates a CVD and waits for completion. Max timeout is 10 minutes. */
  @CanIgnoreReturnValue
  public Cvd createCvdAndWait(String hostId, CreateCvdRequest req, @Nullable String buildApiCreds)
      throws MobileHarnessException, InterruptedException {
    Operation op = createCvd(hostId, req, buildApiCreds);
    CreateCvdResponse res = waitOperation(hostId, op.name, CreateCvdResponse.class);

    if (res == null || res.cvds == null || res.cvds.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_CLOUD_ORCHESTRATOR_OPERATION_ERROR,
          "CVD created but not found in operation response");
    }

    return res.cvds.get(0);
  }

  /** Deletes a CVD and waits for completion. Max timeout is 2 minutes. */
  public void deleteCvdAndWait(String hostId, String cvdId)
      throws MobileHarnessException, InterruptedException {
    Operation op = deleteCvd(hostId, cvdId);
    waitOperation(hostId, op.name, Map.class, 24, Duration.ofSeconds(5));
  }

  @CanIgnoreReturnValue
  private <T> T get(String path, Class<T> responseClass, Duration timeout)
      throws MobileHarnessException {
    try {
      return RetryingCallable.newBuilder(
              () -> executeRequest("GET", path, null, responseClass, null, timeout),
              RetryStrategy.exponentialBackoff(requestRetryDelay, 2, 3))
          .setPredicate(this::isRetriable)
          .build()
          .call();
    } catch (RetryException e) {
      Throwable cause = e.getCause();
      if (cause instanceof HttpResponseException httpResponseException) {
        throw handleHttpException(
            String.format("GET request to %s failed", path), httpResponseException);
      }
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_NETWORK_ERROR,
          String.format(
              "GET request to %s failed or timed out after %d ms", path, timeout.toMillis()),
          e);
    }
  }

  private <T> T post(String path, @Nullable Object body, Class<T> responseClass)
      throws MobileHarnessException {
    return post(path, body, responseClass, null);
  }

  private <T> T post(
      String path, @Nullable Object body, Class<T> responseClass, @Nullable String buildApiCreds)
      throws MobileHarnessException {
    int maxRetries = 5;
    try {
      return RetryingCallable.newBuilder(
              () -> executeRequest("POST", path, body, responseClass, buildApiCreds),
              RetryStrategy.exponentialBackoff(requestRetryDelay, 2, maxRetries))
          .setPredicate(this::isRetriable)
          .build()
          .call();
    } catch (RetryException e) {
      Throwable cause = e.getCause();
      if (cause instanceof HttpResponseException httpResponseException) {
        throw handleHttpException(
            "Failed to send POST request after retries", httpResponseException);
      }
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_NETWORK_ERROR, "Failed to send POST request after retries", e);
    }
  }

  private void delete(String path) throws MobileHarnessException {
    deleteWithResponse(path, null);
  }

  @CanIgnoreReturnValue
  private <T> T deleteWithResponse(String path, @Nullable Class<T> responseClass)
      throws MobileHarnessException {
    int maxRetries = 3;
    try {
      return RetryingCallable.newBuilder(
              () -> executeRequest("DELETE", path, null, responseClass, null),
              RetryStrategy.exponentialBackoff(requestRetryDelay, 2, maxRetries))
          .setPredicate(this::isRetriable)
          .build()
          .call();
    } catch (RetryException e) {
      Throwable cause = e.getCause();
      if (cause instanceof HttpResponseException httpResponseException) {
        throw handleHttpException(
            "Failed to send DELETE request after retries", httpResponseException);
      }
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_NETWORK_ERROR, "Failed to send DELETE request after retries", e);
    }
  }

  private <T> T executeRequest(
      String method,
      String path,
      @Nullable Object body,
      @Nullable Class<T> responseClass,
      @Nullable String buildApiCreds)
      throws IOException {
    return executeRequest(method, path, body, responseClass, buildApiCreds, null);
  }

  private <T> T executeRequest(
      String method,
      String path,
      @Nullable Object body,
      @Nullable Class<T> responseClass,
      @Nullable String buildApiCreds,
      @Nullable Duration timeout)
      throws IOException {
    GenericUrl url = new GenericUrl(rootEndpoint + path);
    HttpContent content = body != null ? new JsonHttpContent(JSON_FACTORY, body) : null;
    HttpRequest request = requestFactory.buildRequest(method, url, content);

    if (buildApiCreds != null) {
      request.getHeaders().set("X-Cutf-Host-Orchestrator-BuildAPI-Creds", buildApiCreds);
    }
    if (sessionCookie != null) {
      request.getHeaders().set("Cookie", sessionCookie);
    }
    if (basicAuth != null) {
      request.getHeaders().set("Authorization", "Basic " + basicAuth);
    }
    if (timeout != null) {
      request.setConnectTimeout((int) timeout.toMillis());
      request.setReadTimeout((int) timeout.toMillis());
    }

    HttpResponse response = request.execute();
    try {
      if (responseClass != null) {
        return response.parseAs(responseClass);
      }
      return null;
    } finally {
      response.disconnect();
    }
  }

  @VisibleForTesting
  void setRequestRetryDelay(Duration requestRetryDelay) {
    this.requestRetryDelay = requestRetryDelay;
  }

  private boolean isRetriable(Exception e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof HttpResponseException httpResponseException) {
        int statusCode = httpResponseException.getStatusCode();
        return statusCode == 502 || statusCode == 503 || statusCode == 504;
      }
      if (cause instanceof IOException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private MobileHarnessException handleHttpException(String message, HttpResponseException e) {
    return new MobileHarnessException(
        AndroidErrorId.ANDROID_CLOUD_ORCHESTRATOR_OPERATION_ERROR,
        String.format("%s: %d %s", message, e.getStatusCode(), e.getMessage()),
        e);
  }
}
