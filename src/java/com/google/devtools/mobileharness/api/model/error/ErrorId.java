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

import com.google.devtools.deviceinfra.api.error.id.DeviceInfraErrorId;

/**
 * Identity for distinguishing Mobile Harness errors/exceptions.
 *
 * <p>Please be <b>specific</b> when you are creating new ErrorIds, in order to better break down
 * and track errors. Do <b>not</b> make general ErrorIds like ILLEGAL_ARGUMENT, ILLEGAL_STATE.
 *
 * @see BasicErrorId
 * @see InfraErrorId
 * @see ExtErrorId
 */
public interface ErrorId extends DeviceInfraErrorId {}
