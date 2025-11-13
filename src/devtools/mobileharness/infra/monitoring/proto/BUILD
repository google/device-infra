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

load("@protobuf//bazel:java_proto_library.bzl", "java_proto_library")
load("@protobuf//bazel:proto_library.bzl", "proto_library")
load("@protobuf//bazel:py_proto_library.bzl", "py_proto_library")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/monitoring:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/monitoring:__pkg__",
        "//wireless/android/test_tools/dual_home_lab/monarch_proxy:__subpackages__",
    ],
)

proto_library(
    name = "monitored_record_proto",
    srcs = ["monitored_record.proto"],
    deps = ["@protobuf//:timestamp_proto"],
)

java_proto_library(
    name = "monitored_record_java_proto",
    deps = [":monitored_record_proto"],
)

py_proto_library(
    name = "monitored_record_py_pb2",
    deps = [":monitored_record_proto"],
)

proto_library(
    name = "record_proto",
    srcs = ["record.proto"],
)
