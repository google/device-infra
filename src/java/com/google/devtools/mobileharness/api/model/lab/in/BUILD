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

# Description:
#   Input data models for Mobile Harness labs and devices.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//:deviceinfra_pkg",
    ],
)

java_library(
    name = "ports",
    srcs = ["Ports.java"],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:lab_java_proto",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "in",
    srcs = [
        "Decorators.java",
        "Drivers.java",
        "Owners.java",
        "Types.java",
    ],
    exports = [
        ":dimension",
    ],
    deps = [
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "dimension",
    srcs = [
        "CompositeDimensions.java",
        "Dimensions.java",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:device_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/util/dimension:value_comparator",
        "//src/java/com/google/wireless/qa/mobileharness/shared/constant:dimension",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "dimension_factory",
    srcs = [
        "DimensionsFactory.java",
    ],
    visibility = [
        # Do no add visibility here. Please use lite_dimension_factory instead.
        "//src/java/com/google/devtools/mobileharness/api/model/lab:__pkg__",
    ],
    deps = [
        ":dimension",
        ":local_configurable_dimension",
        ":local_dimension",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "@maven//:com_google_code_findbugs_jsr305",
    ],
)

java_library(
    name = "lite_dimension_factory",
    srcs = [
        "LiteDimensionsFactory.java",
    ],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/api/model/lab:__pkg__",
    ],
    deps = [
        ":dimension",
        ":local_dimension",
    ],
)

java_library(
    name = "local_dimension",
    srcs = [
        "LocalDimensions.java",
    ],
    visibility = [
        # Do no add visibility here. Please use lite_dimension_factory instead.
        "//src/java/com/google/devtools/mobileharness/api/model/lab:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/controller:__pkg__",
    ],
    deps = [
        ":dimension",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "local_configurable_dimension",
    srcs = [
        "LocalConfigurableDimensions.java",
    ],
    visibility = [
        # Do no add visibility here. Please use lite_dimension_factory instead.
    ],
    deps = [
        ":dimension",
        ":local_dimension",
        "//src/java/com/google/devtools/mobileharness/infra/controller/device/config",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:common_java_proto",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)
