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

package com.google.devtools.deviceinfra.shared.logging.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Qualifier;

/** All annotations used for Udcluster Logging Guice bindings. */
public final class Annotations {
  /** Annotation for binding the FileHandler. */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface LocalFileHandler {}

  /** Annotation for binding the Stackdriver secret file name. */
  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface StackdriverSecretFileName {}

  private Annotations() {}
}
