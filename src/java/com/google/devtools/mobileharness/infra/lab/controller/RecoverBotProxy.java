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

package com.google.devtools.mobileharness.infra.lab.controller;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.VerifyException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import purejavacomm.CommPortIdentifier;
import purejavacomm.PortInUseException;
import purejavacomm.SerialPort;

/**
 * A proxy server that listens for JSON commands on a socket and translates them into serial
 * commands for an Arduino-based recovery bot.
 *
 * <p>It scans serial ports for a device with the signature "auto recovery controller" and then
 * forwards commands like button press/release to it.
 */
public class RecoverBotProxy implements Runnable {
  private static final Logger logger = Logger.getLogger(RecoverBotProxy.class.getName());

  private static final int BAUDRATE = 115200;
  private static final String HELLO_SIGNATURE = "auto recovery controller";
  private static final int SOCKET_PORT = 9000;

  private static final int SERIAL_READ_TIMEOUT_MS = 500;
  private static final int SERIAL_PROBE_PAUSE_MS = 50;
  private static final int SOCKET_READ_TIMEOUT_MS = 2000;
  private static final int MAX_SOCKET_BYTES = 65536;

  private final Gson gson = new Gson();
  private final Object serialLock = new Object();
  private final ExecutorService clientExecutor = Executors.newCachedThreadPool();

  SerialPort serialPort;
  InputStream serialIn;
  OutputStream serialOut;

  /**
   * Main loop of the proxy server.
   *
   * <p>Finds and connects to the recovery bot via serial, then starts a socket server to accept
   * client commands.
   */
  @Override
  public void run() {
    try {
      CommPortIdentifier portId = findSerialPort();
      if (portId == null) {
        throw new VerifyException("No controller device with expected signature found.");
      }

      serialPort = (SerialPort) portId.open("RecoverBotProxy", 2000);
      serialPort.setSerialPortParams(
          BAUDRATE, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

      serialIn = serialPort.getInputStream();
      serialOut = serialPort.getOutputStream();

      logger.info("[proxy] Serial connected: " + serialPort.getName());

      try (ServerSocket serverSocket = new ServerSocket()) {
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress("0.0.0.0", SOCKET_PORT));
        logger.info("[proxy] Listening on port " + SOCKET_PORT + "...");

        while (!Thread.currentThread().isInterrupted()) {
          Socket client = serverSocket.accept();
          clientExecutor.execute(() -> handleClient(client));
        }
      }
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Fatal server error", e);
    } finally {
      try {
        if (serialPort != null) {
          serialPort.close();
        }
      } catch (RuntimeException e) {
        logger.log(Level.WARNING, "Error closing serial port", e);
      }
      clientExecutor.shutdownNow();
    }
  }

  /**
   * Scans all serial ports for a recovery bot device.
   *
   * <p>It probes each port by sending a "ping" command and looking for HELLO_SIGNATURE in response.
   *
   * @return CommPortIdentifier of the first device found, or null if no device is found.
   */
  @Nullable
  private CommPortIdentifier findSerialPort() {
    Enumeration<CommPortIdentifier> ports = CommPortIdentifier.getPortIdentifiers();

    while (ports.hasMoreElements()) {
      CommPortIdentifier pid = ports.nextElement();
      if (pid.getPortType() != CommPortIdentifier.PORT_SERIAL) {
        continue;
      }

      logger.info("Trying port: " + pid.getName());

      SerialPort probePort = null;
      InputStream in = null;
      OutputStream out = null;

      try {
        probePort = (SerialPort) pid.open("RecoverBotProxy probe", 2000);
        probePort.setSerialPortParams(
            BAUDRATE, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

        in = probePort.getInputStream();
        out = probePort.getOutputStream();

        Thread.sleep(SERIAL_PROBE_PAUSE_MS);

        drainAvailable(in);
        out.write("ping\n".getBytes(UTF_8));
        out.flush();

        String line = readLineWithTimeout(in, SERIAL_READ_TIMEOUT_MS);
        logger.info(String.format("Response from %s: '%s'", pid.getName(), line));

        if (line != null && line.toLowerCase(Locale.ROOT).contains(HELLO_SIGNATURE)) {
          logger.info("[proxy] Found target device on " + pid.getName());
          return pid;
        }
      } catch (PortInUseException e) {
        logger.warning("Could not open port " + pid.getName() + " (in use / permission).");
      } catch (Exception e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        logger.log(Level.WARNING, "Probe error on " + pid.getName(), e);
      } finally {
        try {
          if (probePort != null) {
            probePort.close();
          }
        } catch (RuntimeException e) {
          logger.log(Level.WARNING, "Error closing probe port " + pid.getName(), e);
        }
      }
    }
    return null;
  }

  /**
   * Handles a single client connection.
   *
   * <p>Reads a JSON request from the socket, translates it to a serial command for the recovery
   * bot, sends it, reads the response, and sends a JSON response back to the client.
   *
   * @param clientSocket The socket connection to the client.
   */
  void handleClient(Socket clientSocket) {
    try (clientSocket) {
      clientSocket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);

      byte[] reqBytes = readUpTo(clientSocket.getInputStream(), MAX_SOCKET_BYTES);
      if (reqBytes.length == 0) {
        return;
      }

      JsonObject reqJson = gson.fromJson(new String(reqBytes, UTF_8), JsonObject.class);
      logger.info("[proxy] Received request: " + reqJson);

      String command = getAsStringOrNull(reqJson, "command");
      String deviceId = getAsStringOrNull(reqJson, "device_id");
      String boardAddrStr = getAsStringOrNull(reqJson, "board_addr");
      String channelStr = getAsStringOrNull(reqJson, "channel");
      String durationStr = getAsStringOrNull(reqJson, "duration_ms");

      if (command == null) {
        throw new IllegalArgumentException("Missing command");
      }

      Integer boardAddr = (boardAddrStr != null) ? Integer.decode(boardAddrStr) : null;
      Integer channel = (channelStr != null) ? Integer.decode(channelStr) : null;

      String cmdToSend;
      switch (command) {
        case "press_ms" -> {
          if (boardAddr == null || channel == null || durationStr == null) {
            throw new IllegalArgumentException("Missing params for press_ms");
          }
          int duration = Integer.parseInt(durationStr);
          cmdToSend = String.format("press_ms %x %d %d\n", boardAddr, channel, duration);
        }
        case "press" -> {
          if (boardAddr == null || channel == null) {
            throw new IllegalArgumentException("Missing params for press");
          }
          cmdToSend = String.format("press %x %d\n", boardAddr, channel);
        }
        case "release" -> {
          if (boardAddr == null || channel == null) {
            throw new IllegalArgumentException("Missing params for release");
          }
          cmdToSend = String.format("release %x %d\n", boardAddr, channel);
        }
        case "status" -> cmdToSend = "status\n";
        case "ping" -> cmdToSend = "ping\n";
        default -> throw new IllegalArgumentException("Unknown command: " + command);
      }

      String rawResponse;
      synchronized (serialLock) {
        logger.info("[proxy] Sending command: " + cmdToSend.trim());
        serialOut.write(cmdToSend.getBytes(UTF_8));
        serialOut.flush();
        rawResponse = readLineWithTimeout(serialIn, SERIAL_READ_TIMEOUT_MS);
        if (rawResponse != null) {
          rawResponse = rawResponse.trim();
        }
      }

      JsonObject resp = new JsonObject();
      resp.addProperty("status", "OK");
      if (deviceId != null) {
        resp.addProperty("device_id", deviceId);
      }
      resp.addProperty("command", command);
      resp.addProperty("raw_response", rawResponse);

      byte[] respBytes = gson.toJson(resp).getBytes(UTF_8);
      clientSocket.getOutputStream().write(respBytes);
      clientSocket.getOutputStream().flush();

    } catch (JsonSyntaxException | IllegalArgumentException | IOException e) {
      sendError(clientSocket, "ERROR", e.getMessage(), e);
    } catch (RuntimeException e) {
      sendError(clientSocket, "ERROR", "Unexpected error: " + e.getMessage(), e);
    }
  }

