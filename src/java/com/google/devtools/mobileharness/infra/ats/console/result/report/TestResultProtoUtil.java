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

package com.google.devtools.mobileharness.infra.ats.console.result.report;

import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.protobuf.CodedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Utility to read the {@link Result} proto from a file. */
public class TestResultProtoUtil {

  /**
   * Reads {@link Result} from a file and returns it.
   *
   * @param protoFile The {@link File} containing the report
   * @return a {@link Result} created from the file
   * @throws IOException if failed to read the file
   */
  public static Result readFromFile(File protoFile) throws IOException {
    Result report;
    try (InputStream stream = new FileInputStream(protoFile)) {
      report =
          Result.parseFrom(
              CodedInputStream.newInstance(stream), ProtoExtensionRegistry.getGeneratedRegistry());
    }
    return report;
  }

  private TestResultProtoUtil() {}
}
