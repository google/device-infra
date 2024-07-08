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

import com.google.devtools.mobileharness.platform.android.xts.runtime.proto.RuntimeInfoProto.XtsTradefedRuntimeInfo;
import com.google.devtools.mobileharness.platform.android.xts.runtime.proto.RuntimeInfoProto.XtsTradefedRuntimeInfoOrBuilder;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;

/** Util for handling {@link XtsTradefedRuntimeInfo}. */
public class XtsTradefedRuntimeInfoUtil {

  private static final String PROPERTY_KEY = "xts_tradefed_runtime_info";

  private static final TextFormat.Printer TEXTPROTO_PRINTER = TextFormat.printer();
  private static final TextFormat.Parser TEXTPROTO_PARSER =
      TextFormat.Parser.newBuilder()
          .setAllowUnknownFields(true)
          .setAllowUnknownExtensions(true)
          .build();

  public void saveToTestInfo(TestInfo testInfo, XtsTradefedRuntimeInfoOrBuilder runtimeInfo) {
    testInfo.properties().add(PROPERTY_KEY, TEXTPROTO_PRINTER.printToString(runtimeInfo));
  }

  public XtsTradefedRuntimeInfo loadFromTestInfo(TestInfo testInfo) throws ParseException {
    Optional<String> propertyValue = testInfo.properties().getOptional(PROPERTY_KEY);
    if (propertyValue.isPresent()) {
      XtsTradefedRuntimeInfo.Builder result = XtsTradefedRuntimeInfo.newBuilder();
      TEXTPROTO_PARSER.merge(propertyValue.get(), result);
      return result.build();
    } else {
      return XtsTradefedRuntimeInfo.getDefaultInstance();
    }
  }
}
