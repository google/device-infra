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
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@SuppressWarnings("AddressSelection")
public class AdbWebSocketBridgeTest {

  @Rule public final MockWebServer server = new MockWebServer();
  private AdbWebSocketBridge bridge;
  private int bridgePort;

  private final BlockingQueue<WebSocket> webSockets = new LinkedBlockingQueue<>();
  private final BlockingQueue<ByteString> receivedMessages = new LinkedBlockingQueue<>();

  private final WebSocketListener serverListener =
      new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
          webSockets.add(webSocket);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
          receivedMessages.add(bytes);
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
          webSocket.close(1000, "Closing response");
        }
      };

  @Before
  public void setUp() throws Exception {
    try (ServerSocket ss = new ServerSocket(0)) {
      bridgePort = ss.getLocalPort();
    }
    String wsUrl = server.url("/adb").toString().replace("http", "ws");
    bridge =
        new AdbWebSocketBridge(
            wsUrl,
            "test-token",
            bridgePort,
            Sleeper.noOpSleeper(),
            AdbWebSocketBridge.DEFAULT_MAX_WS_QUEUE_SIZE_BYTES);
  }

  @After
  public void tearDown() {
    if (bridge != null) {
      bridge.stop();
    }
  }

  @Test
  public void start_connectAndRelayMessages() throws Exception {
    server.enqueue(new MockResponse().withWebSocketUpgrade(serverListener));

    // Use the public constructor to exercise the production defaults.
    bridge.stop();
    bridge =
        new AdbWebSocketBridge(
            server.url("/adb").toString().replace("http", "ws"), "test-token", bridgePort);
    bridge.start();

    // Small delay to ensure ServerSocket is listening
    Thread.sleep(2000);

    // Manually create a client TCP socket to simulate an external ADB client (like the adb CLI)
    // connecting to the bridge. This allows hermetic end-to-end verification of the data pump.
    try (Socket clientSocket = new Socket(InetAddress.getByName("127.0.0.1"), bridgePort)) {
      clientSocket.setSoTimeout(5000);
      WebSocket ws = webSockets.poll(10, SECONDS);
      assertThat(ws).isNotNull();

      // TCP -> WebSocket
      String testMessage = "Hello from ADB Client";
      OutputStream out = clientSocket.getOutputStream();
      out.write(testMessage.getBytes(StandardCharsets.UTF_8));
      out.flush();

      ByteString received = receivedMessages.poll(10, SECONDS);
      assertThat(received).isNotNull();
      assertThat(received.utf8()).isEqualTo(testMessage);

      // WebSocket -> TCP
      String responseMessage = "Hello from remote device";
      ws.send(ByteString.encodeUtf8(responseMessage));

      InputStream in = clientSocket.getInputStream();
      byte[] buffer = new byte[1024];
      int read = in.read(buffer);
      assertThat(new String(buffer, 0, read, StandardCharsets.UTF_8)).isEqualTo(responseMessage);
    }
  }

  @Test
  public void start_stopCancelsReconnectLoop() throws Exception {
    // Server returns an error so the bridge enters retry backoff
    server.enqueue(new MockResponse().setResponseCode(500));

    bridge.start();
    Thread.sleep(500);

    // Trigger connection
    Thread connectThread =
        new Thread(
            () -> {
              try (Socket clientSocket =
                  new Socket(InetAddress.getByName("127.0.0.1"), bridgePort)) {
                Thread.sleep(10000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } catch (Exception ignored) {
                // Exception ignored intentionally
              }
            });
    connectThread.start();

    // Wait a moment for retry loop to begin
    Thread.sleep(1000);
    long startStop = System.currentTimeMillis();
    bridge.stop();

    // Bridge run loop should terminate quickly
    Thread.sleep(500);
    assertThat(System.currentTimeMillis() - startStop).isLessThan(3000L);
    connectThread.interrupt();
  }

  @Test
  public void start_handlesSuccessiveTcpConnections() throws Exception {
    // Proves that after one adb connection finishes, a fresh adb connection works fine!
    server.enqueue(new MockResponse().withWebSocketUpgrade(serverListener));
    server.enqueue(new MockResponse().withWebSocketUpgrade(serverListener));

    bridge.start();
    Thread.sleep(2000);

    // First connection
    try (Socket socket1 = new Socket(InetAddress.getByName("127.0.0.1"), bridgePort)) {
      WebSocket ws1 = webSockets.poll(5, SECONDS);
      assertThat(ws1).isNotNull();
      socket1.getOutputStream().write("CMD1".getBytes(StandardCharsets.UTF_8));
      assertThat(receivedMessages.poll(5, SECONDS).utf8()).isEqualTo("CMD1");
      ws1.close(1000, "Bye");
    }

    Thread.sleep(2000);

    // Second connection directly after
    try (Socket socket2 = new Socket(InetAddress.getByName("127.0.0.1"), bridgePort)) {
      WebSocket ws2 = webSockets.poll(5, SECONDS);
      assertThat(ws2).isNotNull();
      socket2.getOutputStream().write("CMD2".getBytes(StandardCharsets.UTF_8));
      assertThat(receivedMessages.poll(5, SECONDS).utf8()).isEqualTo("CMD2");
    }
  }

  @Test
  public void start_handlesConcurrentTcpConnections() throws Exception {
    server.enqueue(new MockResponse().withWebSocketUpgrade(serverListener));
    server.enqueue(new MockResponse().withWebSocketUpgrade(serverListener));

    bridge.start();
    Thread.sleep(2000);

    // Open first connection and keep it open
    try (Socket socket1 = new Socket(InetAddress.getByName("127.0.0.1"), bridgePort);
        // Open second connection concurrently
        Socket socket2 = new Socket(InetAddress.getByName("127.0.0.1"), bridgePort)) {

      WebSocket ws1 = webSockets.poll(5, SECONDS);
      WebSocket ws2 = webSockets.poll(5, SECONDS);

      assertThat(ws1).isNotNull();
      assertThat(ws2).isNotNull();

      // Verify they can both send messages
      socket1.getOutputStream().write("CMD1".getBytes(StandardCharsets.UTF_8));
      socket1.getOutputStream().flush();
      socket2.getOutputStream().write("CMD2".getBytes(StandardCharsets.UTF_8));
      socket2.getOutputStream().flush();

      // We might receive them in any order, so we check the queue
      Set<String> received = new HashSet<>();
      received.add(receivedMessages.poll(5, SECONDS).utf8());
      received.add(receivedMessages.poll(5, SECONDS).utf8());

      assertThat(received).containsExactly("CMD1", "CMD2");

      ws1.close(1000, "Bye 1");
      ws2.close(1000, "Bye 2");
    }
  }

  @Test
  public void start_retryWebSocketConnectionOnHandshakeFailure() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(500));
    server.enqueue(new MockResponse().withWebSocketUpgrade(serverListener));

    bridge.start();
    Thread.sleep(2000);

    try (Socket clientSocket = new Socket(InetAddress.getByName("127.0.0.1"), bridgePort)) {
      clientSocket.setSoTimeout(5000);

      WebSocket ws = webSockets.poll(10, SECONDS);
      assertThat(ws).isNotNull();

      String testMessage = "Hello after retry";
      OutputStream out = clientSocket.getOutputStream();
      out.write(testMessage.getBytes(StandardCharsets.UTF_8));
      out.flush();

      ByteString received = receivedMessages.poll(10, SECONDS);
      assertThat(received).isNotNull();
      assertThat(received.utf8()).isEqualTo(testMessage);
    }
  }

  @Test
  public void stop_closesActiveSockets() throws Exception {
    server.enqueue(new MockResponse().withWebSocketUpgrade(serverListener));

    bridge.start();
    Thread.sleep(2000);

    Socket clientSocket = new Socket(InetAddress.getByName("127.0.0.1"), bridgePort);
    clientSocket.setSoTimeout(5000);

    WebSocket ws = webSockets.poll(10, SECONDS);
    assertThat(ws).isNotNull();

    // Session is active. Now stop the bridge.
    bridge.stop();

    // Verify client socket is closed by checking if read returns -1 (EOF)
    InputStream in = clientSocket.getInputStream();
    int read = in.read();
    assertThat(read).isEqualTo(-1);

    clientSocket.close();
  }

  @Test
  public void start_throttlesTcpReadsWhenWebSocketQueueExceedsLimit() throws Exception {
    CountDownLatch releaseServerReads = new CountDownLatch(1);
    AtomicInteger totalReceivedBytes = new AtomicInteger(0);
    CountDownLatch allBytesReceived = new CountDownLatch(1);
    int payloadSize = 8 * 1024 * 1024; // 8 MiB to exceed OS socket buffers and build ws.queueSize()

    WebSocketListener slowServerListener =
        new WebSocketListener() {
          @Override
          public void onOpen(WebSocket webSocket, Response response) {
            webSockets.add(webSocket);
          }

          @Override
          public void onMessage(WebSocket webSocket, ByteString bytes) {
            try {
              if (!releaseServerReads.await(10, SECONDS)) {
                releaseServerReads.countDown();
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            if (totalReceivedBytes.addAndGet(bytes.size()) >= payloadSize) {
              allBytesReceived.countDown();
            }
          }

          @Override
          public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(1000, "Closing response");
          }
        };

    server.enqueue(new MockResponse().withWebSocketUpgrade(slowServerListener));

    AtomicInteger backpressureSleeps = new AtomicInteger(0);
    Sleeper countingSleeper =
        duration -> {
          if (backpressureSleeps.incrementAndGet() >= 3) {
            releaseServerReads.countDown();
          }
          Thread.sleep(5);
        };

    bridge.stop();
    bridge =
        new AdbWebSocketBridge(
            server.url("/adb").toString().replace("http", "ws"),
            "test-token",
            bridgePort,
            countingSleeper,
            /* maxWsQueueSizeBytes= */ 64L * 1024);
    bridge.start();
    Thread.sleep(1000);

    byte[] chunk = new byte[65536];
    try (Socket clientSocket = new Socket(InetAddress.getByName("127.0.0.1"), bridgePort)) {
      clientSocket.setSoTimeout(10000);
      WebSocket ws = webSockets.poll(10, SECONDS);
      assertThat(ws).isNotNull();

      OutputStream out = clientSocket.getOutputStream();
      int written = 0;
      while (written < payloadSize) {
        out.write(chunk);
        written += chunk.length;
      }
      out.flush();

      assertThat(allBytesReceived.await(15, SECONDS)).isTrue();
      assertThat(totalReceivedBytes.get()).isEqualTo(payloadSize);
      assertThat(backpressureSleeps.get()).isAtLeast(3);
      ws.close(1000, "Done");
    }
  }

  @Test
  public void start_terminatesSessionWhenBackpressureWaitInterrupted() throws Exception {
    server.enqueue(new MockResponse().withWebSocketUpgrade(serverListener));

    Sleeper interruptingSleeper =
        duration -> {
          throw new InterruptedException("interrupted in test");
        };
    bridge.stop();
    bridge =
        new AdbWebSocketBridge(
            server.url("/adb").toString().replace("http", "ws"),
            "test-token",
            bridgePort,
            interruptingSleeper,
            /* maxWsQueueSizeBytes= */ 0L);
    bridge.start();
    Thread.sleep(1000);

    try (Socket clientSocket = new Socket(InetAddress.getByName("127.0.0.1"), bridgePort)) {
      clientSocket.setSoTimeout(10000);
      assertThat(webSockets.poll(10, SECONDS)).isNotNull();

      clientSocket.getOutputStream().write("data".getBytes(StandardCharsets.UTF_8));
      clientSocket.getOutputStream().flush();

      // The pump is interrupted while waiting for queue drain, so the session is terminated
      // without forwarding the data.
      assertThat(clientSocket.getInputStream().read()).isEqualTo(-1);
      assertThat(receivedMessages.poll(1, SECONDS)).isNull();
    }
  }

  @Test
  public void start_truncatesLongRemoteCloseReason() throws Exception {
    BlockingQueue<String> serverClosedReasons = new LinkedBlockingQueue<>();
    WebSocketListener recordingServerListener =
        new WebSocketListener() {
          @Override
          public void onOpen(WebSocket webSocket, Response response) {
            webSockets.add(webSocket);
          }

          @Override
          public void onClosed(WebSocket webSocket, int code, String reason) {
            serverClosedReasons.add(reason);
          }
        };
    server.enqueue(new MockResponse().withWebSocketUpgrade(recordingServerListener));

    bridge.start();
    Thread.sleep(1000);

    try (Socket clientSocket = new Socket(InetAddress.getByName("127.0.0.1"), bridgePort)) {
      clientSocket.setSoTimeout(10000);
      WebSocket ws = webSockets.poll(10, SECONDS);
      assertThat(ws).isNotNull();

      // A 120-byte remote reason makes the bridge's "Remote closing: <reason>" exceed the 123-byte
      // RFC 6455 limit, which OkHttp would reject with IllegalArgumentException if not truncated.
      String longReason = "x".repeat(120);
      assertThat(ws.close(1000, longReason)).isTrue();

      assertThat(clientSocket.getInputStream().read()).isEqualTo(-1);
      String echoedReason = serverClosedReasons.poll(10, SECONDS);
      assertThat(echoedReason).isNotNull();
      assertThat(echoedReason).startsWith("Remote closing: ");
      assertThat(ByteString.encodeUtf8(echoedReason).size()).isAtMost(123);
    }
  }

  @Test
  public void pumpTcpToWs_eof_returnsTcpClosed() throws Exception {
    FakeWebSocket ws = new FakeWebSocket(/* sendResult= */ true);

    String reason =
        bridge.pumpTcpToWs(
            new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)),
            ws,
            new AtomicBoolean(false));

    assertThat(reason).isEqualTo("TCP closed");
    assertThat(ws.sent).containsExactly(ByteString.encodeUtf8("abc"));
    assertThat(ws.cancelled).isFalse();
  }

  @Test
  public void pumpTcpToWs_sendRejected_cancelsWebSocket() throws Exception {
    FakeWebSocket ws = new FakeWebSocket(/* sendResult= */ false);

    String reason =
        bridge.pumpTcpToWs(
            new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)),
            ws,
            new AtomicBoolean(false));

    assertThat(reason).isEqualTo("WebSocket send rejected");
    assertThat(ws.cancelled).isTrue();
  }

  @Test
  public void pumpTcpToWs_sessionClosedDuringBackpressure_stopsWithoutSending() throws Exception {
    AtomicBoolean closed = new AtomicBoolean(false);
    FakeWebSocket ws = new FakeWebSocket(/* sendResult= */ true);
    ws.queueSize = Long.MAX_VALUE;
    Sleeper closingSleeper = duration -> closed.set(true);
    AdbWebSocketBridge pumpBridge =
        new AdbWebSocketBridge(
            "ws://localhost/adb",
            "test-token",
            bridgePort,
            closingSleeper,
            /* maxWsQueueSizeBytes= */ 1L);

    try {
      String reason =
          pumpBridge.pumpTcpToWs(
              new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), ws, closed);

      assertThat(reason).isEqualTo("TCP closed");
      assertThat(ws.sent).isEmpty();
      assertThat(ws.cancelled).isFalse();
    } finally {
      pumpBridge.stop();
    }
  }

  /** Minimal {@link WebSocket} fake for exercising the TCP to WebSocket pump in isolation. */
  private static final class FakeWebSocket implements WebSocket {
    private final boolean sendResult;
    final List<ByteString> sent = new ArrayList<>();
    volatile long queueSize = 0;
    volatile boolean cancelled = false;

    FakeWebSocket(boolean sendResult) {
      this.sendResult = sendResult;
    }

    @Override
    public Request request() {
      return new Request.Builder().url("http://localhost/adb").build();
    }

    @Override
    public long queueSize() {
      return queueSize;
    }

    @Override
    public boolean send(String text) {
      return send(ByteString.encodeUtf8(text));
    }

    @Override
    public boolean send(ByteString bytes) {
      if (sendResult) {
        sent.add(bytes);
      }
      return sendResult;
    }

    @Override
    public boolean close(int code, String reason) {
      return true;
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }
}
