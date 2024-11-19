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

package(
    default_applicable_licenses = ["//:license"],
)

proto_library(
    name = "test_args_pb",
    srcs = ["test_args.proto"],
)

java_proto_library(
    name = "test_args_pb_java_proto",
    visibility = [
        "//src/java/com/google/devtools/mobileharness/platform/android/instrumentation:__subpackages__",
    ],
    deps = [":test_args_pb"],
)

proto_library(
    name = "test_info_pb",
    srcs = ["test_info.proto"],
)

java_proto_library(
    name = "test_info_pb_java_proto",
    visibility = [
        "//src/java/com/google/wireless/qa/mobileharness/shared/api/driver:__subpackages__",
    ],
    deps = [":test_info_pb"],
)
