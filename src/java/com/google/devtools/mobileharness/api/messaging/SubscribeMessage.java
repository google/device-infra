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

package com.google.devtools.mobileharness.api.messaging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method is a message subscriber of the OmniLab messaging system.
 *
 * <p>A valid message subscriber is a method that meets all of the following conditions:
 *
 * <ul>
 *   <li>with any name and any visibility.
 *   <li>annotated by this annotation.
 *   <li>with a single parameter of type {@linkplain MessageEvent MessageEvent&lt;FooRequest&gt;}
 *       where {@code FooRequest} is a specific Protobuf message type extending {@linkplain
 *       com.google.protobuf.Message Message}. This allows the message subscriber to receive
 *       messages of type {@code FooRequest}.
 *   <li>whose return type is {@code FooResponse} where {@code FooResponse} is a Protobuf message
 *       type extending {@linkplain com.google.protobuf.Message Message}.
 *   <li>which can throw any exceptions.
 * </ul>
 *
 * For example:
 *
 * <pre>{@code
 * @SubscribeMessage
 * private FooResponse onMessage(MessageEvent<FooRequest> event) {
 *   FooRequest request = event.getMessage();
 *   return FooResponse.getDefaultInstance();
 * }
 * }</pre>
 *
 * <p>Message subscribers in one object will receive one message in the order of method name and
 * message type Java class name.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SubscribeMessage {}
