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

package com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.initializer;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.sdktool.proto.Adb.AdbParam;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.Optional;

/**
 * A template defines the adb initialization steps.
 *
 * <p>Sub-classes can or need to override some methods to handle the adb initialization, or its
 * default implementation will be used.
 */
public abstract class AdbInitializeTemplate {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** List of private and public adb key names. */
  protected static final ImmutableList<String> ADB_KEY_NAMES =
      ImmutableList.of("adbkey", "adbkey.pub");

  private static final int DEFAULT_ANDROID_ADB_SERVER_PORT = 5037;

  private static final String DEFAULT_ADB_SERVER_HOST = "localhost";

  /** Initializes ADB environment and returns {@link AdbParam} storing the info. */
  public AdbParam initializeAdb() {
    Optional<AdbParam> adbParamOptional = getAdbParamFromRemoteIfNeeded();
    if (adbParamOptional.isPresent()) {
      return adbParamOptional.get();
    }

    String adbKeyPath = getAdbKeyPath();
    if (!Strings.isNullOrEmpty(adbKeyPath)) {
      logger.atInfo().log("Android adb_key path: %s", adbKeyPath);
    }

    prepareHomeDirAdbKeyFiles();

    String stockAdbPath = getStockAdbPath();
    String adbPath = getAdbPath(stockAdbPath);

    manageAdbServer(adbPath, stockAdbPath);

    int adbServerPort = getAdbServerPort();

    String adbServerHost = getAdbServerHost();

    boolean enableAdbLibusb = ifEnableAdbLibusb();

    logger.atInfo().log(
        "Android SDK tool paths:\n"
            + "- adb: %s\n"
            + "- stock adb: %s\n"
            + "- adb server port: %d\n"
            + "- adb server host: %s",
        adbPath, stockAdbPath, adbServerPort, adbServerHost);

    ImmutableMap<String, String> adbCommandEnvVars =
        getAdbCommandEnvVars(
            stockAdbPath, adbKeyPath, enableAdbLibusb, adbServerPort, adbServerHost);

    logger.atInfo().log(
        "ADB Command Environment Variables:\n - %s",
        Joiner.on("\n - ")
            .join(
                adbCommandEnvVars.entrySet().stream()
                    .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
                    .iterator()));

    return AdbParam.newBuilder()
        .setAdbPath(adbPath)
        .setStockAdbPath(stockAdbPath)
        .setAdbKeyPath(adbKeyPath)
        .setAdbServerPort(adbServerPort)
        .setAdbServerHost(adbServerHost)
        .setAdbLibusb(enableAdbLibusb)
        .putAllCmdBaseEnvVars(adbCommandEnvVars)
        .build();
  }

  /**
   * Gets the adb param from a service.
   *
   * <p>This retrieved adb param will be returned to the caller immediately. If no adb param
   * returned, it will continue the rest of part of retrieving the adb param, see {@link
   * #initializeAdb()} for more details. By default, it will do nothing.
   */
  protected Optional<AdbParam> getAdbParamFromRemoteIfNeeded() {
    return Optional.empty();
  }

  /** Prepares adb key files in the directory "$HOME/.android". */
  protected abstract void prepareHomeDirAdbKeyFiles();

  /**
   * Gets the adb key path which will be passed to adb command environment variable
   * "ADB_VENDOR_KEYS".
   */
  protected String getAdbKeyPath() {
    String keyPathsFromUser = Flags.instance().adbKeyPathsFromUser.getNonNull();
    if (!keyPathsFromUser.isEmpty() && !keyPathsFromUser.endsWith(":")) {
      keyPathsFromUser += ":";
    }
    return keyPathsFromUser;
  }

  /**
   * Gets the stock adb path.
   *
   * <p>User given adb and the built-in adb are considered as stock adb.
   */
  protected abstract String getStockAdbPath();

  /**
   * Gets the adb path.
   *
   * <p>By default it's same as the stock adb path.
   */
  protected String getAdbPath(String stockAdbPath) {
    return stockAdbPath;
  }

  /** Checks whether it needs to kill the existing adb server in the adb initialization process. */
  protected abstract boolean ifKillAdbServer();

  /** The step to manage the adb server in the adb initialization process. */
  protected abstract void manageAdbServer(String adbPath, String stockAdbPath);

  /** Gets the adb server port being used along with the adb. */
  protected int getAdbServerPort() {
    return DEFAULT_ANDROID_ADB_SERVER_PORT;
  }

  /** Gets the adb server host being used along with the adb. */
  protected String getAdbServerHost() {
    return DEFAULT_ADB_SERVER_HOST;
  }

  /** Whether to start the adb server with flag ADB_LIBUSB=1. */
  protected boolean ifEnableAdbLibusb() {
    return Flags.instance().adbLibusb.getNonNull();
  }

  /** Gets the command environment variables set in the adb command executor. */
  protected ImmutableMap<String, String> getAdbCommandEnvVars(
      String stockAdbPath,
      String adbKeyPath,
      boolean enableAdbLibusb,
      int adbServerPort,
      String adbServerHost) {
    ImmutableMap.Builder<String, String> commandEnvVars = ImmutableMap.builder();

    commandEnvVars.put("ANDROID_ADB", stockAdbPath);

    if (!adbKeyPath.isEmpty()) {
      commandEnvVars.put("ADB_VENDOR_KEYS", adbKeyPath);
    }

    commandEnvVars.put("ADB_LIBUSB", enableAdbLibusb ? "1" : "0");

    return commandEnvVars.buildOrThrow();
  }

  /** Gets the value of flag {@link Flags#adbPathFromUser}. */
  protected String getAdbPathFromUser() {
    return Flags.instance().adbPathFromUser.getNonNull();
  }

  /** Gets the value of flag {@link Flags#adbKeyPathsFromUser}. */
  protected String getAdbKeyPathsFromUser() {
    return Flags.instance().adbKeyPathsFromUser.getNonNull();
  }

  /** Gets the value of flag {@link Flags#adbForceKillServer}. */
  protected boolean getAdbForceKillServer() {
    return Flags.instance().adbForceKillServer.getNonNull();
  }

  /** Gets the value of flag {@link Flags#adbDontKillServer}. */
  protected boolean getAdbDontKillServer() {
    return Flags.instance().adbDontKillServer.getNonNull();
  }
}
