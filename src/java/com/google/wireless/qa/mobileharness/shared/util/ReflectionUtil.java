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

package com.google.wireless.qa.mobileharness.shared.util;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/** Util for using reflection to get classes or create instances. */
public final class ReflectionUtil {

  private static final com.google.devtools.mobileharness.shared.util.reflection.ReflectionUtil
      newUtil = new com.google.devtools.mobileharness.shared.util.reflection.ReflectionUtil();

  /** Returns the non-mock superclass of the given class. */
  public static <T> Class<? super T> getNonMockBaseClass(Class<? super T> clazz) {
    while (clazz.getSimpleName().contains("CGLIB$$")) {
      clazz = clazz.getSuperclass();
    }
    return clazz;
  }

  /**
   * Gets the class according to the simple class name and a super class or interface in the same
   * package. The class loader is current class's class loader. For almost all the cases, since this
   * ReflectionUtil class is loaded by main jar, it uses main jar's class loader.
   *
   * @param classSimpleName simple name of the target class
   * @param baseClassOrInterface super class or interface
   * @param packageName the name of the package of the target class
   * @return the class in the same package of the given super class or interface
   * @throws MobileHarnessException if no class was found, or it did not implement the given
   *     interface
   */
  public static <T> Class<? extends T> getClass(
      String classSimpleName, Class<T> baseClassOrInterface, String packageName)
      throws MobileHarnessException {
    return getClass(
        classSimpleName, baseClassOrInterface, packageName, ReflectionUtil.class.getClassLoader());
  }

  /**
   * Gets the class according to the simple class name and a super class or interface in the same
   * package with the specified class loader.
   *
   * @param classSimpleName simple name of the target class
   * @param baseClassOrInterface super class or interface
   * @param packageName the name of the package of the target class
   * @param classLoader the class loader to load the class. If want to load the class from a plugin
   *     jar's class loader(by default, the class loader is ReflectionUtil's class loader, which is
   *     usually main jar's class loader), it can be passed through this parameter.
   * @return the class in the same package of the given super class or interface
   * @throws MobileHarnessException if no class was found, or it did not implement the given
   *     interface
   */
  public static <T> Class<? extends T> getClass(
      String classSimpleName,
      Class<T> baseClassOrInterface,
      String packageName,
      ClassLoader classLoader)
      throws MobileHarnessException {
    // Gets the class with the given simple name in the package of the super class or interface
    String classCanonicalName = packageName + "." + classSimpleName;
    try {
      return newUtil.loadClass(classCanonicalName, baseClassOrInterface, classLoader);
    } catch (ClassNotFoundException e) {
      throw new MobileHarnessException(
          BasicErrorId.REFLECTION_CLASS_NOT_FOUND,
          String.format("Class [%s] not found", classCanonicalName),
          e);
    }
  }

  /**
   * Creates a new instance of the given class type. The class should have a public constructor with
   * no argument.
   *
   * @throws MobileHarnessException if fails to create a new instance
   */
  public static <T> T newInstance(Class<? extends T> targetClass) throws MobileHarnessException {
    // Gets the constructor.
    Constructor<? extends T> constructor;
    String errMsgTemplate = "Cannot create new instance of " + targetClass.getName() + ": %s";
    try {
      constructor = targetClass.getConstructor();
    } catch (NoSuchMethodException e) {
      throw new MobileHarnessException(
          BasicErrorId.REFLECTION_CONSTRUCTOR_NOT_FOUND,
          String.format(errMsgTemplate, "No constructor with 0 arguments"),
          e);
    }
    // Creates a new device instance.
    try {
      return constructor.newInstance();
    } catch (IllegalAccessException | InstantiationException e) {
      throw new MobileHarnessException(
          BasicErrorId.REFLECTION_INSTANTIATION_ERROR,
          String.format(errMsgTemplate, e.getMessage()),
          e);
    } catch (InvocationTargetException e) {
      throw new MobileHarnessException(
          BasicErrorId.REFLECTION_INSTANTIATION_ERROR,
          String.format(errMsgTemplate, e.getCause().getMessage()),
          e);
    }
  }

  /**
   * Creates a new instance of the given class type with one argument. The class should have a
   * public constructor with only one argument.
   *
   * @throws MobileHarnessException if fails to create a new instance
   */
  public static <T, P> T newInstance(
      Class<? extends T> targetClass, Class<? extends P> argumentType, P argument)
      throws MobileHarnessException {
    // Gets the constructor.
    Constructor<? extends T> constructor;
    String errMsgTemplate = "Cannot create new instance of " + targetClass.getName() + ": %s";
    try {
      constructor = targetClass.getConstructor(argumentType);
    } catch (NoSuchMethodException e) {
      throw new MobileHarnessException(
          BasicErrorId.REFLECTION_CONSTRUCTOR_NOT_FOUND,
          String.format(errMsgTemplate, "No constructor with one argument"),
          e);
    }
    // Creates a new device instance.
    try {
      return constructor.newInstance(argument);
    } catch (IllegalAccessException | InstantiationException e) {
      throw new MobileHarnessException(
          BasicErrorId.REFLECTION_INSTANTIATION_ERROR,
          String.format(errMsgTemplate, e.getMessage()),
          e);
    } catch (InvocationTargetException e) {
      throw new MobileHarnessException(
          BasicErrorId.REFLECTION_INSTANTIATION_ERROR,
          String.format(errMsgTemplate, e.getCause().getMessage()),
          e);
    }
  }

  private ReflectionUtil() {}
}
