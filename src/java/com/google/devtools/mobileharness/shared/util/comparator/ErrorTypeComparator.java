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

package com.google.devtools.mobileharness.shared.util.comparator;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import java.util.Comparator;

/**
 * Comparator for sorting error types.
 *
 * <p>The priority order: INFRA_ISSUE > UNDETERMINED > UNCLASSIFIED > DEPENDENCY_ISSUE >
 * CUSTOMER_ISSUE
 */
public abstract class ErrorTypeComparator implements Comparator<ErrorType> {
  private ErrorTypeComparator() {}

  public static Comparator<ErrorType> getInstance() {
    return Comparator.comparingInt(ErrorTypeComparator::getPriority);
  }

  private static int getPriority(ErrorType errorType) {
    switch (errorType) {
      case INFRA_ISSUE:
        return 0;
      case UNDETERMINED:
        return 1;
      case UNCLASSIFIED:
        return 2;
      case DEPENDENCY_ISSUE:
        return 3;
      case CUSTOMER_ISSUE:
        return 4;
      case UNRECOGNIZED:
        return 5;
    }
    return 5;
  }
}
