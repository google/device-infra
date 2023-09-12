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

# Mobile Harness shared spec proto files.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//visibility:public"],
)

CC_API_VERSION = 2

PYTHON_API_VERSION = 2

# If you are adding a new spec, please add it as a dependency here. If your spec is
# not in the deps list here, it may not be included in the lab server build and will
# fail to be populated.
proto_library(
    name = "spec",
    deps = [
        ":android_adb_shell_decorator_spec_proto",
        ":android_device_setting_decorator_spec_proto",
        ":base_spec_proto",
        ":job_spec_proto",
        ":no_op_decorator_spec_proto",
        ":no_op_driver_spec_proto",
    ],
)

proto_library(
    name = "base_spec_proto",
    srcs = ["base_spec.proto"],
    deps = [
        "@com_google_protobuf//:descriptor_proto",
    ],
)

java_proto_library(
    name = "base_spec_java_proto",
    deps = [":base_spec_proto"],
)

proto_library(
    name = "job_spec_proto",
    srcs = ["job_spec.proto"],
    deps = [
        ":base_spec_proto",
    ],
)

java_proto_library(
    name = "job_spec_java_proto",
    deps = [":job_spec_proto"],
)

proto_library(
    name = "android_device_setting_decorator_spec_proto",
    srcs = ["decorator/android_device_settings_decorator_spec.proto"],
    deps = [
        ":base_spec_proto",
    ],
)

java_proto_library(
    name = "android_device_setting_decorator_spec_java_proto",
    deps = [":android_device_setting_decorator_spec_proto"],
)

proto_library(
    name = "no_op_driver_spec_proto",
    srcs = ["driver/no_op_driver_spec.proto"],
    deps = [
        ":base_spec_proto",
        "//src/java/com/google/wireless/qa/mobileharness/shared/proto:job_proto",
    ],
)

java_proto_library(
    name = "no_op_driver_spec_java_proto",
    deps = [":no_op_driver_spec_proto"],
)

proto_library(
    name = "no_op_decorator_spec_proto",
    srcs = ["decorator/no_op_decorator_spec.proto"],
    deps = [
        ":base_spec_proto",
        "//src/devtools/mobileharness/api/model/proto:test_proto",
    ],
)

java_proto_library(
    name = "no_op_decorator_spec_java_proto",
    deps = [":no_op_decorator_spec_proto"],
)

proto_library(
    name = "no_op_test_spec",
    deps = [
        ":no_op_decorator_spec_proto",
        ":no_op_driver_spec_proto",
    ],
)

java_proto_library(
    name = "no_op_test_spec_java_proto",
    deps = [":no_op_test_spec"],
)

proto_library(
    name = "android_adb_shell_decorator_spec_proto",
    srcs = ["decorator/android_adb_shell_decorator_spec.proto"],
    deps = [":base_spec_proto"],
)

java_proto_library(
    name = "android_adb_shell_decorator_spec_java_proto",
    deps = [":android_adb_shell_decorator_spec_proto"],
)

proto_library(
    name = "xts_tradefed_test_spec_proto",
    srcs = ["driver/xts_tradefed_test_spec.proto"],
    deps = [":base_spec_proto"],
)

java_proto_library(
    name = "xts_tradefed_test_spec_java_proto",
    deps = [":xts_tradefed_test_spec_proto"],
)

proto_library(
    name = "pixel_buds_flash_decorator_spec_proto",
    srcs = ["decorator/pixel_buds_flash_decorator_spec.proto"],
    deps = [":base_spec_proto"],
)

cc_proto_library(
    name = "pixel_buds_flash_decorator_spec_cc_proto",
    deps = [":pixel_buds_flash_decorator_spec_proto"],
)

java_proto_library(
    name = "pixel_buds_flash_decorator_spec_java_proto",
    deps = [":pixel_buds_flash_decorator_spec_proto"],
)
