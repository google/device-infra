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

import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import java.lang.reflect.Method;

/** Reflection util for protobuf. */
public final class ProtoReflectionUtil {

  /** Gets the default instance of the given protobuf message class. */
  @SuppressWarnings("unchecked")
  public static <T extends MessageLite> T getDefaultInstance(Class<T> protoClazz) {
    try {
      Method method = protoClazz.getMethod("getDefaultInstance");
      return (T) method.invoke(method);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("Failed to get default instance for " + protoClazz, e);
    }
  }

  /**
   * Creates a new builder for the given protobuf class.
   *
   * @param protoClazz the protobuf class
   * @param builderClazz the builder class of the protobuf class
   * @return a new builder of the protobuf class
   * @throws LinkageError if failed to call newBuilder() on the protobuf class.
   * @throws ClassCastException if the builder class is not the superclass of the protobuf builder
   *     class.
   */
  public static <B extends Message.Builder> B newInstance(
      Class<? extends Message> protoClazz, Class<B> builderClazz) {
    try {
      return builderClazz.cast(protoClazz.getMethod("newBuilder").invoke(null));
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("Failed to call newBuilder() on %s" + protoClazz, e);
    }
  }

  private ProtoReflectionUtil() {}
}
