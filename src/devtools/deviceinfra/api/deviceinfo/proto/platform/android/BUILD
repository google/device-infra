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
    name = "android_real_device_info_proto",
    srcs = ["android_real_device_info.proto"],
    visibility = [
        "//src/devtools/deviceinfra/api/deviceinfo/proto:__pkg__",
    ],
)

java_proto_library(
    name = "android_real_device_info_java_proto",
    deps = [":android_real_device_info_proto"],
)
