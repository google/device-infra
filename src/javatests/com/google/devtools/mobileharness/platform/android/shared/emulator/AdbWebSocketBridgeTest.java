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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
      };

  @Before
  public void setUp() throws Exception {
    try (ServerSocket ss = new ServerSocket(0)) {
      bridgePort = ss.getLocalPort();
    }
    String wsUrl = server.url("/adb").toString().replace("http", "ws");
    bridge = new AdbWebSocketBridge(wsUrl, "test-token", bridgePort, Sleeper.noOpSleeper());
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
}
