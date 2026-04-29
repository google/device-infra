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

package com.google.devtools.mobileharness.shared.util.reflection;

import com.google.common.reflect.TypeToken;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

/** Utility for Java reflection types. */
public final class TypeUtil {

  /**
   * Checks if the given type is complete and fully resolved.
   *
   * <p>A type is complete if it does not contain any raw types, wildcards, or type variables at any
   * level of its generic hierarchy.
   *
   * @param type the type to check
   * @throws IllegalArgumentException if the type is incomplete or contains raw types
   */
  public static void checkCompleteness(Type type) {
    // Rejects Wildcards and Type Variables.
    if (type instanceof WildcardType || type instanceof TypeVariable) {
      throw new IllegalArgumentException(
          String.format(
              "Type %s must not contain wildcards or type variables.", type.getTypeName()));
    }

    // Checks for Raw Types in Hierarchy.
    if (type instanceof Class || type instanceof ParameterizedType) {
      TypeToken<?> typeToken = TypeToken.of(type);
      for (TypeToken<?> supertype : typeToken.getTypes()) {
        if (supertype.getType() instanceof Class<?> rawClass) {
          if (rawClass.getTypeParameters().length > 0) {
            throw new IllegalArgumentException(
                String.format("Type %s must not be raw.", rawClass.getTypeName()));
          }
        }
      }
    }

    // Recurses on type arguments.
    if (type instanceof ParameterizedType parameterizedType) {
      for (Type arg : parameterizedType.getActualTypeArguments()) {
        checkCompleteness(arg);
      }
    }

    // Recurses on array component.
    if (type instanceof GenericArrayType genericArrayType) {
      checkCompleteness(genericArrayType.getGenericComponentType());
    }

    // Recurses on array component for raw array types.
    if (type instanceof Class<?> clazz && clazz.isArray()) {
      checkCompleteness(clazz.getComponentType());
    }
  }

  private TypeUtil() {}
}
