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
    name = "async_timer_decorator",
    srcs = ["AsyncTimerDecorator.java"],
    deps = [
        ":lifecycle_decorator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "base_decorator",
    srcs = ["BaseDecorator.java"],
    visibility = [
        "//java/com/google/wireless/qa/mobileharness/shared/api:__pkg__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/api/decorator:__pkg__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/api/decorator/base:__pkg__",
        "//javatests/com/google/wireless/qa/mobileharness/shared/api/driver:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator:__pkg__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator/base:__pkg__",
    ],
    deps = [
        ":decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:base_driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "decorator",
    srcs = ["Decorator.java"],
    deps = [
        ":lifecycle_decorator_subclass",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "lifecycle_decorator",
    srcs = ["LifecycleDecorator.java"],
    deps = [
        ":base_decorator",
        ":lifecycle_decorator_subclass",
        "//src/devtools/mobileharness/api/model/proto:test_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "//src/java/com/google/devtools/mobileharness/shared/util/error:more_throwables",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "lifecycle_decorator_subclass",
    srcs = ["LifecycleDecoratorSubclass.java"],
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator/base:__pkg__",
    ],
)

java_library(
    name = "phase_skippable_decorator",
    srcs = ["PhaseSkippableDecorator.java"],
    deps = [
        ":lifecycle_decorator",
        ":setup_only_decorator",
        ":teardown_only_decorator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator/constant:step_skippable_decorator_constants",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator/util:phase_skippable_decorator_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:driver_factory",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "setup_only_decorator",
    srcs = ["SetupOnlyDecorator.java"],
    deps = [
        ":lifecycle_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)

java_library(
    name = "step_skippable_lifecycle_decorator",
    srcs = ["StepSkippableLifecycleDecorator.java"],
    deps = [
        ":lifecycle_decorator",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator/constant:step_skippable_decorator_constants",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/decorator/util:step_skippable_lifecycle_decorator_util",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
        "@maven//:com_google_errorprone_error_prone_annotations",
    ],
)

java_library(
    name = "teardown_only_decorator",
    srcs = ["TeardownOnlyDecorator.java"],
    deps = [
        ":lifecycle_decorator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job",
    ],
)
