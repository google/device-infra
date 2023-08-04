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
    default_visibility = [
        "//:deviceinfra_all_pkg",
    ],
)

proto_library(
    name = "device_config_proto",
    srcs = ["device_config.proto"],
)

proto_library(
    name = "device_descriptor_proto",
    srcs = ["device_descriptor.proto"],
    deps = [
        ":device_config_proto",
        ":device_dimension_proto",
        ":device_info_proto",
        "@com_google_protobuf//:timestamp_proto",
    ],
)

proto_library(
    name = "device_dimension_proto",
    srcs = ["device_dimension.proto"],
)

proto_library(
    name = "device_identifier_proto",
    srcs = ["device_identifier.proto"],
)

proto_library(
    name = "device_info_proto",
    srcs = ["device_info.proto"],
    deps = [
        ":device_type_proto",
        "//src/devtools/deviceinfra/api/deviceinfo/proto/platform/android:android_real_device_info_proto",
    ],
)

proto_library(
    name = "device_locator_proto",
    srcs = ["device_locator.proto"],
)

proto_library(
    name = "device_requirement_proto",
    srcs = ["device_requirement.proto"],
)

proto_library(
    name = "device_type_proto",
    srcs = ["device_type.proto"],
)

java_proto_library(
    name = "device_config_java_proto",
    deps = [":device_config_proto"],
)

java_proto_library(
    name = "device_descriptor_java_proto",
    deps = [":device_descriptor_proto"],
)

java_proto_library(
    name = "device_dimension_java_proto",
    deps = [":device_dimension_proto"],
)

java_proto_library(
    name = "device_identifier_java_proto",
    deps = [":device_identifier_proto"],
)

java_proto_library(
    name = "device_info_java_proto",
    deps = [":device_info_proto"],
)

java_proto_library(
    name = "device_locator_java_proto",
    deps = [":device_locator_proto"],
)

java_proto_library(
    name = "device_requirement_java_proto",
    deps = [":device_requirement_proto"],
)

java_proto_library(
    name = "device_type_java_proto",
    deps = [":device_type_proto"],
)
