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

# Mobile Harness shared proto files.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

# If you want to reference custom_apk_install to generate APK manifest, please
# whitelist your packages here.
package_group(
    name = "custom_apk_install_manifest_builder",
    includes = [
        "//:deviceinfra_all_pkg",
    ],
)

exports_files([
    "common.proto",
    "job.proto",
])

proto_library(
    name = "common_proto",
    srcs = ["common.proto"],
)

java_proto_library(
    name = "common_java_proto",
    deps = [":common_proto"],
)

proto_library(
    name = "communication_proto",
    srcs = ["communication.proto"],
)

java_proto_library(
    name = "communication_java_proto",
    deps = [":communication_proto"],
)

proto_library(
    name = "device_query_proto",
    srcs = ["device_query.proto"],
    deps = [":lab_query_proto"],
)

java_proto_library(
    name = "device_query_java_proto",
    deps = [":device_query_proto"],
)

proto_library(
    name = "job_proto",
    srcs = ["job.proto"],
)

java_proto_library(
    name = "job_java_proto",
    deps = [":job_proto"],
)

proto_library(
    name = "job_config_proto",
    srcs = ["job_config.proto"],
    deps = [
        ":job_proto",
        "//src/devtools/mobileharness/api/model/proto:job_proto",
        "@com_google_protobuf//:descriptor_proto",
    ],
)

cc_proto_library(
    name = "job_config_cc_proto",
    deps = [":job_config_proto"],
)

java_proto_library(
    name = "job_config_java_proto",
    deps = [":job_config_proto"],
)

proto_library(
    name = "lab_query_proto",
    srcs = ["lab_query.proto"],
)

proto_library(
    name = "android_device_spec",
    srcs = [
        "android_device_spec.proto",
    ],
    visibility = [":custom_apk_install_manifest_builder"],
)

cc_proto_library(
    name = "android_device_spec_cc_proto",
    visibility = [":custom_apk_install_manifest_builder"],
    deps = [":android_device_spec"],
)

java_proto_library(
    name = "android_device_spec_java_proto",
    visibility = [":custom_apk_install_manifest_builder"],
    deps = [":android_device_spec"],
)

java_lite_proto_library(
    name = "android_device_spec_java_proto_lite",
    visibility = [":custom_apk_install_manifest_builder"],
    deps = [":android_device_spec"],
)
