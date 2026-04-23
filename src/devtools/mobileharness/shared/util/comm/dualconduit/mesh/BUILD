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

load("@rules_go//go:def.bzl", "go_library", "go_test")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src/devtools/mobileharness/shared/util/comm/dualconduit:__subpackages__"],
)

go_library(
    name = "mesh",
    srcs = ["mesh.go"],
    importpath = "github.com/google/device-infra/src/devtools/mobileharness/shared/util/comm/dualconduit/mesh",
    deps = [
        "@com_github_envoyproxy_go_control_plane_envoy//config/cluster/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//config/core/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//config/endpoint/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//config/listener/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//config/route/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//extensions/filters/http/router/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//extensions/filters/network/http_connection_manager/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//extensions/filters/network/tcp_proxy/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//extensions/upstreams/http/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//service/cluster/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//service/discovery/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//service/endpoint/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//service/listener/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//service/route/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//service/runtime/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//service/secret/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane//pkg/cache/types",
        "@com_github_envoyproxy_go_control_plane//pkg/cache/v3:cache",
        "@com_github_envoyproxy_go_control_plane//pkg/resource/v3:resource",
        "@com_github_envoyproxy_go_control_plane//pkg/server/v3:server",
        "@org_golang_google_grpc//:grpc",
        "@org_golang_google_protobuf//types/known/anypb",
        "@org_golang_google_protobuf//types/known/durationpb",
    ],
)

go_test(
    name = "mesh_test",
    srcs = ["mesh_test.go"],
    embed = [":mesh"],
    deps = [
        "@com_github_envoyproxy_go_control_plane_envoy//config/cluster/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane_envoy//extensions/upstreams/http/v3:go_default_library",
        "@com_github_envoyproxy_go_control_plane//pkg/resource/v3:resource",
    ],
)
