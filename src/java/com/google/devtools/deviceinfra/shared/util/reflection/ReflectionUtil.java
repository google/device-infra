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

package com.google.devtools.deviceinfra.shared.util.reflection;

import com.google.devtools.deviceinfra.api.error.DeviceInfraException;
import com.google.devtools.deviceinfra.api.error.id.defined.BasicErrorId;

/** Utility for reflection operations. */
public class ReflectionUtil {

  @SuppressWarnings("unchecked")
  public <T> Class<? extends T> loadClass(String className, Class<T> type, ClassLoader classLoader)
      throws DeviceInfraException, ClassNotFoundException {
    Class<?> clazz;
    try {
      clazz = Class.forName(className, /* initialize= */ true, classLoader);
    } catch (RuntimeException e) {
      throw BasicErrorId.REFLECTION_LOAD_CLASS_ERROR.toException(
          "Failed to load class: " + className, e);
    }
    if (type.isAssignableFrom(clazz)) {
      return (Class<? extends T>) clazz;
    } else {
      throw BasicErrorId.REFLECTION_LOAD_CLASS_TYPE_MISMATCH.toException(
          String.format("%s is not assignable from %s", type.getName(), clazz.getName()));
    }
  }
}
