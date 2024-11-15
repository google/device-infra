# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

java_library(
    name = "callables",
    srcs = ["Callables.java"],
    deps = [
        ":mobile_harness_callable",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/constant/closeable",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "concurrency_util",
    srcs = ["ConcurrencyUtil.java"],
    deps = [
        ":callables",
        ":mobile_harness_callable",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:tag",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "mobile_harness_callable",
    srcs = ["MobileHarnessCallable.java"],
    deps = ["//src/java/com/google/devtools/mobileharness/api/model/error"],
)

java_library(
    name = "more_futures",
    srcs = ["MoreFutures.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "thread_factory_util",
    srcs = ["ThreadFactoryUtil.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "thread_pools",
    srcs = ["ThreadPools.java"],
    deps = [
        ":thread_factory_util",
        "//src/java/com/google/devtools/mobileharness/shared/context:invocation_context_executors",
        "@maven//:com_google_guava_guava",
    ],
)
