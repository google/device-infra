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

load("@rules_java//java:java_library.bzl", "java_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/devtools/mobileharness/fe/v6:visibility"],
)

java_library(
    name = "device_source",
    srcs = ["DeviceSource.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "host_source",
    srcs = ["HostSource.java"],
    deps = [
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "key_descriptor",
    srcs = [
        "DeviceKeyDescriptor.java",
        "HostKeyDescriptor.java",
    ],
    deps = [
        ":device_source",
        ":host_source",
        "//src/java/com/google/devtools/mobileharness/shared/util/auto:auto_value",
    ],
)

java_library(
    name = "device_keys",
    srcs = ["DeviceKeys.java"],
    deps = [
        ":device_source",
        ":key_descriptor",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "host_keys",
    srcs = ["HostKeys.java"],
    deps = [
        ":host_source",
        ":key_descriptor",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "key_registry",
    srcs = [
        "AtsKeyRegistry.java",
        "KeyRegistry.java",
    ],
    deps = [
        ":device_keys",
        ":device_source",
        ":host_keys",
        ":host_source",
        ":key_descriptor",
        "//src/devtools/mobileharness/api/query/proto:lab_query_java_proto",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:javax_inject_jsr330_api",
    ],
)
