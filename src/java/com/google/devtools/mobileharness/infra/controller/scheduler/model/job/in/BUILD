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

java_library(
    name = "device_requirements",
    srcs = [
        "DeviceRequirement.java",
        "DeviceRequirements.java",
    ],
    visibility = [
        "//:deviceinfra_pkg",
    ],
    deps = [
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in:decorators",
        "//src/java/com/google/devtools/mobileharness/api/model/job/in:dimensions",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "in_factory",
    srcs = ["JobInInternalFactory.java"],
    visibility = [
        # Note: this tool is only for MH infra, please don't add more visibilities.
        "//src/java/com/google/devtools/mobileharness/service/moss/util/slg:__pkg__",
    ],
    deps = [
        ":device_requirements",
        "//src/devtools/mobileharness/api/model/proto:job_java_proto",
    ],
)
