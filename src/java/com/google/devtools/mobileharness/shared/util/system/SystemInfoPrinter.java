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

package com.google.devtools.mobileharness.shared.util.system;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance;
import com.google.devtools.mobileharness.shared.constant.inject.Annotations.MainArgs;
import com.google.devtools.mobileharness.shared.constant.inject.Annotations.SystemEnvironment;
import com.google.devtools.mobileharness.shared.constant.inject.Annotations.SystemProperties;
import com.google.devtools.mobileharness.shared.util.logging.MobileHarnessLogFormatter;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.devtools.mobileharness.shared.version.Version;
import com.google.devtools.mobileharness.shared.version.VersionUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A utility class to print system information.
 *
 * <p>It is the single place which builds the host/process context block (hostname, PID, process
 * start time and uptime, versions, main args, memory, environment variables and system properties).
 * Java and OS information is not printed separately because it is already in system properties
 * (e.g., {@code java.version} and {@code os.name}). The block can be printed in two {@link Mode}s
 * which have the same lines and only differ in value filtering, so the two outputs can be compared
 * line by line. See {@link #getSystemInfo} for an example.
 *
 * <p>The process start time is formatted in the same format and time zone as the timestamp prefix
 * of each log line (see {@link MobileHarnessLogFormatter#getDateTimeFormatter()}), so it can be
 * compared with log lines directly. The process uptime (of the JVM) is formatted like "{@code 1h 2m
 * 3s}" by {@link TimeUtils#toReadableDurationString}.
 *
 * <p>It is expected to be called once per process start, session start or test start, rather than
 * in a hot path. The local hostname, whose resolution may block on DNS, is resolved at most once
 * per instance, and the class is a singleton so all callers in an injector share the same cache.
 */
@Singleton
public final class SystemInfoPrinter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Mode of printing system information. */
  public enum Mode {
    /**
     * Prints all values as is.
     *
     * <p>Use it for logs which stay on the host (e.g., the local server log at process start).
     */
    FULL,

    /**
     * Prints the same lines as {@link #FULL} but replaces potentially sensitive values with {@code
     * <redacted>}.
     *
     * <p>Use it for logs which may leave the host or be seen by someone other than the host owner
     * (e.g., OLC server session logs and lab-side test logs).
     */
    REDACTED,
  }

  @VisibleForTesting static final String REDACTED_VALUE = "<redacted>";

  /**
   * Environment variables whose values are printed in {@link Mode#REDACTED} mode.
   *
   * <p>Uses an allowlist rather than a denylist because partners can inject environment variables
   * with arbitrary names (e.g., via {@code extra_docker_args} / {@code mtt_docker_env}). Values of
   * allowlisted environment variables still have URL user info stripped (e.g., {@code HTTP_PROXY}
   * may contain {@code user:pass@}).
   */
  @VisibleForTesting
  static final ImmutableSet<String> ENVIRONMENT_VARIABLE_ALLOWLIST =
      ImmutableSet.of(
          "ANDROID_BUILD_TOP",
          "ANDROID_HOME",
          "ANDROID_HOST_OUT",
          "ANDROID_PRODUCT_OUT",
          "ANDROID_SDK_ROOT",
          "HOME",
          "HOSTNAME",
          "HTTPS_PROXY",
          "HTTP_PROXY",
          "JAVA_HOME",
          "LANG",
          "LC_ALL",
          "LD_LIBRARY_PATH",
          "NO_PROXY",
          "OLC_SERVER_EMBEDDED_MODE",
          "PATH",
          "PWD",
          "SHELL",
          "TERM",
          "TMPDIR",
          "TZ",
          "USER",
          "USE_NEW_OLC_SERVER",
          "USE_NEW_PUBLIC_DIR_FOR_NEW_OLC_SERVER",
          "USE_TF_RETRY",
          "http_proxy",
          "https_proxy",
          "no_proxy");

  /**
   * System properties whose values are printed in {@link Mode#REDACTED} mode.
   *
   * <p>Mostly for noise reduction (e.g., {@code java.class.path}).
   */
  @VisibleForTesting
  static final ImmutableSet<String> SYSTEM_PROPERTY_ALLOWLIST =
      ImmutableSet.of(
          "XTS_ROOT",
          "XTS_TYPE",
          "file.encoding",
          "java.home",
          "java.io.tmpdir",
          "java.runtime.version",
          "java.vendor",
          "java.version",
          "java.vm.name",
          "java.vm.version",
          "os.arch",
          "os.name",
          "os.version",
          "user.dir",
          "user.home",
          "user.name",
          "user.timezone");

  /**
   * Flag names (case-insensitive) containing any of these words have their values redacted in
   * {@link Mode#REDACTED} mode.
   *
   * <p>Uses a denylist because flag names are defined by us and are predictable.
   */
  private static final Pattern SENSITIVE_FLAG_NAME_PATTERN =
      Pattern.compile("password|passwd|secret|token|key|cred|psk", Pattern.CASE_INSENSITIVE);

  /** Matches the user info part (e.g., {@code user:pass@}) of a URL. */
  private static final Pattern URL_USER_INFO_PATTERN =
      Pattern.compile("([a-zA-Z][a-zA-Z0-9+.\\-]*://)[^/?#@\\s]+@");

  /** The instance registered by the main class of the current process, if any. */
  private static final AtomicReference<SystemInfoPrinter> processInstance = new AtomicReference<>();

  /**
   * Returns the instance registered by {@link #registerAsProcessInstance()}, if any.
   *
   * <p>For classes which are not created by Guice (e.g., builtin plugins).
   */
  public static Optional<SystemInfoPrinter> getProcessInstance() {
    return Optional.ofNullable(processInstance.get());
  }

  private final NetworkUtil networkUtil;
  private final SystemUtil systemUtil;
  private final ImmutableList<String> mainArgs;
  private final ImmutableMap<String, String> systemEnvironment;
  private final ImmutableMap<String, String> systemProperties;

  private final Object localHostNameLock = new Object();

  /** The cached local hostname, or {@code null} if it has not been resolved yet. */
  @GuardedBy("localHostNameLock")
  @Nullable
  private String localHostName;

  @Inject
  SystemInfoPrinter(
      NetworkUtil networkUtil,
      SystemUtil systemUtil,
      @MainArgs ImmutableList<String> mainArgs,
      @SystemEnvironment ImmutableMap<String, String> systemEnvironment,
      @SystemProperties ImmutableMap<String, String> systemProperties) {
    this.networkUtil = networkUtil;
    this.systemUtil = systemUtil;
    this.mainArgs = mainArgs;
    this.systemEnvironment = systemEnvironment;
    this.systemProperties = systemProperties;
  }

  /**
   * Registers this instance as the instance of the current process, so that classes which are not
   * created by Guice can get it by {@link #getProcessInstance()}.
   *
   * <p>Should be called by the main class of a process (e.g., OLC server, lab server, ATS console).
   */
  public void registerAsProcessInstance() {
    processInstance.set(this);
  }

  /**
   * Prints system information of the current process in the given mode.
   *
   * <p>See {@link #getSystemInfo} for an example of the output.
   */
  public void printSystemInfo(Importance importance, Mode mode) {
    logger.atInfo().with(IMPORTANCE, importance).log("%s", getSystemInfo(importance, mode));
  }

  /**
   * Returns system information of the current process in the given mode.
   *
   * <p>Example output in {@link Mode#REDACTED} mode (in {@link Mode#FULL} mode, the lines are the
   * same but no value is replaced with {@code <redacted>}):
   *
   * <pre>{@code
   * System info (mode=REDACTED):
   *   Hostname: lab-host-1
   *   PID: 12345
   *   Process start time: 2026-11-05 00:00:00:123 PST
   *   Process uptime: 1h 2m 3s
   *   OmniLab version: lab-4.389.0 cl/761411824 kokoro-1148 branch-main github-fece030c...
   *   Client version: 4.35.0
   *   Main args: [--public_dir=/tmp/public, --api_key=<redacted>]
   *   Memory: jvm_used=..., jvm_total=..., jvm_max=..., heap_used=..., free=..., used=..., ...
   *   Environment variables:
   *     HTTPS_PROXY=http://<redacted>@proxy:3128
   *     MY_SECRET_ENV=<redacted>
   *     PATH=/usr/bin:/bin
   *   System properties:
   *     java.class.path=<redacted>
   *     java.version=21.0.4
   *     os.name=Linux
   * }</pre>
   *
   * @param importance the importance of the warning log if the local hostname fails to be resolved
   */
  @VisibleForTesting
  String getSystemInfo(Importance importance, Mode mode) {
    boolean redacted = mode == Mode.REDACTED;

    StringBuilder result = new StringBuilder();
    result.append(String.format("System info (mode=%s):", mode));
    appendField(result, "Hostname", getLocalHostName(importance));
    appendField(result, "PID", ProcessHandle.current().pid());
    appendField(
        result,
        "Process start time",
        orNa(
            systemUtil
                .getProcessStartTime()
                .map(MobileHarnessLogFormatter.getDateTimeFormatter()::format)));
    appendField(
        result,
        "Process uptime",
        orNa(systemUtil.getProcessUptime().map(TimeUtils::toReadableDurationString)));
    appendField(result, "OmniLab version", VersionUtil.getVersionString());
    appendField(result, "Client version", Version.CLIENT_VERSION);
    appendField(result, "Main args", redacted ? redactArgs(mainArgs) : mainArgs);
    appendField(result, "Memory", systemUtil.getMemoryInfo());
    appendMap(
        result,
        "Environment variables",
        systemEnvironment,
        redacted ? SystemInfoPrinter::redactEnvironmentVariable : (name, value) -> value);
    appendMap(
        result,
        "System properties",
        systemProperties,
        redacted ? SystemInfoPrinter::redactSystemProperty : (name, value) -> value);
    return result.toString();
  }

  /**
   * Returns the local hostname, or "{@code n/a}" if it fails to be resolved.
   *
   * <p>{@link NetworkUtil#getLocalHostName()} may block on DNS resolution (e.g., when the hostname
   * is not in {@code /etc/hosts} and DNS is slow or unreachable), so the result (including the
   * failure) is resolved at most once and cached. Concurrent callers wait for the first resolution
   * rather than resolving again.
   */
  private String getLocalHostName(Importance importance) {
    synchronized (localHostNameLock) {
      if (localHostName == null) {
        localHostName = resolveLocalHostName(importance);
      }
      return localHostName;
    }
  }

  private String resolveLocalHostName(Importance importance) {
    try {
      return networkUtil.getLocalHostName();
    } catch (MobileHarnessException e) {
      logger
          .atWarning()
          .with(IMPORTANCE, importance)
          .withCause(e)
          .log("Failed to get local hostname");
      return "n/a";
    }
  }

  /**
   * Redacts values of flags whose names look sensitive, and strips URL user info in all args.
   *
   * <p>Supports both "{@code --flag=value}" and "{@code --flag value}". Examples:
   *
   * <ul>
   *   <li>{@code [--public_dir=/tmp/public]} stays the same
   *   <li>{@code [--api_key=abc]} becomes {@code [--api_key=<redacted>]}
   *   <li>{@code [--db_password, abc]} becomes {@code [--db_password, <redacted>]}
   *   <li>{@code [--proxy=http://u:p@proxy]} becomes {@code [--proxy=http://<redacted>@proxy]}
   * </ul>
   */
  @VisibleForTesting
  static ImmutableList<String> redactArgs(List<String> args) {
    ImmutableList.Builder<String> result = ImmutableList.builder();
    boolean redactNextValue = false;
    for (String arg : args) {
      boolean isFlag = arg.startsWith("-");
      if (redactNextValue && !isFlag) {
        result.add(REDACTED_VALUE);
        redactNextValue = false;
        continue;
      }
      redactNextValue = false;
      if (isFlag) {
        int equalSignIndex = arg.indexOf('=');
        String flagName = equalSignIndex == -1 ? arg : arg.substring(0, equalSignIndex);
        if (SENSITIVE_FLAG_NAME_PATTERN.matcher(flagName).find()) {
          if (equalSignIndex == -1) {
            result.add(arg);
            redactNextValue = true;
          } else {
            result.add(arg.substring(0, equalSignIndex + 1) + REDACTED_VALUE);
          }
          continue;
        }
      }
      result.add(stripUrlUserInfo(arg));
    }
    return result.build();
  }

  /**
   * Returns the value of an environment variable to print in {@link Mode#REDACTED} mode.
   *
   * <p>Values of environment variables in {@link #ENVIRONMENT_VARIABLE_ALLOWLIST} (case-sensitive)
   * are kept with URL user info stripped, and others are replaced with {@code <redacted>}.
   * Examples:
   *
   * <ul>
   *   <li>{@code PATH=/usr/bin} stays the same
   *   <li>{@code HTTPS_PROXY=http://u:p@proxy} becomes {@code HTTPS_PROXY=http://<redacted>@proxy}
   *   <li>{@code GCP_SA_KEY=abc} becomes {@code GCP_SA_KEY=<redacted>}
   * </ul>
   */
  @VisibleForTesting
  static String redactEnvironmentVariable(String name, String value) {
    return ENVIRONMENT_VARIABLE_ALLOWLIST.contains(name) ? stripUrlUserInfo(value) : REDACTED_VALUE;
  }

  /**
   * Returns the value of a system property to print in {@link Mode#REDACTED} mode.
   *
   * <p>Values of system properties in {@link #SYSTEM_PROPERTY_ALLOWLIST} are kept with URL user
   * info stripped, and others are replaced with {@code <redacted>}. Examples:
   *
   * <ul>
   *   <li>{@code os.name=Linux} stays the same
   *   <li>{@code java.class.path=/a.jar} becomes {@code java.class.path=<redacted>}
   * </ul>
   */
  @VisibleForTesting
  static String redactSystemProperty(String name, String value) {
    return SYSTEM_PROPERTY_ALLOWLIST.contains(name) ? stripUrlUserInfo(value) : REDACTED_VALUE;
  }

  /**
   * Replaces the user info part of all URLs in the value with {@code <redacted>}.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code http://host:80/path} stays the same
   *   <li>{@code http://user:pass@host} becomes {@code http://<redacted>@host}
   *   <li>{@code a=http://u@h,b=socks5://u:p@h} becomes {@code
   *       a=http://<redacted>@h,b=socks5://<redacted>@h}
   * </ul>
   */
  @VisibleForTesting
  static String stripUrlUserInfo(String value) {
    return URL_USER_INFO_PATTERN.matcher(value).replaceAll("$1" + REDACTED_VALUE + "@");
  }

  private static void appendField(StringBuilder result, String name, Object value) {
    result.append("\n  ").append(name).append(": ").append(value);
  }

  private static String orNa(Optional<?> value) {
    return value.map(Object::toString).orElse("n/a");
  }

  private static void appendMap(
      StringBuilder result,
      String name,
      Map<String, String> map,
      BiFunction<String, String, String> valueMapper) {
    result.append("\n  ").append(name).append(':');
    ImmutableSortedMap.copyOf(map)
        .forEach(
            (key, value) ->
                result
                    .append("\n    ")
                    .append(key)
                    .append('=')
                    .append(valueMapper.apply(key, value)));
  }
}
