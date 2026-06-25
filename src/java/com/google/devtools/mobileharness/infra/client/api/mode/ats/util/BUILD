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
    name = "remote_session_device_cache",
    srcs = ["RemoteSessionDeviceCache.java"],
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client/api/mode/ats:__subpackages__",
    ],
    deps = [
        "//src/devtools/mobileharness/infra/client/api/proto:resource_federation_java_proto",
        "//src/devtools/mobileharness/infra/lab/proto:prepare_test_service_java_proto",
        "//src/java/com/google/devtools/common/metrics/stability/rpc:exception",
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/api/model/lab:locator",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/ats:annotations",
        "//src/java/com/google/devtools/mobileharness/infra/client/api/mode/remote:lab_server_locator",
        "//src/java/com/google/devtools/mobileharness/infra/client/longrunningservice/util:session_device_cache",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc/stub:device_cache",
        "//src/java/com/google/devtools/mobileharness/shared/util/time:time_utils",
        "//src/java/com/google/wireless/qa/mobileharness/client/api/util/stub",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:javax_inject_jsr330_api",
    ],
)
