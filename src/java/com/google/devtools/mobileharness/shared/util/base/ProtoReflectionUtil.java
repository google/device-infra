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

import static java.util.Arrays.stream;

import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import java.lang.reflect.Method;

/** Reflection util for Protobuf. */
public final class ProtoReflectionUtil {

  /**
   * Returns true if the given Protobuf class is a specific Protobuf message generated class, false
   * if it is a base class like {@link Message}.
   */
  public static boolean isGeneratedClass(Class<? extends Message> protoClazz) {
    return Message.class.isAssignableFrom(protoClazz)
        && stream(protoClazz.getMethods())
            .anyMatch(
                method ->
                    method.getName().equals("getDefaultInstance")
                        && method.getParameterCount() == 0);
  }

  /** Gets the default instance of the given Protobuf message class. */
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
   * Creates a new builder for the given Protobuf class.
   *
   * @param protoClazz the Protobuf class
   * @param builderClazz the builder class of the Protobuf class
   * @return a new builder of the Protobuf class
   * @throws LinkageError if failed to call newBuilder() on the Protobuf class.
   * @throws ClassCastException if the builder class is not the superclass of the Protobuf builder
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
