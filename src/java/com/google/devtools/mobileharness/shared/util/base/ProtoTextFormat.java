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

package com.google.devtools.mobileharness.shared.util.base;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.protobuf.TextFormat.Printer;

/** Utilities of {@link TextFormat}. */
public class ProtoTextFormat {

  private static final Printer DEBUG_PRINTER = TextFormat.printer();

  private static final TextFormat.Parser PARSER =
      TextFormat.Parser.newBuilder()
          .setAllowUnknownFields(true)
          .setAllowUnknownExtensions(true)
          .build();

  /** See {@link TextFormat#shortDebugString}. */
  public static String shortDebugString(MessageOrBuilder message) {
    return shortDebugStringOss(message);
  }

  @SuppressWarnings({"deprecation", "unused"})
  private static String shortDebugStringOss(MessageOrBuilder message) {
    // emittingSingleLine() is not open sourced.
    return DEBUG_PRINTER.shortDebugString(message);
  }

  /**
   * Similar to {@link TextFormat#parse(CharSequence, Class)} but allow unknown
   * enums/fields/extensions.
   */
  public static <T extends Message> T parse(CharSequence textproto, Class<T> protoClass)
      throws ParseException {
    return parse(textproto, ProtoExtensionRegistry.getGeneratedRegistry(), protoClass);
  }

  /**
   * Similar to {@link TextFormat#parse(CharSequence, ExtensionRegistry, Class)} but allow unknown
   * enums/fields/extensions.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Message> T parse(
      CharSequence textproto, ExtensionRegistry extensionRegistry, Class<T> protoClass)
      throws ParseException {
    Message.Builder builder;
    try {
      builder = (Message.Builder) protoClass.getMethod("newBuilder").invoke(null);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("Failed to call newBuilder() on %s" + protoClass, e);
    }
    PARSER.merge(textproto, extensionRegistry, builder);
    return (T) builder.build();
  }

  private ProtoTextFormat() {}
}
