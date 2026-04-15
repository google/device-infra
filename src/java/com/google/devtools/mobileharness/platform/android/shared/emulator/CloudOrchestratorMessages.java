/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.platform.android.shared.emulator;

import com.google.api.client.util.Key;
import java.util.List;
import java.util.Map;

/**
 * Messages for Cloud Orchestrator API.
 *
 * <p>These messages are based on the Go API definitions found in:
 *
 * <ul>
 *   <li>{@code google3/third_party/cloud_android_orchestration/api/v1/instancemanager.go} (Host
 *       messages)
 *   <li>{@code
 *       google3/third_party/android_cuttlefish/frontend/src/host_orchestrator/api/v1/messages.go}
 *       (CVD messages)
 * </ul>
 */
public final class CloudOrchestratorMessages {

  private CloudOrchestratorMessages() {}

  /** Represents a build from ci.android.com. */
  public static class AndroidCiBuild {
    @Key("branch")
    public String branch;

    @Key("build_id")
    public String buildId;

    @Key("target")
    public String target;

    public AndroidCiBuild() {}

    public AndroidCiBuild(String branch, String buildId, String target) {
      this.branch = branch;
      this.buildId = buildId;
      this.target = target;
    }
  }

  /** Represents a bundle of artifacts from CI. */
  public static class AndroidCiBundle {
    @Key("build")
    public AndroidCiBuild build;

    @Key("type")
    public long type;

    public AndroidCiBundle() {}

    public AndroidCiBundle(AndroidCiBuild build, long type) {
      this.build = build;
      this.type = type;
    }
  }

  /** Request for fetching artifacts. */
  public static class FetchArtifactsRequest {
    @Key("android_ci_bundle")
    public AndroidCiBundle androidCiBundle;

    public FetchArtifactsRequest() {}

    public FetchArtifactsRequest(AndroidCiBundle androidCiBundle) {
      this.androidCiBundle = androidCiBundle;
    }
  }

  /** Response for fetching artifacts. */
  public static class FetchArtifactsResponse {
    @Key("android_ci_bundle")
    public AndroidCiBundle androidCiBundle;
  }

  /** Represents a CVD. */
  public static class Cvd {
    @Key("group")
    public String group;

    @Key("name")
    public String name;

    @Key("status")
    public String status;

    @Key("displays")
    public List<String> displays;

    @Key("webrtc_device_id")
    public String webrtcDeviceId;

    @Key("adb_serial")
    public String adbSerial;

    @Key("build_source")
    public BuildSource buildSource;
  }

  /** Represents the artifacts source for CVD. */
  public static class BuildSource {
    @Key("android_ci_build_source")
    public AndroidCiBuildSource androidCiBuildSource;

    public BuildSource() {}

    public BuildSource(AndroidCiBuildSource androidCiBuildSource) {
      this.androidCiBuildSource = androidCiBuildSource;
    }
  }

  /** Builds from ci.android.com. */
  public static class AndroidCiBuildSource {
    @Key("main_build")
    public AndroidCiBuild mainBuild;

    @Key("kernel_build")
    public AndroidCiBuild kernelBuild;

    @Key("bootloader_build")
    public AndroidCiBuild bootloaderBuild;

    @Key("system_image_build")
    public AndroidCiBuild systemImageBuild;

    public AndroidCiBuildSource() {}

    public AndroidCiBuildSource(AndroidCiBuild mainBuild) {
      this.mainBuild = mainBuild;
    }
  }

  /** Request for creating a CVD. */
  public static class CreateCvdRequest {
    @Key("env_config")
    public Map<String, Object> envConfig;

    /**
     * @deprecated Use {@link #envConfig} field.
     */
    @Key("cvd")
    @Deprecated
    public Cvd cvd;

    /**
     * @deprecated Use {@link #envConfig} field.
     */
    @Key("additional_instances_num")
    @Deprecated
    public long additionalInstancesNum;

    public CreateCvdRequest() {}

    public CreateCvdRequest(Map<String, Object> envConfig) {
      this.envConfig = envConfig;
    }

    public CreateCvdRequest(Cvd cvd) {
      this.cvd = cvd;
    }
  }

  /** Response for creating a CVD. */
  public static class CreateCvdResponse {
    @Key("cvds")
    public List<Cvd> cvds;
  }

  /** Long running operation. */
  public static class Operation {
    @Key("name")
    public String name;

    @Key("done")
    public boolean done;

    @Key("error")
    public OperationError error;

    @Key("response")
    public Map<String, Object> response;
  }

  /** Error in operation. */
  public static class OperationError {
    @Key("code")
    public int code;

    @Key("message")
    public String message;
  }

  /** Host instance information. */
  public static class HostInstance {
    @Key("name")
    public String name;

    @Key("docker")
    public DockerInstance docker;

    @Key("gcp")
    public GcpInstance gcp;

    public HostInstance() {}

    public HostInstance(String name) {
      this.name = name;
    }
  }

  /** Docker specific host properties. */
  public static class DockerInstance {
    @Key("image_name")
    public String imageName;

    @Key("ip_address")
    public String ipAddress;

    public DockerInstance() {}

    public DockerInstance(String imageName) {
      this.imageName = imageName;
    }
  }

  /** GCP specific host properties. */
  public static class GcpInstance {
    @Key("machine_type")
    public String machineType;

    public GcpInstance() {}

    public GcpInstance(String machineType) {
      this.machineType = machineType;
    }
  }

  /** Request for creating a host. */
  public static class CreateHostRequest {
    @Key("host_instance")
    public HostInstance hostInstance;

    public CreateHostRequest() {}

    public CreateHostRequest(HostInstance hostInstance) {
      this.hostInstance = hostInstance;
    }
  }

  /** Response for listing hosts. */
  public static class ListHostsResponse {
    @Key("items")
    public List<HostInstance> items;
  }

  /** Response for listing CVDs. */
  public static class ListCvdsResponse {
    @Key("cvds")
    public List<Cvd> cvds;
  }
}
