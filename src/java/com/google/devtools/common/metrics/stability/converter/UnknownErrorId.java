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

package com.google.devtools.common.metrics.stability.converter;

import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.model.proto.NamespaceProto.Namespace;
import com.google.devtools.common.metrics.stability.util.ErrorIdFormatter;
import java.util.Objects;

/** For looking up ErrorId from given error code/name/type. */
public class UnknownErrorId implements ErrorId {
  private final int code;
  private final String name;
  private final ErrorType type;
  private final Namespace namespace;

  public static final ErrorId NOT_DEFINED =
      new UnknownErrorId(-1, "UNKNOWN_ERROR_ID", ErrorType.UNCLASSIFIED, Namespace.UNKNOWN);

  private UnknownErrorId(int code, String name, ErrorType type, Namespace namespace) {
    this.code = code;
    this.name = name;
    this.type = type;
    this.namespace = namespace;
  }

  @Override
  public int code() {
    return code;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public ErrorType type() {
    return type;
  }

  @Override
  public Namespace namespace() {
    return namespace;
  }

  @Override
  public String toString() {
    return ErrorIdFormatter.formatErrorId(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ErrorId)) {
      return false;
    }
    ErrorId that = (ErrorId) o;
    return code == that.code()
        && Objects.equals(name, that.name())
        && type == that.type()
        && namespace == that.namespace();
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, name, type, namespace);
  }
}
