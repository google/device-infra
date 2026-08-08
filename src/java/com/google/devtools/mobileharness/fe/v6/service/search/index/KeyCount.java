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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

/**
 * A (key, count) pair in the global exact-match value index.
 *
 * <p>For a given normalized value string, the global exact index maps it to the list of keys that
 * carry that value along with the device count for each. This supports O(1) exact-match lookup
 * across all keys when the suggestion engine receives a typed token.
 */
public record KeyCount(String key, int count) {}
