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

package com.google.wireless.qa.mobileharness.shared.api.validator.job;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Validator for {@code AndroidCdpDecorator}. */
public class AndroidCdpDecoratorJobValidator implements JobValidator {

  public static final String PARAM_TARGET_TYPE = "target_type";
  public static final String PARAM_PACKAGE_NAME = "package_name";
  public static final String PARAM_DEBUG_PORT = "debug_port";
  public static final String PARAM_LOCAL_SERVER_PORT = "local_server_port";

  public static final String TARGET_TYPE_WEBVIEW = "webview";

  @VisibleForTesting
  static final String ERROR_PACKAGE_NAME_MANDATORY_FOR_WEBVIEW =
      "package_name parameter is mandatory when target_type is webview.";

  @VisibleForTesting
  static final String ERROR_INVALID_DEBUG_PORT =
      "Invalid debug_port: must be an integer between 0 and 65535.";

  @VisibleForTesting
  static final String ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX =
      "Invalid local_server_port mapping: ";

  @CanIgnoreReturnValue
  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = new ArrayList<>();

    validateTargetTypeAndPackageName(job, errors);
    validateDebugPort(job, errors);
    validateLocalServerPort(job, errors);

    return errors;
  }

  private static void validateTargetTypeAndPackageName(JobInfo job, List<String> errors) {
    String targetTypeParam = job.params().get(PARAM_TARGET_TYPE, "");
    String targetType =
        targetTypeParam == null ? "" : targetTypeParam.trim().toLowerCase(Locale.US);
    if (targetType.equals(TARGET_TYPE_WEBVIEW)) {
      String packageName = job.params().get(PARAM_PACKAGE_NAME);
      if (Strings.isNullOrEmpty(packageName) || packageName.trim().isEmpty()) {
        errors.add(ERROR_PACKAGE_NAME_MANDATORY_FOR_WEBVIEW);
      }
    }
  }

  private static void validateDebugPort(JobInfo job, List<String> errors) {
    if (job.params().has(PARAM_DEBUG_PORT)) {
      String debugPortStr = job.params().get(PARAM_DEBUG_PORT);
      try {
        int debugPort = Integer.parseInt(debugPortStr.trim());
        if (debugPort < 0 || debugPort > 65535) {
          errors.add(ERROR_INVALID_DEBUG_PORT);
        }
      } catch (NumberFormatException e) {
        errors.add(ERROR_INVALID_DEBUG_PORT);
      }
    }
  }

  private static void validateLocalServerPort(JobInfo job, List<String> errors) {
    if (!job.params().has(PARAM_LOCAL_SERVER_PORT)) {
      return;
    }
    String localServerPortParam = job.params().get(PARAM_LOCAL_SERVER_PORT);
    if (localServerPortParam == null) {
      return;
    }
    for (String p : Splitter.on(',').split(localServerPortParam)) {
      String mapping = p.trim();
      if (mapping.isEmpty()) {
        continue;
      }
      if (!isValidReversePortMapping(mapping)) {
        errors.add(ERROR_INVALID_LOCAL_SERVER_PORT_PREFIX + mapping);
      }
    }
  }

  private static boolean isValidReversePortMapping(String mapping) {
    if (mapping.contains(":")) {
      try {
        HostAndPort hostAndPort = HostAndPort.fromString(mapping);
        if (!hostAndPort.hasPort()) {
          return false;
        }
        int guestPort = Integer.parseInt(hostAndPort.getHost());
        int hostPort = hostAndPort.getPort();
        return guestPort >= 0 && guestPort <= 65535 && hostPort >= 0 && hostPort <= 65535;
      } catch (IllegalArgumentException e) {
        return false;
      }
    } else {
      try {
        int hostPort = Integer.parseInt(mapping);
        return hostPort >= 0 && hostPort <= 65535;
      } catch (NumberFormatException e) {
        return false;
      }
    }
  }
}
