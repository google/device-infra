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

package com.google.devtools.mobileharness.platform.android.xts.runtime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.xts.runtime.proto.RuntimeInfoProto.XtsTradefedRuntimeInfo;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/** A file utility to read/write {@code XtsTradefedRuntimeInfo} for XTS Tradefed tests. */
public class XtsTradefedRuntimeInfoFileUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final TextFormat.Printer PRINTER = TextFormat.printer();
  private static final TextFormat.Parser PARSER =
      TextFormat.Parser.newBuilder()
          .setAllowUnknownFields(true)
          .setAllowUnknownExtensions(true)
          .build();

  public void writeInfo(Path infoPath, XtsTradefedRuntimeInfo info) throws IOException {
    Path lockFilePath = prepareLockFile(infoPath);

    logger.atInfo().log("Acquiring file lock to write XtsTradefedRuntimeInfo to %s", infoPath);
    try (FileChannel lockFile = new FileOutputStream(lockFilePath.toString()).getChannel();
        FileLock ignored = lockFile.lock()) {
      Files.writeString(infoPath, PRINTER.printToString(info), UTF_8, CREATE, TRUNCATE_EXISTING);
    } catch (IOException e) {
      logger.atWarning().log("Failed to write XtsTradefedRuntimeInfo to %s", infoPath);
      throw e;
    }
  }

  public XtsTradefedRuntimeInfo readInfo(Path infoPath) throws IOException {
    Path lockFilePath = prepareLockFile(infoPath);

    logger.atInfo().log("Acquiring file lock to read XtsTradefedRuntimeInfo from %s", infoPath);
    try (FileChannel lockFile = new FileOutputStream(lockFilePath.toString()).getChannel();
        FileLock ignored = lockFile.lock()) {
      if (!Files.exists(infoPath)) {
        return XtsTradefedRuntimeInfo.getDefaultInstance();
      }
      XtsTradefedRuntimeInfo.Builder builder = XtsTradefedRuntimeInfo.newBuilder();
      PARSER.merge(
          Files.newBufferedReader(infoPath, UTF_8), ExtensionRegistry.getEmptyRegistry(), builder);
      return builder.build();
    } catch (IOException e) {
      logger.atWarning().log("Failed to read XtsTradefedRuntimeInfo from %s", infoPath);

      throw e;
    }
  }

  private Path prepareLockFile(Path infoPath) throws IOException {
    Path lockFilePath = Path.of(infoPath + ".lck");

    if (!Files.exists(lockFilePath)) {
      Files.createFile(lockFilePath);
    }
    Files.setLastModifiedTime(lockFilePath, FileTime.fromMillis(System.currentTimeMillis()));
    return lockFilePath;
  }
}
