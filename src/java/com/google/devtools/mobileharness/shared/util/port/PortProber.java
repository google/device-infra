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

package com.google.devtools.mobileharness.shared.util.port;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Random;

/**
 * Branched from {@code com.google.testing.serverutil.PortProber}.
 *
 * <p>Class that finds free ports for use on the local machine, either through a port scan or via
 * the PortServer process.
 *
 * <p>This class is entirely static and not testable.
 *
 * <p>This class is not being actively supported, but needs a significant rewrite for testability's
 * sake. The API is likely to change for methods beyond .pickUnusedPort(). Read the method javadoc
 * carefully as the behavior of the public .pickUnusedPort* methods are not consistent between local
 * and forge environments.
 */
public class PortProber {

  /**
   * Range of ports to try when trying to find a free port. To be consistent with the
   * /net/util/ports.cc, we use this to give us a range: 32768 - 60000
   */
  private static final int RANDOM_PORT_BASE = 32768;

  private static final int RANDOM_PORT_RANGE = 60000 - RANDOM_PORT_BASE + 1;

  /** Constant to specify an invalid port */
  private static final int INVALID_PORT = -1;

  private static final Random random = new SecureRandom();

  private static final ByteBuffer PORT_SERVER_REQUEST_BUFFER = ByteBuffer.allocateDirect(512);
  private static final String PORT_SERVER_PORT_ENV_VAR = "PORTSERVER_PORT";
  private static final Duration PORT_FREE_TIMEOUT = Duration.ofMillis(10);
  private static final int SOCKET_TIMEOUT_IN_MS = 5000;

  private static final int PORT_SERVER_PORT; // Set from environment variable PORTSERVER_PORT
  private static final String PID_STRING;

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /* Static initialization for PortServer. */
  static {
    int portServerPortSetting = 0;
    String pidStringSetting = null;

    String portServerPortString = System.getenv(PORT_SERVER_PORT_ENV_VAR);
    if (null != portServerPortString) {
      try {
        int port = Integer.parseInt(portServerPortString);
        if (port > 0 && port < 65536) {
          // Only set PID_STRING if we got a valid PortServer port.
          pidStringSetting = ProcessHandle.current().pid() + "\n";
          // Only set PORT_SERVER_PORT if we got a valid PID.
          portServerPortSetting = port;
        }
      } catch (NumberFormatException e) {
        logger.atSevere().withCause(e).log(
            "Could not parse port from portServerPort: %s", portServerPortString);
      }
    } else {
      logger.atInfo().log(
          "%s not set in process environment, so not using PortServer.", PORT_SERVER_PORT_ENV_VAR);
    }
    PORT_SERVER_PORT = portServerPortSetting;
    PID_STRING = pidStringSetting;
  }

  /** Different kinds of sockets that we try to create. */
  enum SocketType {
    UDP,
    TCP
  }

  /**
   * Checks to see if a port is currently free.
   *
   * @throws UnexpectedPortStateException if the port is busy
   */
  public static void checkLocalPortIsFree(int port) throws IOException {
    if (isPortOpen("localhost", port)) {
      throw new UnexpectedPortStateException("the local port is busy");
    }
  }

  /**
   * Verifies that a port closes within the specified timeout interval.
   *
   * @throws UnexpectedPortStateException if the port does not become free
   */
  private static void checkLocalPortBecomesFree(int port, Duration timeout)
      throws IOException, InterruptedException {
    if (isPortOpen("localhost", port)) {
      // give it some time to close
      Thread.sleep(timeout.toMillis());
      checkLocalPortIsFree(port);
    }
  }

  /**
   * Checks to see if a port is currently busy. If you want to block until a newly started server is
   * available,
   *
   * @throws UnexpectedPortStateException if the port is free
   */
  public static void checkLocalPortIsBusy(int port) throws IOException {
    if (!isPortOpen("localhost", port)) {
      throw new UnexpectedPortStateException("the local port is free");
    }
  }

  /**
   * Determines whether a port is open. A port is open if a service is currently receiving
   * connections on that port.
   *
   * @return {@code true} if the specified port is open (able to receive connections).
   */
  public static boolean isPortOpen(String host, int port) throws IOException {
    try {
      Socket socket = new Socket(host, port);
      socket.close();
      return true;
    } catch (ConnectException e) {
      assertStartsWith("Connection refused", e.getMessage());
      return false;
    } catch (SocketException e) {
      assertStartsWith("Connection reset by peer", e.getMessage());
      return false;
    }
  }

