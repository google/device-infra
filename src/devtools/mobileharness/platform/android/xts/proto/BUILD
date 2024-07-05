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

DEFAULT_VISIBILITY = [
    "//:deviceinfra_all_pkg",
]

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = DEFAULT_VISIBILITY,
)

proto_library(
    name = "configuration_proto",
    srcs = ["configuration.proto"],
)

java_proto_library(
    name = "configuration_java_proto",
    deps = [":configuration_proto"],
)

proto_library(
    name = "device_configuration_proto",
    srcs = ["device_configuration.proto"],
)

java_proto_library(
    name = "device_configuration_java_proto",
    deps = [":device_configuration_proto"],
)

proto_library(
    name = "runtime_info_proto",
    srcs = ["runtime_info.proto"],
    deps = [
        "@com_google_protobuf//:timestamp_proto",
    ],
)

java_proto_library(
    name = "runtime_info_java_proto",
    deps = [":runtime_info_proto"],
)

proto_library(
    name = "test_message_proto",
    srcs = ["test_message.proto"],
)

java_proto_library(
    name = "test_message_java_proto",
    deps = [":test_message_proto"],
)
