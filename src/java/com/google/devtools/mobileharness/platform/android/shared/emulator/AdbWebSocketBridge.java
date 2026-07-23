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

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryException;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryStrategy;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryingCallable;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * A production-grade bridge that connects a local ADB client to a remote ADB server via WebSocket.
 */
public class AdbWebSocketBridge {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int ADB_LISTEN_TIMEOUT_MS = 30000;
  private static final int MAX_WS_CONNECT_RETRIES = 10;

  /**
   * Standard max frame size for ADB WebSocket chunks (16KB).
   *
   * <p>This value is chosen to match OkHttp's internal {@code INITIAL_MAX_FRAME_SIZE} to ensure
   * efficient data pumping without unnecessary fragmentation.
   */
  private static final int ADB_WS_MAX_FRAME_SIZE = 16384;

  private static final Supplier<OkHttpClient> SHARED_CLIENT =
      Suppliers.memoize(
          () -> {
            okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
            dispatcher.setMaxRequests(500);
            dispatcher.setMaxRequestsPerHost(500);
            return new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .pingInterval(Duration.ofSeconds(10))
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ZERO)
                .writeTimeout(Duration.ofSeconds(10))
                .build();
          });

  private final String webSocketUrl;
  private final String token;
  private final int adbPort;
  private final Sleeper sleeper;
  private final ListeningExecutorService executor =
      ThreadPools.createStandardThreadPool("adb-websocket-bridge");
  private volatile ServerSocket serverSocket;
  private final AtomicBoolean isRunning = new AtomicBoolean(false);

  public AdbWebSocketBridge(String webSocketUrl, String token, int adbPort) {
    this(webSocketUrl, token, adbPort, Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  AdbWebSocketBridge(String webSocketUrl, String token, int adbPort, Sleeper sleeper) {
    this.webSocketUrl = webSocketUrl;
    this.token = token;
    this.adbPort = adbPort;
    this.sleeper = sleeper;
  }

  /** Starts the bridge in the background. */
  public void start() {
    logFailure(
        executor.submit(
            () -> {
              run();
              return null;
            }),
        Level.SEVERE,
        "Bridge background loop failed");
  }

  private void run() throws IOException {
    isRunning.set(true);
    try (ServerSocket ss = new ServerSocket()) {
      ss.setReuseAddress(true);
      ss.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), adbPort), 50);
      this.serverSocket = ss;
      serverSocket.setSoTimeout(ADB_LISTEN_TIMEOUT_MS);

      while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
        try {
          Socket tcpSocket = serverSocket.accept();
          tcpSocket.setKeepAlive(true);
          tcpSocket.setSoTimeout(0);

          logger.atInfo().log("Accepted TCP connection: %s, submitting to executor", tcpSocket);
          try {
            logFailure(
                executor.submit(
                    () -> {
                      try {
                        handleSession(tcpSocket);
                      } catch (InterruptedException e) {
                        logger.atWarning().log(
                            "Session handler interrupted for TCP: %s", tcpSocket);
                        Thread.currentThread().interrupt();
                      } catch (RuntimeException | Error e) {
                        logger.atWarning().withCause(e).log(
                            "Error in session handler for TCP: %s", tcpSocket);
                        throwIfInstanceOf(e, Error.class);
                      } finally {
                        try {
                          logger.atInfo().log(
                              "Closing TCP socket in executor finally: %s", tcpSocket);
                          tcpSocket.close();
                        } catch (IOException e) {
                          // ignore
                        }
                      }
                    }),
                Level.SEVERE,
                "Session handler fatal error");
          } catch (Exception e) {
            try {
              logger.atWarning().log(
                  "Failed to submit session handler to executor, closing TCP: %s", tcpSocket);
              tcpSocket.close();
            } catch (IOException ignored) {
              // ignore
            }
            throw e;
          }
        } catch (SocketTimeoutException e) {
          // Just a heartbeat for the listener loop
        } catch (IOException | RuntimeException | Error e) {
          if (isRunning.get()) {
            logger.atWarning().withCause(e).log("Error in bridge accept loop");
            try {
              sleeper.sleep(Duration.ofMillis(100));
            } catch (InterruptedException ie) {
              logger.atWarning().log("Sleeper interrupted in accept loop");
              Thread.currentThread().interrupt();
            }
          }
          throwIfInstanceOf(e, Error.class);
        }
      }
    } finally {
      stop();
    }
  }

  public void stop() {
    isRunning.set(false);
    executor.shutdownNow();
    if (serverSocket != null) {
      try {
        serverSocket.close();
      } catch (IOException ignored) {
        // Exception ignored intentionally on teardown
      }
    }
  }

  /**
   * Handles a single local TCP connection and sets up the WebSocket bridge.
   *
   * <p>Retry Strategy:
   *
   * <ul>
   *   <li>Max retries: {@value #MAX_WS_CONNECT_RETRIES} attempts.
   *   <li>Backoff: Exponential backoff using {@code 2^(i/2)} seconds (e.g., 1s, 1.4s, 2s, 2.8s...).
   *   <li>Timeout per attempt: 15 seconds to establish the WebSocket connection.
   * </ul>
   */
  private void handleSession(Socket tcpSocket) throws InterruptedException {
    logger.atInfo().log("Handling bridge session for TCP socket: %s", tcpSocket);
    Callable<ConnectListener> connectCallable =
        () -> {
          logger.atInfo().log("Attempting to connect WebSocket for TCP socket: %s", tcpSocket);
          CountDownLatch sessionLatch = new CountDownLatch(1);
          AtomicBoolean closed = new AtomicBoolean(false);
          ConnectListener listener = new ConnectListener(tcpSocket, sessionLatch, closed);
          WebSocket ws = SHARED_CLIENT.get().newWebSocket(buildRequest(), listener);
          listener.ws = ws;

          if (listener.connectedLatch.await(15, SECONDS) && listener.connected.get()) {
            logger.atInfo().log(
                "WebSocket connection established successfully for TCP: %s", tcpSocket);
            return listener;
          } else {
            // TODO: rate limit this logging.
            logger.atFine().log(
                "WebSocket connection attempt timed out or failed for TCP: %s", tcpSocket);
            if (ws != null) {
              // Code 1000 indicates a "Normal Closure" per WebSocket protocol (RFC 6455)
              ws.close(1000, "Connection attempt failed");
            }
            throw new ConnectException("Failed to establish WebSocket connection");
          }
        };

    RetryStrategy retryStrategy =
        RetryStrategy.exponentialBackoff(
            Duration.ofSeconds(1), Math.sqrt(2), MAX_WS_CONNECT_RETRIES);

    ConnectListener successfulListener = null;
    try {
      successfulListener =
          RetryingCallable.newBuilder(connectCallable, retryStrategy)
              .setPredicate(e -> isRunning.get() && e instanceof ConnectException)
              .setThrowStrategy(RetryingCallable.ThrowStrategy.THROW_LAST)
              .setSleeper(sleeper)
              .build()
              .call();
    } catch (RetryException e) {
      logger.atWarning().withCause(e).log(
          "Failed to establish WebSocket connection after retries for TCP: %s", tcpSocket);
      if (e.getCause() instanceof InterruptedException) {
        Thread.currentThread().interrupt();
        throw (InterruptedException) e.getCause();
      }
      try {
        tcpSocket.close();
      } catch (IOException ignored) {
        // Exception ignored intentionally
      }
      return;
    }

    // Start TCP -> WebSocket pumping
    startTcpToWsPump(
        tcpSocket,
        successfulListener.ws,
        successfulListener.closed,
        successfulListener.sessionLatch);

    // Wait for session to finish
    logger.atInfo().log("Waiting for session to finish for TCP: %s", tcpSocket);
    successfulListener.sessionLatch.await();
    logger.atInfo().log("Session finished for TCP: %s", tcpSocket);
  }

  private class ConnectListener extends WebSocketListener {
    final CountDownLatch connectedLatch = new CountDownLatch(1);
    final AtomicBoolean connected = new AtomicBoolean(false);

    private final Socket tcpSocket;
    private final CountDownLatch sessionLatch;
    private final AtomicBoolean closed;
    volatile WebSocket ws;

    ConnectListener(Socket tcpSocket, CountDownLatch sessionLatch, AtomicBoolean closed) {
      this.tcpSocket = tcpSocket;
      this.sessionLatch = sessionLatch;
      this.closed = closed;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
      logger.atInfo().log("WebSocket connection opened. WS: %s", webSocket);
      connected.set(true);
      connectedLatch.countDown();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
      if (connected.get()) {
        logger.atWarning().withCause(t).log(
            "WebSocket session failed. WS: %s, Response: %s", webSocket, response);
      } else {
        // TODO: rate limit this logging.
        logger.atFine().withCause(t).log(
            "WebSocket connection failure during setup. WS: %s, Response: %s", webSocket, response);
      }
      connectedLatch.countDown();
      if (connected.get() && sessionLatch != null) {
        terminateSession(
            webSocket, tcpSocket, closed, "WS Failure: " + t.getMessage(), sessionLatch);
      }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
      if (tcpSocket != null) {
        try {
          tcpSocket.getOutputStream().write(bytes.toByteArray());
          tcpSocket.getOutputStream().flush();
        } catch (IOException e) {
          terminateSession(
              webSocket, tcpSocket, closed, "TCP write failure: " + e.getMessage(), sessionLatch);
        }
      }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
      logger.atInfo().log(
          "WebSocket connection closing. WS: %s, Code: %d, Reason: %s", webSocket, code, reason);
      if (sessionLatch != null) {
        terminateSession(webSocket, tcpSocket, closed, "Remote closing: " + reason, sessionLatch);
      }
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
      logger.atInfo().log(
          "WebSocket connection closed. WS: %s, Code: %d, Reason: %s", webSocket, code, reason);
      if (sessionLatch != null) {
        terminateSession(webSocket, tcpSocket, closed, "Remote closed: " + reason, sessionLatch);
      }
    }
  }

  private void startTcpToWsPump(
      Socket tcpSocket, WebSocket ws, AtomicBoolean closed, CountDownLatch latch) {
    logger.atInfo().log("Starting TCP to WebSocket pump for TCP socket: %s", tcpSocket);
    logFailure(
        executor.submit(
            () -> {
              try (InputStream in = tcpSocket.getInputStream()) {
                byte[] buffer = new byte[ADB_WS_MAX_FRAME_SIZE];
                int bytesRead;
                while (!closed.get() && (bytesRead = in.read(buffer)) != -1) {
                  if (!ws.send(ByteString.of(buffer, 0, bytesRead))) {
                    break;
                  }
                }
              } catch (IOException e) {
                // Expected on session close
              } finally {
                terminateSession(ws, tcpSocket, closed, "TCP closed", latch);
              }
            }),
        Level.SEVERE,
        "TCP to WebSocket pump fatal error");
  }

  private void terminateSession(
      WebSocket ws, Socket tcpSocket, AtomicBoolean closed, String reason, CountDownLatch latch) {
    if (closed.compareAndSet(false, true)) {
      logger.atInfo().log(
          "Terminating bridge session. Reason: %s, WS: %s, TCP: %s", reason, ws, tcpSocket);
      if (ws != null) {
        // Code 1000 indicates a "Normal Closure" per WebSocket protocol (RFC 6455)
        ws.close(1000, reason);
      }
      try {
        tcpSocket.close();
      } catch (IOException ignored) {
        // Exception ignored intentionally
      }
      latch.countDown();
    }
  }

  private Request buildRequest() {
    Request.Builder builder = new Request.Builder().url(webSocketUrl);
    if (token.contains("=")) {
      builder.addHeader("Cookie", token);
    } else {
      builder.addHeader("Authorization", buildAuthHeader(token));
    }
    return builder.build();
  }

  private String buildAuthHeader(String token) {
    if (token.contains(":") && !token.startsWith("http")) {
      String[] parts = token.split(":", 2);
      return Credentials.basic(parts[0], parts.length > 1 ? parts[1] : "");
    } else if (token.startsWith("Basic ") || token.startsWith("Bearer ")) {
      return token;
    } else {
      return "Bearer " + token;
    }
  }
}
