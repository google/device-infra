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

package com.google.wireless.qa.mobileharness.shared.model.job.in.spec;

import com.google.protobuf.Message;

/**
 * Interface for any driver or decorator that wants to use protobuf extension of {@code JobSpec} and
 * {@code --spec_file} to initialize. All drivers/decorators should implement this interface if they
 * expect to be constructed with a proto spec.
 */
public interface SpecConfigable<T extends Message> {}
