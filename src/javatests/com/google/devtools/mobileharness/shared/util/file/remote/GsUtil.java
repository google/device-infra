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

package com.google.devtools.mobileharness.shared.util.file.remote;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.remote.GcsUtil.GcsParams;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A tool to test GcsUtil. */
final class GsUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static enum Command {
    LIST("list"),
    DOWNLOAD("download"),
    UPLOAD("upload"),
    DELETE("delete");

    private final String command;

    Command(String command) {
      this.command = command;
    }

    /** Returns the Command of the given command string if it is valid or null otherwise. */
    public static Command of(String command) {
      for (Command c : Command.values()) {
        if (c.command.equals(command)) {
          return c;
        }
      }
      return null;
    }
  }

  /** A wrapper of GcsParams with a builder to help parsing flags. */
  @AutoValue
  abstract static class GcsClient {

    abstract String bucketName();

    abstract String credentialFile();

    @Memoized
    GcsParams gcsParams() {
      return new GcsParams(bucketName(), credentialFile(), GcsParams.Scope.READ_WRITE);
    }

    static Builder builder() {
      return new AutoValue_GsUtil_GcsClient.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setBucketName(String bucketName);

      abstract Builder setCredentialFile(String credentialFile);

      abstract GcsClient build();
    }
  }

  private static final class FlagParser {
    private GcsClient gcsClient;
    private Command command;
    private final List<String> commandOptions = new ArrayList<>();

    void parse(String[] args) {
      ArrayList<String> leftOvers = new ArrayList<>(Arrays.asList(args));
      command = Command.of(leftOvers.remove(0));
      GcsClient.Builder builder = GcsClient.builder();
      while (!leftOvers.isEmpty()) {
        String arg = leftOvers.remove(0);
        if (arg.startsWith("--")) {
          if (arg.startsWith("--bucket")) {
            builder.setBucketName(Iterables.get(Splitter.on('=').split(arg), 1));
          } else if (arg.startsWith("--credential")) {
            builder.setCredentialFile(Iterables.get(Splitter.on('=').split(arg), 1));
          }
        } else {
          commandOptions.add(arg);
        }
      }
      gcsClient = builder.build();
    }

    Command getCommand() {
      return command;
    }

    List<String> getCommandOptions() {
      return commandOptions;
    }

    GsUtil getGsUtil() throws MobileHarnessException {
      GcsParams gcsParams = gcsClient.gcsParams();
      logger.atInfo().log(
          "gcsParams: %s, %s, %s",
          gcsParams.applicationName, gcsParams.bucketName, gcsParams.cloudStorageConfigPath);
      return new GsUtil(new GcsUtil(gcsParams));
    }
  }

  private GcsUtil gcsUtil;

  private GsUtil(GcsUtil gcsUtil) {
    this.gcsUtil = gcsUtil;
  }

  private void list(String prefix) throws MobileHarnessException {
    List<String> files = gcsUtil.listFiles(prefix);
    for (String file : files) {
      logger.atInfo().log("file: %s", file);
    }
  }

  private void download(String gcsObjectPath, String localPath)
      throws MobileHarnessException, InterruptedException {
    if (gcsUtil.fileExist(Path.of(gcsObjectPath))) {
      gcsUtil.copyFileToLocal(Path.of(gcsObjectPath), Path.of(localPath));
    }
  }

  private void upload(String localPath, String gcsObjectPath)
      throws MobileHarnessException, InterruptedException {
    gcsUtil.copyFileToCloud(Path.of(localPath), Path.of(gcsObjectPath));
  }

  void delete(String gcsObjectPath) throws MobileHarnessException {
    gcsUtil.deleteCloudFile(gcsObjectPath);
  }

  /**
   * Usage: gs_util command --bucket=bucket_name> --credential=<credential_file> [args]...
   *
   * <p>command: list. download. upload. delete.
   *
   * <p>args: list: prefix. download: gcs_object_path local_path. upload: local_path
   * gcs_object_path. delete: gcs_object_path.
   */
  public static void main(String[] args) throws MobileHarnessException, InterruptedException {
    FlagParser flagParser = new FlagParser();
    flagParser.parse(args);
    GsUtil gsUtil = flagParser.getGsUtil();
    switch (flagParser.getCommand()) {
      case LIST:
        gsUtil.list(flagParser.getCommandOptions().get(0));
        return;
      case DOWNLOAD:
        gsUtil.download(
            flagParser.getCommandOptions().get(0), flagParser.getCommandOptions().get(1));
        return;
      case UPLOAD:
        gsUtil.upload(flagParser.getCommandOptions().get(0), flagParser.getCommandOptions().get(1));
        return;
      case DELETE:
        gsUtil.delete(flagParser.getCommandOptions().get(0));
        return;
    }
  }
}
