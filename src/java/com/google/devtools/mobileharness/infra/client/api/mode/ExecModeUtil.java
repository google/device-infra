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

package com.google.devtools.mobileharness.infra.client.api.mode;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.api.proto.ResourceFederationProto.ResourceFederation;
import com.google.wireless.qa.mobileharness.shared.util.ReflectionUtil;

/**
 * Utility methods for exec modes.
 *
 * @author derekchen@google.com (Derek Chen)
 */
public final class ExecModeUtil {
  /** Suffix of the class name of the exec modes. */
  private static final String EXEC_MODE_CLASS_SUFFIX = "Mode";

  private static final String EXEC_MODE_PACKAGE_NAME_PRIMARY =
      "com.google.wireless.qa.mobileharness.client.api.mode";

  private static final String EXEC_MODE_PACKAGE_NAME_SECONDARY =
      "com.google.devtools.mobileharness.infra.client.api.mode";

  /**
   * Creates {@code ExecMode} instance according to the exec mode name. Supposes exec mode name is
   * XXX_YYY, and the class definition is
   * com.google.devtools.mobileharness.infra.client.api.mode.xxx.yyy.XxxYyyMode. If failed to create
   * {@code ExecMode} instance due to {@code BasicErrorId.REFLECTION_CLASS_NOT_FOUND} , will attempt
   * to create instance in alternative package, example
   * com.google.wireless.qa.mobileharness.client.api.mode.xxx.yyy.XxxYyyMode.
   *
   * <p>Please use #createRemoteModeInstance if creating {@code RemoteMode}.
   *
   * @throws MobileHarnessException if {@code ExecMode} instance cannot be created.
   */
  public static ExecMode createInstance(String modeName) throws MobileHarnessException {
    Class<? extends ExecMode> execModeClass = getExecModeClass(modeName);
    return ReflectionUtil.newInstance(execModeClass);
  }

  /**
   * Creates {@code ExecMode} instance according to the exec mode name and a {@code
   * ResourceFederation}. Supposes exec mode name is XXX_YYY, and the class definition is
   * com.google.devtools.mobileharness.infra.client.api.mode.xxx.yyy.XxxYyyMode. If failed to create
   * {@code ExecMode} instance due to {@code BasicErrorId.REFLECTION_CLASS_NOT_FOUND} , will attempt
   * to create instance in alternative package, example
   * com.google.wireless.qa.mobileharness.client.api.mode.xxx.yyy.XxxYyyMode.
   *
   * @throws MobileHarnessException if {@code ExecMode} instance cannot be created.
   */
  public static ExecMode createRemoteModeInstance(
      String modeName, ResourceFederation resourceFederation) throws MobileHarnessException {
    Class<? extends ExecMode> execModeClass = getExecModeClass(modeName);
    return ReflectionUtil.newInstance(execModeClass, ResourceFederation.class, resourceFederation);
  }

  /**
   * Gets the name of the exec mode. Supposes the exec mode class is XxxYyyMode, the mode name
   * should be XXX_YYY.
   */
  public static String getModeName(ExecMode execMode) {
    StringBuilder modeName = new StringBuilder();
    String className = execMode.getClass().getSimpleName();
    if (className.endsWith(EXEC_MODE_CLASS_SUFFIX)) {
      // Removes the suffix.
      className = className.substring(0, className.length() - EXEC_MODE_CLASS_SUFFIX.length());
    }

    // Converts XxxYyy to XXX_YYY.
    for (int i = 0; i < className.length(); i++) {
      char ch = className.charAt(i);
      if (Character.isUpperCase(ch)) {
        if (modeName.length() > 0) {
          modeName.append('_');
        }
        modeName.append(ch);
      } else {
        modeName.append(Character.toUpperCase(ch));
      }
    }
    return modeName.toString();
  }

  @VisibleForTesting
  public static Class<? extends ExecMode> getExecModeClass(String modeName)
      throws MobileHarnessException {
    StringBuilder packageName = new StringBuilder(EXEC_MODE_PACKAGE_NAME_PRIMARY);
    StringBuilder alternativePackageName = new StringBuilder(EXEC_MODE_PACKAGE_NAME_SECONDARY);
    StringBuilder className = new StringBuilder();
    boolean newPackage = true;
    for (int i = 0; i < modeName.length(); i++) {
      char ch = modeName.charAt(i);
      if (ch == '_') {
        newPackage = true;
        continue;
      }
      if (newPackage) {
        newPackage = false;
        packageName.append('.');
        alternativePackageName.append('.');
        className.append(Character.toUpperCase(ch));
      } else {
        className.append(Character.toLowerCase(ch));
      }
      packageName.append(Character.toLowerCase(ch));
      alternativePackageName.append(Character.toLowerCase(ch));
    }
    className.append(EXEC_MODE_CLASS_SUFFIX);
    try {
      // Uses reflection to create class.
      return ReflectionUtil.getClass(className.toString(), ExecMode.class, packageName.toString());
    } catch (MobileHarnessException e) {
      if (e.getErrorId().equals(BasicErrorId.REFLECTION_CLASS_NOT_FOUND)) {
        return ReflectionUtil.getClass(
            className.toString(), ExecMode.class, alternativePackageName.toString());
      } else {
        throw e;
      }
    }
  }

  private ExecModeUtil() {}
}