  private static void assertStartsWith(String expectedPrefix, String actual) {
    if (actual != null && actual.startsWith(expectedPrefix)) {
      return;
    }
    throw new AssertionError("[" + actual + "] didn't start with [" + expectedPrefix + "]");
  }

  /**
   * Find an unused port and return it. The method will try to find a port that we can bind to using
   * both UDP and TCP. Most of this code was cribbed from the c++ equivalent /net/util/ports.cc
   *
   * <p>When PORTSERVER_PORT is set in the current environment, will issue a TCP request over
   * localhost to a PortServer at that port to ensure we do not race with other tests for available
   * port numbers. Failure to allocate a port using this method will fall back on the previous
   * approach.
   *
   * @throws UnableToFindPortException if unable to find a free port
   */
  public static int pickUnusedPort() throws IOException, InterruptedException {
    return pickUnusedPort(RANDOM_PORT_BASE, RANDOM_PORT_RANGE);
  }

  /**
   * Find an unused port and return it, starting a search at the indicated port. The method will try
   * to find a port that we can bind to using both UDP and TCP. Most of this code was cribbed from
   * the c++ equivalent /net/util/ports.cc
   *
   * <p>When PORTSERVER_PORT is set in the current environment, will issue a TCP request over
   * localhost to a PortServer at that port to ensure we do not race with other tests for available
   * port numbers.
   *
   * @param startPort First port number to check when finding a free port. This is ignored when
   *     requesting a port from a port server.
   * @param portRangeLength Suggested size of the port range to search. If not running on Forge,
   *     select ports from the range [suggestedStartPort, suggestedStartPort + portRangeLength]. The
   *     range length must be non-negative.
   */
  public static int pickUnusedPort(int startPort, int portRangeLength) {
    if (PORT_SERVER_PORT != 0) {
      int port = pickPortFromPortServer();
      if (port > 0 && port < 65536) {
        return port;
      }
      throw new UnableToFindPortException("Couldn't get port from portserver.");
    }
    return scanForUnusedPort(startPort, portRangeLength);
  }

  private static int scanForUnusedPort(int minPort, int portRangeLength) {
    Preconditions.checkArgument(
        portRangeLength > 0, "Must specify a portRangeLength of 1 or greater");
    int maxPort = minPort + portRangeLength - 1;
    int startPort = getRandomStartingPort(minPort, portRangeLength);
    int foundPort = INVALID_PORT;
    SocketType portType = SocketType.TCP;

    // scan up from starting port.
    for (int currentPort = startPort;
        foundPort == INVALID_PORT && currentPort <= maxPort;
        currentPort++) {
      foundPort = checkPortAt(currentPort, portType);
      portType = togglePortType(portType);
    }

    // if no valid port found, start from beginning and scan up to starting port.
    for (int currentPort = minPort;
        foundPort == INVALID_PORT && currentPort < startPort;
        currentPort++) {
      foundPort = checkPortAt(currentPort, portType);
      portType = togglePortType(portType);
    }

    if (foundPort == INVALID_PORT) {
      logger.atInfo().log("No port found within requested range.  Picking system-provided port.");
      foundPort = checkPortAt(0, portType); // Have the system pick a port for us.
    }
    return foundPort;
  }

  private static int getRandomStartingPort(int minPort, int range) {
    synchronized (random) {
      return minPort + random.nextInt(range);
    }
  }

  /*
   * Alternating between {@link SocketType} can help avoid a performance drop
   * in the situation where the machine has been eating up ports of a single
   * type.
   *
   * For example, if UDP ports are scarce, checking TCP first always results in
   * a delay to notice that the corresponding UDP port is not available.
   */
  private static SocketType togglePortType(SocketType portType) {
    return (portType == SocketType.TCP) ? SocketType.UDP : SocketType.TCP;
  }

  /**
   * We are going to try to grab a port for the given type (e.g. tcp) first, and only if it
   * succeeds, will we try to grab the other type (e.g. udp).
   */
  private static int checkPortAt(int portToTry, SocketType portType) {
    int candidatePort = checkPortForListen(portToTry, portType);
    if (candidatePort != INVALID_PORT) {
      candidatePort = checkPortForListen(candidatePort, togglePortType(portType));
      if (candidatePort != INVALID_PORT) {
        try {
          checkLocalPortBecomesFree(candidatePort, PORT_FREE_TIMEOUT);
        } catch (IOException | InterruptedException e) {
          e.printStackTrace();
          candidatePort = INVALID_PORT;
        }
      }
    }
    return candidatePort;
  }

