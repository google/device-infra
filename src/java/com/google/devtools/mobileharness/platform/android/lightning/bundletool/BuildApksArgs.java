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

package com.google.devtools.mobileharness.platform.android.lightning.bundletool;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Arguments for {@code bundletool build-apks}.
 *
 * <p>See https://developer.android.com/tools/bundletool for detailed usage of each argument
 */
public record BuildApksArgs(
    Path bundle,
    Path output,
    OutputFormat outputFormat,
    boolean connectedDevice,
    String deviceId,
    boolean overwrite,
    Optional<Path> keystore,
    String keystorePassword,
    String keystoreKeyAlias,
    String keystoreKeyPassword,
    Optional<Path> lineage,
    Duration commandTimeout) {

  enum OutputFormat {
    APK_SET("apk_set"),
    DIRECTORY("directory");

    private final String flagValue;

    OutputFormat(String flagValue) {
      this.flagValue = flagValue;
    }

    String getFlagValue() {
      return flagValue;
    }
  }

  ImmutableList<String> toBundletoolCommand(String adbPath, String aaptPath) {
    ImmutableList.Builder<String> args =
        ImmutableList.<String>builder()
            .add("build-apks")
            .add("--adb=" + adbPath)
            .add("--aapt2=" + aaptPath)
            .add("--bundle=" + bundle)
            .add("--output=" + output)
            .add("--output-format=" + outputFormat.getFlagValue());
    if (connectedDevice) {
      args.add("--connected-device");
    }
    if (!deviceId.isEmpty()) {
      args.add("--device-id=" + deviceId);
    }
    if (overwrite) {
      args.add("--overwrite");
    }
    keystore.ifPresent(ks -> args.add("--ks=" + ks));
    if (!keystorePassword.isEmpty()) {
      args.add("--ks-pass=" + keystorePassword);
    }
    if (!keystoreKeyAlias.isEmpty()) {
      args.add("--ks-key-alias=" + keystoreKeyAlias);
    }
    if (!keystoreKeyPassword.isEmpty()) {
      args.add("--key-pass=" + keystoreKeyPassword);
    }
    lineage.ifPresent(ln -> args.add("--lineage=" + ln));
    return args.build();
  }

  public static Builder builder() {
    return new AutoBuilder_BuildApksArgs_Builder()
        .setOutputFormat(OutputFormat.APK_SET)
        .setConnectedDevice(false)
        .setDeviceId("")
        .setOverwrite(false)
        .setKeystorePassword("")
        .setKeystoreKeyAlias("")
        .setKeystoreKeyPassword("")
        .setCommandTimeout(Duration.ofMinutes(10));
  }

  /** Builder for {@link BuildApksArgs}. */
  @AutoBuilder
  public abstract static class Builder {

    /** The required Bundle/AAB file to build APKs for. */
    public abstract Builder setBundle(Path bundle);

    /**
     * The required output path for the APKs.
     *
     * <p>Depending on the output format, this will be a directory or a file.
     */
    public abstract Builder setOutput(Path output);

    /** Format of the output, either an Apk Set file or a directory (default APK_SET). */
    public abstract Builder setOutputFormat(OutputFormat outputFormat);

    /** Whether to build APKs optimized for a connected device (default false). */
    public abstract Builder setConnectedDevice(boolean connectedDevice);

    /** The device ID to build APKs for (default empty). */
    public abstract Builder setDeviceId(String deviceId);

    /** Whether to overwrite the output file or directory (default false). */
    public abstract Builder setOverwrite(boolean overwrite);

    /** Keystore file to sign the APKs with (default empty). */
    public abstract Builder setKeystore(Path keystore);

    /**
     * Password of the keystore file (default empty).
     *
     * <p>Must be in one of two formats:
     *
     * <ul>
     *   <li>"pass:<password>" - providing the password in plain text
     *   <li>"file:<password-file>" - providing the password in a file
     * </ul>
     *
     * <p>Required if {@code keystore} is set.
     */
    public abstract Builder setKeystorePassword(String keystorePassword);

    /**
     * Alias of the key to use in the keystore to sign the APKs (default empty).
     *
     * <p>Required if {@code keystore} is set.
     */
    public abstract Builder setKeystoreKeyAlias(String keystoreKeyAlias);

    /**
     * The password for the keystore key (default empty).
     *
     * <p>See {@link #setKeystorePassword(String)} for the format.
     *
     * <p>Required if {@code keystore} is set.
     */
    public abstract Builder setKeystoreKeyPassword(String keystoreKeyPassword);

    /** Binary file of SigningCertificateLineage (default absent). */
    public abstract Builder setLineage(Path lineage);

    /** Timeout for the command (default 10 minutes). */
    public abstract Builder setCommandTimeout(Duration commandTimeout);

    protected abstract BuildApksArgs autoBuild();

    public BuildApksArgs build() {
      BuildApksArgs args = autoBuild();
      if (args.keystore.isPresent()) {
        checkArgument(!args.keystorePassword.isEmpty(), "Keystore password cannot be empty.");
        checkArgument(!args.keystoreKeyAlias.isEmpty(), "Keystore key alias cannot be empty.");
        checkArgument(
            !args.keystoreKeyPassword.isEmpty(), "Keystore key password cannot be empty.");
      }
      return args;
    }
  }
}