  /**
   * Sends a JSON error response to the client.
   *
   * @param sock The client socket.
   * @param status The status to include in the response.
   * @param message The error message.
   * @param e The exception that occurred.
   */
  private static void sendError(Socket sock, String status, String message, Exception e) {
    Logger.getLogger(RecoverBotProxy.class.getName()).log(Level.SEVERE, "Client handling error", e);
    try {
      JsonObject resp = new JsonObject();
      resp.addProperty("status", status);
      resp.addProperty("error", message);

      byte[] bytes = new Gson().toJson(resp).getBytes(UTF_8);
      sock.getOutputStream().write(bytes);
      sock.getOutputStream().flush();
    } catch (Exception e2) {
      Logger.getLogger(RecoverBotProxy.class.getName())
          .log(Level.SEVERE, "Failed to send error response", e2);
    }
  }

  /**
   * Retrieves a string property from a {@link JsonObject} or returns null if not present or null.
   *
   * @param obj The JSON object.
   * @param key The property key.
   * @return The string value or null.
   */
  @Nullable
  private static String getAsStringOrNull(JsonObject obj, String key) {
    return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
  }

  /**
   * Like python conn.recv(65536): read whatever is available (up to max) without requiring '\n'.
   */
  private static byte[] readUpTo(InputStream in, int maxBytes) throws IOException {
    byte[] buf = new byte[maxBytes];
    int n = in.read(buf); // will block up to socket SO_TIMEOUT
    if (n <= 0) {
      return new byte[0];
    }
    return Arrays.copyOf(buf, n);
  }

  /** Drain any currently available bytes from serial input (best effort). */
  private static void drainAvailable(InputStream in) throws IOException {
    while (in.available() > 0) {
      int toRead = Math.min(in.available(), 4096);
      byte[] tmp = in.readNBytes(toRead);
      if (tmp.length == 0) {
        break;
      }
    }
  }

  /**
   * Read a line from serial like pyserial.readline(timeout=x):
   *
   * <ul>
   *   <li>collect bytes until '\n' or '\r'
   *   <li>if nothing arrives before timeout -> return null
   *   <li>if some bytes arrive but no newline before timeout -> return partial string
   * </ul>
   */
  @Nullable
  private static String readLineWithTimeout(InputStream in, int timeoutMs) throws IOException {
    long deadline = Instant.now().plusMillis(timeoutMs).toEpochMilli();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    while (InstantSource.system().instant().isBefore(Instant.ofEpochMilli(deadline))) {
      int avail = in.available();
      if (avail <= 0) {
        try {
          Thread.sleep(5);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
        continue;
      }

      int b = in.read();
      if (b < 0) {
        break;
      }

      if (b == '\n' || b == '\r') {
        if (baos.size() == 0) {
          continue;
        }
        break;
      }

      baos.write(b);
      if (baos.size() >= 8192) {
        break;
      } // safety
    }

    if (baos.size() == 0) {
      return null;
    }
    return baos.toString(UTF_8);
  }

  /**
   * Entry point for the RecoverBotProxy server.
   *
   * @param args Command line arguments.
   */
  public static void main(String[] args) {
    RecoverBotProxy proxy = new RecoverBotProxy();
    Thread t = new Thread(proxy, "RecoverBotProxy");
    t.setDaemon(false);
    t.start();
  }
}
