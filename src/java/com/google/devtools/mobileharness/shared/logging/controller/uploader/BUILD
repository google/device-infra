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
    name = "uploader",
    srcs = glob(["*.java"]),
    visibility = [
        "//src/java/com/google/devtools/mobileharness/shared/logging:__subpackages__",
        "//src/javatests/com/google/devtools/mobileharness/shared/logging:__subpackages__",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/logging/annotation",
        "//src/java/com/google/devtools/mobileharness/shared/logging/controller/uploader/stackdriver/stub",
        "//src/java/com/google/devtools/mobileharness/shared/logging/parameter:log_project",
        "@maven//:com_google_api_grpc_proto_google_cloud_logging_v2",
        "@maven//:com_google_inject_guice",
    ],
)
