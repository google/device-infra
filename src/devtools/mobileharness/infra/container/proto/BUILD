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

package(default_applicable_licenses = ["//:license"])

proto_library(
    name = "mode_setting_proto",
    srcs = ["mode_setting.proto"],
    visibility = [
        "//src/devtools/mobileharness/infra/lab/proto:__pkg__",
    ],
)

java_proto_library(
    name = "mode_setting_java_proto",
    visibility = [
        "//src/java/com/google/devtools/mobileharness/infra/client:__subpackages__",
        "//src/java/com/google/devtools/mobileharness/infra/lab/rpc:__subpackages__",
        "//src/java/com/google/wireless/qa/mobileharness/shared/model/job/util:__pkg__",
        "//src/javatests/com/google/devtools/mobileharness/infra/client:__subpackages__",
    ],
    deps = [":mode_setting_proto"],
)

proto_library(
    name = "sandbox_setting_proto",
    srcs = ["sandbox_setting.proto"],
    visibility = [
        "//src/devtools/mobileharness/infra/lab/proto:__pkg__",
    ],
)

java_proto_library(
    name = "sandbox_setting_java_proto",
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [":sandbox_setting_proto"],
)

proto_library(
    name = "test_engine_proto",
    srcs = ["test_engine.proto"],
    visibility = [
        "//src/devtools/mobileharness/infra/lab/proto:__pkg__",
    ],
    deps = [
    ],
)

java_proto_library(
    name = "test_engine_java_proto",
    visibility = [
        "//:deviceinfra_all_pkg",
    ],
    deps = [":test_engine_proto"],
)