  private static synchronized int pickPortFromPortServer() {
    long start = System.currentTimeMillis();

    try {
      // Connect to local PortServer.
      SocketChannel channel = SocketChannel.open();
      InetSocketAddress address = new InetSocketAddress("localhost", PORT_SERVER_PORT);
      Socket socket = channel.socket();
      socket.setSoTimeout(SOCKET_TIMEOUT_IN_MS);
      socket.connect(address, SOCKET_TIMEOUT_IN_MS);
      return pickPortFromPortServerSocket(channel);

    } catch (UnknownHostException e) {
      logger.atSevere().withCause(e).log(
          "Unable to resolve PortServer at localhost:%d\n", PORT_SERVER_PORT);
      return 0;
    } catch (IOException e) {
      long duration = System.currentTimeMillis() - start;
      logger.atSevere().withCause(e).log(
          "Unable to connect to PortServer (waited %dms):\n", duration);
      return 0;
    }
  }

  private static synchronized int pickPortFromPortServerSocket(
      SocketChannel portServerSocketChannel) {
    Preconditions.checkNotNull(portServerSocketChannel);
    String portServerResponse = "";
    try {
      try {
        // Send port request to PortServer for this PID.
        PORT_SERVER_REQUEST_BUFFER.clear();
        PORT_SERVER_REQUEST_BUFFER.put(PID_STRING.getBytes(UTF_8));
        PORT_SERVER_REQUEST_BUFFER.flip();

        if (portServerSocketChannel.write(PORT_SERVER_REQUEST_BUFFER) <= 0) {
          logger.atSevere().log("Unexpected failure writing to PortServer buffer.");
          return 0;
        }

        // Perform blocking read for reply from PortServer.
        PORT_SERVER_REQUEST_BUFFER.clear();
        if (portServerSocketChannel.read(PORT_SERVER_REQUEST_BUFFER) <= 0) {
          logger.atSevere().log("Unexpected failure reading from PortServer buffer.");
          return 0;
        }

        // Parse and return port from reply.
        PORT_SERVER_REQUEST_BUFFER.flip();
        byte[] bytes =
            new byte[PORT_SERVER_REQUEST_BUFFER.limit() - PORT_SERVER_REQUEST_BUFFER.position()];
        PORT_SERVER_REQUEST_BUFFER.get(bytes);
        portServerResponse = new String(bytes, UTF_8).trim();
        int port = Integer.parseInt(portServerResponse);
        if (port > 0 && port < 65536) {
          return port;
        } else {
          logger.atWarning().log("Got invalid port number from PortServer: %d", port);
          return 0;
        }
      } finally {
        portServerSocketChannel.socket().close();
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Exception while communicating with PortServer.");
      return 0;
    } catch (NumberFormatException e) {
      logger.atSevere().withCause(e).log(
          "Got unexpected NumberFormatException while parsing PortServer response: %s",
          portServerResponse);
      return 0;
    }
  }

  /**
   * Try to open a port for listening using the specified type.
   *
   * @param port int specifying the port to use. May be 0 for ephemeral.
   * @param type SocketType specifying the type of socket to try to create.
   * @return port that we could successfully bind to or {@code INVALID_PORT} if we were unable to
   *     bind to the port requested, or we were unable to get an ephemeral port.
   */
  private static int checkPortForListen(int port, final SocketType type) {
    if (port < 0) {
      throw new IllegalArgumentException("Invalid port (<0) specified");
    }
    switch (type) {
      case TCP:
        try {
          ServerSocket socket = new ServerSocket(port);
          int realPort = socket.getLocalPort();
          socket.close();
          return realPort;
        } catch (IOException ioe) {
          return INVALID_PORT;
        }
      case UDP:
        try {
          DatagramSocket udpSocket = new DatagramSocket(port);
          int realPort = udpSocket.getLocalPort();
          udpSocket.close();
          return realPort;
        } catch (SocketException se) {
          return INVALID_PORT;
        }
    }
    throw new IllegalArgumentException("Invalid type specified: " + type);
  }

  private PortProber() {}
}
