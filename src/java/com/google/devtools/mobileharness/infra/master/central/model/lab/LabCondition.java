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

package com.google.devtools.mobileharness.infra.master.central.model.lab;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.infra.master.central.proto.Lab.LabServerCondition;
import java.util.Optional;

/** The condition of the Lab Server and Daemon Server on a lab. */
@AutoValue
public abstract class LabCondition {

  public static LabCondition create(Optional<LabServerCondition> labServerCondition) {
    return new AutoValue_LabCondition(labServerCondition);
  }

  public abstract Optional<LabServerCondition> labServerCondition();
}
