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

import com.google.protobuf.MessageLite;
import java.lang.reflect.Method;

/** Reflection util for protobuf. */
public final class ProtoReflectionUtil {

  /** Gets the default instance of the given protobuf message class. */
  @SuppressWarnings("unchecked")
  public static <T extends MessageLite> T getDefaultInstance(Class<T> clazz) {
    try {
      Method method = clazz.getMethod("getDefaultInstance");
      return (T) method.invoke(method);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("Failed to get default instance for " + clazz, e);
    }
  }

  private ProtoReflectionUtil() {}
}
