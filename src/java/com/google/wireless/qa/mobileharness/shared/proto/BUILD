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

load("//net/proto2/compiler/stubby/java:java_stubby_library.bzl", "java_stubby_library")
load("//java/com/google/apps/framework/template:builddefs_labs.bzl", "af_soy_proto")
load("//net/proto2/compiler/stubby/cc:cc_stubby_library.bzl", "cc_stubby_library")
load("//tools/build_defs/js/jspb_proto_library:rules.bzl", "jspb_proto_library")
load("//tools/build_defs/proto/cpp:cc_proto_library.bzl", "cc_proto_library")

package(default_visibility = ["//visibility:public"])

exports_files([
    "common.proto",
    "config.proto",
    "job.proto",
])

CC_API_VERSION = 2

PY_API_VERSION = 2

proto_library(
    name = "communication_proto",
    srcs = ["communication.proto"],
)

java_proto_library(
    name = "communication_java_proto",
    deps = [":communication_proto"],
)

proto_library(
    name = "common_proto",
    srcs = ["common.proto"],
)

cc_proto_library(
    name = "common_cc_proto",
    deps = [":common_proto"],
)

java_proto_library(
    name = "common_java_proto",
    deps = [":common_proto"],
)

py_proto_library(
    name = "common_py_pb" + str(PY_API_VERSION),
    api_version = PY_API_VERSION,
    deps = [":common_proto"],
)

jspb_proto_library(
    name = "common_jspb_proto",
    deps = [":common_proto"],
)

af_soy_proto(
    name = "common_soy_proto",
    proto_java = [":common_java_proto"],
    proto_js = [":common_jspb_proto"],
    proto_libs = [":common_proto"],
)

proto_library(
    name = "config_proto",
    srcs = ["config.proto"],
    deps = [
        ":common_proto",
        "//src/devtools/mobileharness/api/model/proto:device_proto",
    ],
)

cc_proto_library(
    name = "config_cc_proto",
    deps = [":config_proto"],
)

go_proto_library(
    name = "common_go_proto",
    deps = [":common_proto"],
)

java_proto_library(
    name = "config_java_proto",
    deps = [":config_proto"],
)

py_proto_library(
    name = "config_py_pb" + str(PY_API_VERSION),
    api_version = PY_API_VERSION,
    deps = [":config_proto"],
)

proto_library(
    name = "job_proto",
    srcs = ["job.proto"],
)

cc_proto_library(
    name = "job_cc_proto",
    deps = [":job_proto"],
)

java_proto_library(
    name = "job_java_proto",
    deps = [":job_proto"],
)

py_proto_library(
    name = "job_py_pb" + str(PY_API_VERSION),
    api_version = PY_API_VERSION,
    deps = [":job_proto"],
)

jspb_proto_library(
    name = "job_jspb_proto",
    deps = [":job_proto"],
)

go_proto_library(
    name = "job_go_proto",
    deps = [":job_proto"],
)

af_soy_proto(
    name = "job_soy_proto",
    proto_java = [":job_java_proto"],
    proto_js = [":job_jspb_proto"],
    proto_libs = [":job_proto"],
)

proto_library(
    name = "job_config_proto",
    srcs = ["job_config.proto"],
    deps = [
        ":job_proto",
        "//net/proto2/proto:descriptor",
        "//src/devtools/mobileharness/api/model/proto:job_proto",
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

py_proto_library(
    name = "job_config_py_pb" + str(PY_API_VERSION),
    api_version = PY_API_VERSION,
    deps = [":job_config_proto"],
)

jspb_proto_library(
    name = "job_config_jspb_proto",
    deps = [":job_config_proto"],
)

proto_library(
    name = "metadata_proto",
    srcs = ["metadata.proto"],
)

cc_proto_library(
    name = "metadata_cc_proto",
    deps = [":metadata_proto"],
)

java_proto_library(
    name = "metadata_java_proto",
    deps = [":metadata_proto"],
)

proto_library(
    name = "file_transfer_serv",
    srcs = ["file_transfer_serv.proto"],
    has_services = 1,
    use_java_stubby_library = True,
    deps = [
        ":common_proto",
    ],
)

java_stubby_library(
    name = "file_transfer_serv_java_stubby",
    srcs = [":file_transfer_serv"],
    deps = [":file_transfer_serv_java_proto"],
)

cc_proto_library(
    name = "file_transfer_serv_cc_proto",
    deps = [":file_transfer_serv"],
)

cc_stubby_library(
    name = "file_transfer_serv_cc_stubby",
    srcs = [":file_transfer_serv"],
    stubby_versions = ["1"],
    deps = [":file_transfer_serv_cc_proto"],
)

java_proto_library(
    name = "file_transfer_serv_java_proto",
    deps = [":file_transfer_serv"],
)

# If you want to reference custom_apk_install to generate APK manifest, please
# whitelist your packages here.
package_group(
    name = "custom_apk_install_manifest_builder",
    includes = [
        "//:deviceinfra_all_pkg",
        "//devtools/mobileharness/project:afw_android_gce",
    ],
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

py_proto_library(
    name = "android_device_spec_py_pb" + str(PY_API_VERSION),
    api_version = PY_API_VERSION,
    visibility = [":custom_apk_install_manifest_builder"],
    deps = [":android_device_spec"],
)

proto_library(
    name = "ios_crawl_proto",
    srcs = [
        "ios_crawl.proto",
    ],
    deps = [
        "//testing/mobileharness/client/proto:open_ios_deep_links_proto",
    ],
)

cc_proto_library(
    name = "ios_crawl_cc_proto",
    deps = [":ios_crawl_proto"],
)

java_proto_library(
    name = "ios_crawl_java_proto",
    deps = [":ios_crawl_proto"],
)
