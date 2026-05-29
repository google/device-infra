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

package com.google.devtools.mobileharness.platform.android.xts.businesslogic;

import static java.util.Arrays.stream;

import java.lang.reflect.Method;

/**
 * Resolves methods provided by the target object and invokes them based on Business Logic parsed
 * rules.
 */
public final class BusinessLogicExecutor {

  private final Object target;

  public BusinessLogicExecutor(Object target) {
    this.target = target;
  }

  /**
   * Executes a business logic condition.
   *
   * @param method the name of the method to invoke, returning boolean.
   * @param args the string arguments to supply to the method.
   * @return the return value of the method invoked.
   */
  public boolean executeCondition(String method, String... args)
      throws ReflectiveOperationException {
    return (Boolean) invokeMethod(method, args);
  }

  /**
   * Executes a business logic action.
   *
   * @param method the name of the method to invoke (e.g., skipModule).
   * @param args the string arguments to supply to the method.
   */
  public void executeAction(String method, String... args) throws ReflectiveOperationException {
    var unused = invokeMethod(method, args);
  }

  private Object invokeMethod(String method, String... args) throws ReflectiveOperationException {
    final String methodName = getMethodName(method);
    Method matchedMethod =
        stream(target.getClass().getMethods())
            .filter(m -> m.getName().equals(methodName))
            .filter(m -> m.getParameterCount() == args.length)
            .filter(m -> stream(m.getParameterTypes()).allMatch(p -> p.equals(String.class)))
            .findFirst()
            .orElseThrow(
                () ->
                    new NoSuchMethodException(
                        "Method "
                            + method
                            + " not found or parameters do not match Strings array"));
    return matchedMethod.invoke(target, (Object[]) args);
  }

  private static String getMethodName(String method) {
    if (method.contains("#")) {
      return method.substring(method.lastIndexOf("#") + 1);
    }
    return method.substring(method.lastIndexOf(".") + 1);
  }
}
