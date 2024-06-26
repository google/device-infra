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

# Library to push monitoring data to Cloud.

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = [
        "//src/javatests/com/google/devtools/mobileharness/infra/monitoring:__pkg__",
    ],
)

java_library(
    name = "cloud_pubsub",
    srcs = [
        "BatchPipelineService.java",
        "CloudPubSubPublisher.java",
        "DataPuller.java",
        "DataPusher.java",
        "DataPushers.java",
    ],
    deps = [
        "//src/java/com/google/devtools/mobileharness/api/model/error",
        "//src/java/com/google/devtools/mobileharness/shared/util/logging:google_logger",
        "@com_google_protobuf//:protobuf_java",
        "@com_google_protobuf//:protobuf_java_util",
        "@io_grpc_grpc_java//core",
        "@maven//:com_google_api_api_common",
        "@maven//:com_google_api_grpc_proto_google_cloud_pubsub_v1",
        "@maven//:com_google_auth_google_auth_library_oauth2_http",
        "@maven//:com_google_cloud_google_cloud_pubsub",
        "@maven//:com_google_guava_guava",
    ],
)
