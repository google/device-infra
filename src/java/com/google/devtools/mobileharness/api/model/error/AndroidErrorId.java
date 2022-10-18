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

package com.google.devtools.mobileharness.api.model.error;

import com.google.common.base.Preconditions;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.util.ErrorIdFormatter;

/**
 * Error IDs for Mobile Harness Android platform supports, or Android related Driver/Decorator,
 * Detector/Device implementations. *
 */
public enum AndroidErrorId implements ErrorId {
  // ***********************************************************************************************
  // Standard Android Platforms: 100_001 ~ 170_000
  // ***********************************************************************************************

  ANDROID_ERROR_ID_PLACE_HOLDER_TO_BE_RENAMED(200_000, ErrorType.UNDETERMINED);

  public static final int MIN_CODE = ExtErrorId.MAX_CODE + 1;
  public static final int MAX_CODE = 200_000;

  private final int code;
  private final ErrorType type;

  AndroidErrorId(int code, ErrorType type) {
    Preconditions.checkArgument(code >= MIN_CODE);
    Preconditions.checkArgument(code <= MAX_CODE);
    Preconditions.checkArgument(type != ErrorType.UNCLASSIFIED);
    this.code = code;
    this.type = type;
  }

  @Override
  public int code() {
    return code;
  }

  @Override
  public ErrorType type() {
    return type;
  }

  @Override
  public String toString() {
    return ErrorIdFormatter.formatErrorId(this);
  }
}
