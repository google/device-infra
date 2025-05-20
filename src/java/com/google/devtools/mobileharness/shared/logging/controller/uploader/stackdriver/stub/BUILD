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
)

java_library(
    name = "stub",
    srcs = glob(["*.java"]),
    visibility = [
        "//src/java/com/google/devtools/mobileharness/shared/logging/controller/uploader:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/shared/logging/controller/uploader:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/shared/logging/controller/uploader/stackdriver/stub:__pkg__",
    ],
    #runtime_deps = [
    #   "//third_party/java/protobuf:protobuf_impl",
    #],
    deps = [
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc",
        "//src/java/com/google/devtools/common/metrics/stability/rpc/grpc:stub_util",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/logging/annotation",
        "//src/java/com/google/devtools/mobileharness/shared/util/comm/stub:client_interceptor_factory",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local",
        "//src/java/com/google/devtools/mobileharness/shared/util/file/local:res_util",
        "//src/java/com/google/devtools/mobileharness/shared/util/flags",
        "//src/java/com/google/devtools/mobileharness/shared/util/path",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_api_grpc_grpc_google_cloud_logging_v2",
        "@maven//:com_google_api_grpc_proto_google_cloud_logging_v2",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_inject_guice",
    ],
)
