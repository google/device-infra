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
 * Copyright 2026 Google LLC
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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutionException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Decorator to simulate network conditions (bandwidth throttling, packet delay, and packet loss) on
 * the DUT downlink interface using tcconfig (tcset / tcdel).
 */
@DecoratorAnnotation(
    help =
        "Throttles and simulates network conditions on the DUT downlink interface using"
            + " tcset/tcdel.")
public class NetworkSimulationDecorator extends LifecycleDecorator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(
      required = false,
      help = "Bandwidth throttling rate (e.g. '4Mbps', '1.5Mbps', '800Kbps').")
  public static final String PARAM_NET_RATE = "net_rate";

  @ParamAnnotation(
      required = false,
      help = "Baseline network latency to inject (e.g. '300ms', '100ms').")
  public static final String PARAM_NET_DELAY = "net_delay";

  @ParamAnnotation(
      required = false,
      help = "Latency jitter / distribution variation (e.g. '60ms', '10ms').")
  public static final String PARAM_NET_DELAY_DISTRO = "net_delay_distro";

  @ParamAnnotation(
      required = false,
      help = "Packet loss percentage (e.g. '0.5%', '2%'). Must not exceed 100%.")
  public static final String PARAM_NET_LOSS = "net_loss";

  @ParamAnnotation(
      required = false,
      help = "Network interface to shape. Defaults to 'eth1' (Raspberry Pi 5 DUT downlink).")
  public static final String PARAM_NET_IFACE = "net_iface";

  @ParamAnnotation(
      required = false,
      help = "Traffic direction to shape: 'outgoing' (host to DUT, default) or 'incoming'.")
  public static final String PARAM_NET_DIRECTION = "net_direction";

  @ParamAnnotation(
      required = false,
      help =
          "Network IP/subnet to exclude from traffic control to protect control-plane (ADB,"
              + " DevTools) traffic. Defaults to auto-detecting host IP on the interface,"
              + " or 'none'/'false' to disable.")
  public static final String PARAM_NET_EXCLUDE_NETWORK = "net_exclude_network";

  public static final String DEFAULT_INTERFACE = "eth1";
  public static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
  public static final Duration ADB_CONNECT_TIMEOUT = Duration.ofSeconds(10);
  public static final String TCSET_BIN = "/usr/local/bin/tcset";
  public static final String TCDEL_BIN = "/usr/local/bin/tcdel";

  /** Declarative mapping of Mobile Harness job parameters to tcset CLI flags. */
  private static final ImmutableMap<String, String> SHAPING_PARAM_FLAGS =
      ImmutableMap.<String, String>builder()
          .put(PARAM_NET_RATE, "--rate")
          .put(PARAM_NET_DELAY, "--delay")
          .put(PARAM_NET_DELAY_DISTRO, "--delay-distro")
          .put(PARAM_NET_LOSS, "--loss")
          .buildOrThrow();

  private final CommandExecutor commandExecutor;
  private final NetUtil netUtil;
  private final AndroidAdbUtil androidAdbUtil;

  /** Standard framework constructor. Required by the lab server framework. */
  public NetworkSimulationDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(decoratedDriver, testInfo, new CommandExecutor(), new NetUtil(), new AndroidAdbUtil());
  }

  /** Constructor for dependency injection and testing. */
  @Inject
  @VisibleForTesting
  NetworkSimulationDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      CommandExecutor commandExecutor,
      NetUtil netUtil,
      AndroidAdbUtil androidAdbUtil) {
    super(decoratedDriver, testInfo);
    this.commandExecutor = commandExecutor;
    this.netUtil = netUtil;
    this.androidAdbUtil = androidAdbUtil;
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    Params params = testInfo.jobInfo().params();

    String iface = sanitizeParam(params.get(PARAM_NET_IFACE)).orElse(DEFAULT_INTERFACE);

    List<String> shapingArgs = new ArrayList<>();
    for (Map.Entry<String, String> entry : SHAPING_PARAM_FLAGS.entrySet()) {
      Optional<String> sanitizedVal = sanitizeParam(params.get(entry.getKey()));
      if (sanitizedVal.isPresent()) {
        String val = sanitizedVal.get();
        if (val.startsWith("-")) {
          throw new MobileHarnessException(
              BasicErrorId.JOB_PARAM_VALUE_FORMAT_ERROR,
              String.format(
                  "Invalid negative value for network simulation parameter '%s': '%s'."
                      + " Network simulation parameters cannot be negative.",
                  entry.getKey(), val));
        }
        if (entry.getKey().equals(PARAM_NET_LOSS)) {
          validateLossParam(val);
        }
        shapingArgs.add(entry.getValue());
        shapingArgs.add(val);
      }
    }

    if (shapingArgs.isEmpty()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "No valid network simulation parameters provided (%s). Skipping tcset.",
              String.join(", ", SHAPING_PARAM_FLAGS.keySet()));
      return SetupResult.continueDecorated();
    }

    List<String> cmd = new ArrayList<>();
    cmd.add("sudo");
    cmd.add(TCSET_BIN);
    cmd.add(iface);
    cmd.add("--overwrite");
    cmd.addAll(shapingArgs);

    Optional<String> direction = sanitizeParam(params.get(PARAM_NET_DIRECTION));
    boolean isIncoming =
        direction.isPresent() && Ascii.equalsIgnoreCase("incoming", direction.get());
    if (direction.isPresent()) {
      cmd.add("--direction");
      cmd.add(direction.get().toLowerCase(Locale.ROOT));
    }

    // Exclude host IP from network simulation to protect control-plane traffic (ADB, DevTools,
    // ICMP ping) while throttling internet usage.
    Optional<String> excludeNetwork = resolveExcludeNetwork(iface, params);
    if (excludeNetwork.isPresent()) {
      // For outgoing traffic (host -> DUT), host is sender: use --exclude-src-network.
      // For incoming traffic (DUT -> host), host is receiver: use --exclude-dst-network.
      String excludeFlag = isIncoming ? "--exclude-dst-network" : "--exclude-src-network";
      cmd.add(excludeFlag);
      cmd.add(excludeNetwork.get());
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Excluding host network %s (%s) from network simulation to protect control-plane"
                  + " traffic.",
              excludeNetwork.get(), excludeFlag);
    }

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Applying network simulation on interface %s: %s", iface, String.join(" ", cmd));
    try {
      String output = commandExecutor.run(Command.of(cmd).timeout(COMMAND_TIMEOUT));
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Network simulation applied successfully: %s", output);
    } catch (CommandException e) {
      String diagnostic = extractCommandError(e);
      throw new MobileHarnessException(
          BasicErrorId.COMMAND_EXEC_FAIL,
          String.format(
              "Failed to apply network simulation on interface %s with command [%s]: %s",
              iface, String.join(" ", cmd), diagnostic),
          e);
    }

    return SetupResult.continueDecorated();
  }

  @Override
  protected void tearDown(TeardownContext context) {
    TestInfo testInfo = context.testInfo();
    String iface =
        sanitizeParam(testInfo.jobInfo().params().get(PARAM_NET_IFACE)).orElse(DEFAULT_INTERFACE);

    // 1. Unconditionally reset traffic shaping rules on the interface.
    try {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Resetting network simulation on interface %s: sudo %s %s --all",
              iface, TCDEL_BIN, iface);
      Command tcdelCommand =
          Command.of("sudo", TCDEL_BIN, iface, "--all")
              .timeout(COMMAND_TIMEOUT)
              .successExitCodes(0, 1, 2);
      String output = commandExecutor.run(tcdelCommand);
      testInfo.log().atInfo().alsoTo(logger).log("Network simulation reset output: %s", output);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "Interrupted while resetting network simulation on interface %s: %s. Continuing"
                  + " teardown.",
              iface, e.getMessage());
    } catch (Exception e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "Failed to reset network simulation on interface %s: %s. Continuing teardown.",
              iface, e.getMessage());
    }

    // 2. Proactively re-establish ADB connection for network-connected Android DUTs (e.g.
    // 192.168.1.2:5555) in case test failure recovery, reboot, or severe packet loss caused the
    // device to disconnect. Non-Android DUTs (Roku, RDK, DIAL) do not use ADB and are skipped.
    try {
      if (isAndroidDevice()) {
        String deviceId = getDevice().getDeviceId();
        if (deviceId != null && deviceId.contains(":")) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Verifying network ADB connection for physical Android DUT: %s", deviceId);
          androidAdbUtil.connect(deviceId, ADB_CONNECT_TIMEOUT);
          testInfo.log().atInfo().alsoTo(logger).log("ADB verified/reconnected for: %s", deviceId);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "Interrupted while verifying ADB connection in teardown: %s. Continuing teardown.",
              e.getMessage());
    } catch (Exception e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "Failed to verify ADB connection in teardown: %s. Continuing teardown.",
              e.getMessage());
    }
  }

  /**
   * Resolves the network/IP to exclude from traffic shaping.
   *
   * <p>Returns the explicit network if specified, empty if disabled ("none"/"false"), or the
   * auto-detected interface IPv4 address by default.
   */
  private Optional<String> resolveExcludeNetwork(String iface, Params params) {
    Optional<String> explicitExcludeNet = sanitizeParam(params.get(PARAM_NET_EXCLUDE_NETWORK));
    if (explicitExcludeNet.isPresent()) {
      String netVal = explicitExcludeNet.get();
      if (Ascii.equalsIgnoreCase("none", netVal) || Ascii.equalsIgnoreCase("false", netVal)) {
        return Optional.empty();
      }
      if (!Ascii.equalsIgnoreCase("auto", netVal)) {
        return Optional.of(netVal);
      }
    }
    return getInterfaceIpv4(iface);
  }

  /**
   * Queries {@link NetUtil} for the primary non-loopback IPv4 address of the specified interface.
   */
  private Optional<String> getInterfaceIpv4(String ifaceName) {
    try {
      return netUtil.getNetworkInterfaceAndAddress().orElse(ImmutableList.of()).stream()
          .filter(info -> info.name().equals(ifaceName))
          .flatMap(info -> info.ips().stream())
          .findFirst()
          .map(InetAddress::getHostAddress);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to query network interface '%s' for IPv4 address via NetUtil", ifaceName);
      return Optional.empty();
    }
  }

  /** Validates that packet loss does not exceed 100%. */
  @VisibleForTesting
  static void validateLossParam(String rawLoss) throws MobileHarnessException {
    String lossStr = CharMatcher.whitespace().or(CharMatcher.anyOf("%")).removeFrom(rawLoss);
    try {
      double loss = Double.parseDouble(lossStr);
      if (loss > 100.0) {
        throw new MobileHarnessException(
            BasicErrorId.JOB_PARAM_VALUE_FORMAT_ERROR,
            String.format(
                "Invalid loss value for network simulation parameter '%s': '%s'."
                    + " Packet loss percentage cannot exceed 100%%.",
                PARAM_NET_LOSS, rawLoss));
      }
    } catch (NumberFormatException ignored) {
      // Let tcset validate and reject malformed non-numeric formats.
    }
  }

  /**
   * Sanitizes a string parameter by trimming whitespace and surrounding quotation marks. Returns
   * empty if the resulting string is blank or the input was null.
   */
  private static Optional<String> sanitizeParam(@Nullable String param) {
    return Optional.ofNullable(param)
        .map(CharMatcher.whitespace().or(CharMatcher.anyOf("\"'"))::trimFrom)
        .filter(s -> !s.isEmpty());
  }

  /**
   * Generically extracts diagnostic error output from command exceptions across all failure types
   * (e.g. invalid rates, negative values, out-of-range loss, nonexistent interfaces).
   */
  private static String extractCommandError(CommandException e) {
    if (e instanceof CommandExecutionException commandExecutionException) {
      CommandResult result = commandExecutionException.result();
      String stderr = result.stderr().trim();
      if (!stderr.isEmpty()) {
        return stderr;
      }
      String stdout = result.stdout().trim();
      if (!stdout.isEmpty()) {
        return stdout;
      }
    }
    return e.getMessage();
  }

  /** Returns true if the decorated device is an Android device. */
  private boolean isAndroidDevice() {
    Device device = getDevice();
    if (device == null) {
      return false;
    }
    if (device instanceof AndroidDevice) {
      return true;
    }
    Set<String> deviceTypes = device.getDeviceTypes();
    return deviceTypes != null && deviceTypes.contains("AndroidDevice");
  }
}
