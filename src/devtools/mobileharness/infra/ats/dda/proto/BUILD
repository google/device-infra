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

load("//third_party/protobuf/bazel:java_proto_library.bzl", "java_proto_library")
load("//third_party/protobuf/bazel:proto_library.bzl", "proto_library")

package(default_applicable_licenses = ["//:license"])

proto_library(
    name = "session_plugin_proto",
    srcs = ["session_plugin.proto"],
    deps = [
        "//src/devtools/common/metrics/stability/model/proto:error_proto",
        "//src/devtools/mobileharness/api/model/proto:job_proto",
        "//src/devtools/mobileharness/api/query/proto:lab_query_proto",
        "@com_google_protobuf//:duration_proto",
    ],
)

java_proto_library(
    name = "session_plugin_java_proto",
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/dda:__subpackages__",
    ],
    deps = [":session_plugin_proto"],
)
