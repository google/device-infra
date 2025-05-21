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
    name = "session_error_util",
    srcs = ["SessionErrorUtil.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/converter",
        "//src/java/com/google/devtools/common/metrics/stability/converter:deserialized_exception",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "version_proto_util",
    srcs = ["VersionProtoUtil.java"],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:version_service_java_proto",
        "//src/devtools/mobileharness/shared/version/proto:version_java_proto",
        "//src/java/com/google/devtools/mobileharness/shared/version",
        "//src/java/com/google/devtools/mobileharness/shared/version:version_util",
    ],
)

java_library(
    name = "session_query_util",
    srcs = ["SessionQueryUtil.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/ats/console/controller/olcserver:__pkg__",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/longrunningservice:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_java_proto",
        "//src/devtools/mobileharness/infra/client/longrunningservice/proto:session_service_java_proto",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/constant:session_properties",
        "//src/java/com/google/devtools/mobileharness/shared/util/message:field_mask_utils",
        "@com_google_protobuf_protobuf_java",
        "@maven//:com_google_guava_guava",
    ],
)
