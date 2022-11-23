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

package com.google.devtools.mobileharness.shared.util.network.localhost;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Branch of com.google.net.base.LocalHost. */
public class LocalHost {
  private static String localHostName_ = null;
  private static String canonicalLocalHostName_ = null;
  private static boolean isInitialized_ = false;

  private static final Logger logger = Logger.getLogger(LocalHost.class.getName());
  private static final HostsCache hostsCache = new HostsCache("/etc/hosts");

  private static synchronized void initialize() {
    if (!isInitialized_) {
      InetAddress addr;
      try {
        addr = getAddress();
      } catch (UnknownHostException e) {
        addr = null;
      }
      initializeWithAddress(addr);
    }
  }

  private static synchronized void initializeWithAddress(@Nullable InetAddress addr) {
    String name;
    if (addr == null) {
      name = "unknown";
    } else {
      // Get the locally-configured hostname.
      name = addr.getHostName().toLowerCase();
      // If it's not fully qualified, try reverse DNS instead.
      if (firstDot(name) < 0) {
        name = addr.getCanonicalHostName().toLowerCase();
      }
    }

    final int dot = firstDot(name);
    if (dot >= 0) {
      // Store the FQDN (foo.hoo.google.com) and its truncated form (foo).
      canonicalLocalHostName_ = name;
      localHostName_ = name.substring(0, dot);
    } else {
      // We have a plain hostname or IP literal; just store it as-is.
      canonicalLocalHostName_ = localHostName_ = name;
    }
    isInitialized_ = true;
  }

  private static int firstDot(String fqdn) {
    return InetAddresses.isInetAddress(fqdn) ? -1 : fqdn.indexOf('.');
  }

  /**
   * Returns an IP address (and the Java-provided hostname) of the local machine.
   *
   * <p>The IPv4/IPv6 bias is typically defined by getaddrinfo(), unless the default resolver is
   * changed via the -Dsun.net.spi.nameservice.provider.1 flag. We should generally aim to configure
   * our systems such that the JVM returns a sane choice.
   *
   * @return the address of the local host
   * @throws UnknownHostException if the local host could not be resolved into an address
   */
  public static InetAddress getAddress() throws UnknownHostException {
    InetAddress localhost = InetAddress.getLocalHost();

    // If we got a loopback address, look for an address using the network cards.
    // This is a workaround for http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4665037,
    // which could occur if a machine's hostname is mapped to 127.x.x.x in /etc/hosts.
    // This hasn't been extended to support IPv6, because it would be more productive
    // to fix the machines that are in this state.
    if (localhost.isLoopbackAddress()) {
      try {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) {
          return localhost;
        }
        while (interfaces.hasMoreElements()) {
          NetworkInterface networkInterface = interfaces.nextElement();
          Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();

          while (addresses.hasMoreElements()) {
            InetAddress address = addresses.nextElement();

            if (isValidLocalHostAddress(address)) {
              // Insert our hostname, because NetworkInterface doesn't provide one.
              return InetAddress.getByAddress(localhost.getHostName(), address.getAddress());
            }
          }
        }
      } catch (SocketException e) {
        // fall-through
      }
    }

    return localhost;
  }

  private static boolean isValidLocalHostAddress(InetAddress address) {
    return !(address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address instanceof Inet6Address);
  }

  /**
   * Returns the IP addresses of this machine, as defined by the /etc/hosts file.
   *
   * <p>This preserves the order of addresses in the hosts file, since we have other components
   * (e.g. localip.go) expecting the preferred address to appear first.
   *
   * <p>If the hosts file is not usable, this falls back to getAddress().
   *
   * @return a list of one or more local IPv4/IPv6 addresses
   * @throws UnknownHostException if the local host could not be resolved into an address
   */
  public static ImmutableList<InetAddress> getAddresses() throws UnknownHostException {
    return hostsCache.getAddresses();
  }

  /** The internal implementation of getAddresses(), with caching. */
  private static class HostsCache {
    private final String fileName;
    private File file = null;
    private String hostname = null;
    private long cacheLastModified = 0;
    private ImmutableList<InetAddress> cacheResult;

    HostsCache(String fileName) {
      this.fileName = fileName;
    }

    synchronized ImmutableList<InetAddress> getAddresses() throws UnknownHostException {
      // Construct the File on first use, to avoid doing anything
      // potentially-expensive from the static initializer.
      if (file == null) {
        file = new File(fileName);
      }

      long lastModified;
      // First, check for a cached result.
      // If lastModified() returns 0, then the cache is considered invalid.
      try {
        lastModified = file.lastModified();
      } catch (AccessControlException e) {
        // If reading the file is expressly prohibited (e.g. on App Engine),
        // then fall back to one address without spamming the logs.
        return ImmutableList.of(getAddress());
      }
      if (cacheLastModified != 0 && cacheLastModified == lastModified) {
        return cacheResult;
      }
      // InetAddress#getHostName() incurs a DNS reverse lookup, only do this on the first run.
      if (hostname == null) {
        hostname = InetAddress.getLocalHost().getHostName();
      }
      final List<InetAddress> result = new ArrayList<>();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8))) {
        final Splitter fieldSplitter = Splitter.on(CharMatcher.whitespace()).omitEmptyStrings();
        String line;
        while ((line = reader.readLine()) != null) {
          final int commentIndex = line.indexOf('#');
          if (commentIndex >= 0) {
            line = line.substring(0, commentIndex);
          }
          String ipString = null;
          for (String field : fieldSplitter.split(line)) {
            if (ipString == null) {
              // The first field should be an IP address.
              ipString = field;
            } else if (field.equals(hostname)) {
              // One of the remaining fields matches my hostname.
              InetAddress ip;
              try {
                ip = InetAddresses.forString(ipString);
              } catch (IllegalArgumentException e) {
                ip = null;
              }
              if (ip != null && !ip.isLoopbackAddress()) {
                result.add(ip);
              }
            }
          }
        }
      } catch (IOException e) {
        logger.log(Level.WARNING, "Error reading the hosts file: " + file, e);
        result.clear();
      }

      if (result.isEmpty()) {
        // The hosts file was not sanely populated, so fall back to the
        // one-address implementation.  If this throws UnknownHostException,
        // we just won't cache anything.
        logger.warning("Falling back to getAddress()");
        result.add(getAddress());
      }

      // Cache a copy of this result.
      cacheLastModified = lastModified;
      cacheResult = ImmutableList.copyOf(result);
      return cacheResult;
    }
  }

  /** Returns the short name for local host in all lower case, e.g. coyote */
  public static String getHostName() {
    initialize();
    return localHostName_;
  }

  /**
   * Returns the fully qualified domain name for local host in all lower case, e.g.
   * foo.hoo.google.com
   */
  public static String getCanonicalHostName() {
    initialize();
    return canonicalLocalHostName_;
  }
}
