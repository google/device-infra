/**
 * @fileoverview Utility functions for creating UI status objects for mock data.
 */

import {DeviceActions} from '../../../models/device_action';
import {
  HostActions,
  LabServerActions,
  PreflightLabServerReleaseResponse,
  ReleaseDetails,
} from '../../../models/host_action';
import {
  Editability,
  HostConfigUiStatus,
  PartStatus,
} from '../../../models/host_config_models';
import {HostOverview} from '../../../models/host_overview';

const EDITABLE: Editability = {editable: true};
const VISIBLE_EDITABLE: PartStatus = {visible: true, editability: EDITABLE};

/**
 * Creates a default HostConfigUiStatus where all parts are visible and editable.
 */
export function createDefaultUiStatus(): HostConfigUiStatus {
  return {
    hostAdmins: {...VISIBLE_EDITABLE},
    deviceConfigMode: {...VISIBLE_EDITABLE},
    deviceConfig: {
      sectionStatus: {...VISIBLE_EDITABLE},
      subSections: {},
    },
    hostProperties: {
      sectionStatus: {...VISIBLE_EDITABLE},
    },
    deviceDiscovery: {...VISIBLE_EDITABLE},
  };
}

/**
 * Creates a PartStatus object.
 */
export function createPartStatus(
  visible: boolean,
  editable = false,
  reason?: string,
): PartStatus {
  if (!visible) {
    return {visible: false};
  }
  return {
    visible: true,
    editability: {editable, ...(reason && {reason})},
  };
}

function createDefaultReleaseDetails(): ReleaseDetails {
  return {
    changeLogs: [
      {
        name: 'Bug Fixes',
        items: [
          {
            change: {
              cl: 858423150,
              author: 'test_author',
              text: 'test_text',
              bugs: [123456789],
            },
          },
        ],
      },
    ],
    syncCommands: [
      {
        name: 'test_sync_command',
        command: 'test_command',
      },
    ],
  };
}

/**
 * Creates default release configs for mock scenarios.
 */
export function createDefaultReleaseResponse(): PreflightLabServerReleaseResponse {
  return {
    ready: {
      versions: [
        {
          name: 'mobileharness_lab_server',
          version: 'v4.349.0',
          status: 'Latest',
          buildTime: '2024-03-15 21:30:00',
          ports: [{protocol: 'grpc', portNumber: 9994}],
          releaseDetails: {
            changeLogs: [
              {
                name: 'Bug Fixes',
                items: [
                  {
                    change: {
                      cl: 858423150,
                      author: 'huiliu',
                      text: 'Prevent lab-resolved files from being escaped in KickOffTestRequest.',
                      bugs: [443716027, 468693971],
                    },
                  },
                  {
                    change: {
                      cl: 858945464,
                      author: 'huiliu',
                      text: 'Resolve test files cannot be sent issue.',
                      bugs: [468693971],
                    },
                  },
                ],
              },
              {
                name: 'Incremental Changes',
                items: [
                  {
                    change: {
                      cl: 858343140,
                      author: 'huiliu',
                      text: 'Support to set different master spec for different jobs in one session.',
                      bugs: [476920060],
                    },
                  },
                ],
              },
              {
                name: 'Deprecated',
                items: [
                  {
                    change: {
                      cl: 858854515,
                      author: 'zeek',
                      text: 'Remove the logic to limit iOS simulator count based on disk type.',
                      bugs: [473739492],
                    },
                  },
                ],
              },
            ],
            syncCommands: [
              {
                name: 'copy_tf_config',
                command:
                  'cp -f @android_tradefed_test_config /usr/local/google/mobileharness/android_tradefed_test_config_key',
              },
              {
                name: 'copy_tf_credential',
                command:
                  'cp -f @android_tradefed_credential_key /usr/local/google/mobileharness/android_tradefed_test_key',
              },
              {
                name: 'prepare_dual_home_lab_dir',
                command:
                  'mkdir -p /etc/dual_home_lab/agent && chmod -R 775 /etc/dual_home_lab/',
              },
              {
                name: 'copy_credential',
                command:
                  'cp -f @udcluster_daemon_credential /etc/dual_home_lab/agent/dual-home-lab.json',
              },
              {
                name: 'remove_mh_default_credential',
                command:
                  'rm -f /usr/local/google/mobileharness/mh_default_credential.json',
              },
              {
                name: 'copy_robo_credential',
                command:
                  'cp -f @default_robo_credential /usr/local/google/mobileharness/default_robo_credential.json',
              },
              {
                name: 'copy_file_transfer_credential',
                command:
                  'cp -f @file_transfer_credential /usr/local/google/mobileharness/file_transfer_credential.json',
              },
              {
                name: 'copy_web_flashstation_key',
                command:
                  'cp -f @web_flashstation_key /usr/local/google/mobileharness/web_flashstation_key.json',
              },
              {
                name: 'add_read_permission_of_credential',
                command: 'chmod +r /etc/dual_home_lab/agent/dual-home-lab.json',
              },
              {
                name: 'process_before_killing',
                command: 'ps xao pid,ppid,pgid,command',
              },
              {
                name: 'kill_zombie_lab_script',
                command: '@legacy_daemon_killer',
              },
              {
                name: 'process_after_killing',
                command: 'ps xao pid,ppid,pgid,command',
              },
              {
                name: 'clean_previous_lab_server',
                command:
                  '/usr/local/buildtools/java/jdk21/bin/java --uid= -loas_pwd_fallback_in_corp -jar @cleaner',
              },
            ],
            asyncCommands: [
              {
                name: 'start_command_server',
                command:
                  '@command_server_binary --launcher_javabase=/usr/local/buildtools/java/jdk21 --uid= -loas_pwd_fallback_in_corp run',
              },
              {
                name: 'start_mobileharness_lab_server',
                command:
                  '@binary --launcher_javabase=/usr/local/buildtools/java/jdk21 --uid= -loas_pwd_fallback_in_corp -Xms3g -Xmx8g run ...',
              },
            ],
            files: [
              {
                name: 'kill_zombie_lab_script',
                path: 'gs://release-resource/kill_zombie_lab.sh',
              },
              {
                name: 'command_server_binary',
                path: 'gs://release-resource/mobileharness/command_server_deploy.jar',
              },
              {
                name: 'binary',
                path: 'gs://release-resource/mobileharness/lab_server_deploy_4.349.0.jar',
              },
              {
                name: 'cleaner',
                path: 'gs://release-resource/mobileharness/lab_cleaner_deploy_4.349.0.jar',
              },
              {
                name: 'web_flashstation_key',
                path: 'gs://release-resource/keys/mh_web_flashstation.json',
              },
              {
                name: 'legacy_daemon_killer',
                path: 'gs://release-resource/kill_legacy_daemon_server.sh',
              },
              {
                name: 'android_tradefed_test_config',
                path: 'gs://release-resource/keys/android_tradefed_test_config_key',
              },
            ],
          },
        },
        {
          name: 'release_configs',
          version: 'v4.358.0',
          status: '',
          buildTime: '2025-03-14 12:00:00',
          ports: [
            {
              protocol: 'TCP',
              portNumber: 8080,
            },
            {
              protocol: 'TCP',
              portNumber: 8081,
            },
          ],
          releaseDetails: createDefaultReleaseDetails(),
        },
        {
          name: 'release_configs',
          version: 'v4.357.0',
          status: 'Current',
          buildTime: '2025-03-13 12:00:00',
          ports: [
            {
              protocol: 'TCP',
              portNumber: 8080,
            },
          ],
          releaseDetails: createDefaultReleaseDetails(),
        },
        {
          name: 'release_configs',
          version: 'v4.356.0',
          status: '',
          buildTime: '2025-03-12 12:00:00',
          releaseDetails: createDefaultReleaseDetails(),
        },
        {
          name: 'Host Release Config',
          version: 'v4.355.0',
          status: '',
          buildTime: '2025-03-11 12:00:00',
          releaseDetails: createDefaultReleaseDetails(),
        },
      ],
    },
  };
}

/**
 * Creates a default HostOverview with basic running status.
 */
export function createDefaultHostOverview(hostName: string): HostOverview {
  return {
    hostName,
    ip: '192.168.1.1',
    os: 'gLinux',
    canUpgrade: false,
    labTypeDisplayNames: ['Satellite Lab'],
    labServer: {
      connectivity: {
        state: 'RUNNING',
        title: 'Running',
        tooltip: 'Host is running and connected.',
      },
      activity: {
        state: 'STARTED',
        title: 'Started',
        tooltip: 'Lab Server is started.',
      },
      version: 'R123.45.6',
      passThroughFlags: '',
      actions: createLabServerActions('RUNNING'),
    },
    daemonServer: {
      status: {
        state: 'RUNNING',
        title: 'Running',
        tooltip: 'Daemon Server is running.',
      },
      version: '24.08.01',
    },
    properties: {},
    diagnosticLinks: [
      {
        label: 'Tradefed Log',
        url: 'http://example.com/tradefed_log',
        category: 'LAB_SERVER',
      },
      {
        label: 'Test Log',
        url: 'http://example.com/test_log',
        category: 'DAEMON_SERVER',
      },
      {
        label: 'Host Statusz',
        url: 'http://example.com/host_statusz',
        category: 'OVERVIEW',
      },
    ],
  };
}

/**
 * Creates default device actions for mock scenarios.
 */
export function createDeviceActions(
  permissionState:
    | 'NO_PERMISSION'
    | 'USER_PERMISSION'
    | 'GROUP_PERMISSION'
    | 'ALL_PERMISSIONS' = 'ALL_PERMISSIONS',
): DeviceActions {
  return {
    remoteControl: {
      enabled: true,
      visible: true,
      tooltip: permissionState,
      isReady: true,
    },
    decommission: {
      enabled: true,
      visible: true,
      tooltip: permissionState,
      isReady: true,
    },
    configuration: {
      enabled: true,
      visible: true,
      tooltip: permissionState,
      isReady: true,
    },
    screenshot: {
      enabled: true,
      visible: true,
      tooltip: permissionState,
      isReady: true,
    },
    logcat: {
      enabled: true,
      visible: true,
      tooltip: permissionState,
      isReady: true,
    },
    flash: {
      params: {
        deviceType: 'AndroidRealDevice',
        requiredDimensions: 'required_dimensions',
      },
      enabled: true,
      visible: true,
      tooltip: permissionState,
      isReady: true,
    },
    quarantine: {
      enabled: true,
      visible: true,
      tooltip: permissionState,
      isReady: true,
    },
  };
}

/**
 * Creates HostActions based on the host state.
 * @param status The status of the host (RUNNING, STOPPED, MISSING, ERROR, etc.)
 * @param isCoreLab Whether the host belongs to a Shared/Core Lab.
 */
export function createHostActions(
  status = 'RUNNING',
  isCoreLab = false,
): HostActions {
  const isMissing = status === 'MISSING';
  const manageHostEnabled = !isCoreLab;
  const removeEnabled = isMissing;

  return {
    configuration: {
      enabled: manageHostEnabled,
      visible: true,
      tooltip: manageHostEnabled
        ? 'Configure host properties'
        : 'Configuration is not available for Shared Labs',
      isReady: true,
    },
    debug: {
      enabled: true,
      visible: true,
      tooltip: 'Run and view live diagnostic commands on the host',
      isReady: true,
    },
    decommission: {
      enabled: removeEnabled,
      visible: removeEnabled,
      tooltip: removeEnabled
        ? 'Decommission this missing host record from OmniLab'
        : '',
      isReady: true,
    },
  };
}

/**
 * Creates LabServerActions based on the host state.
 * @param status The status of the lab server (RUNNING, STOPPED, MISSING, ERROR, etc.)
 * @param isCoreLab Whether the host belongs to a Shared/Core Lab.
 */
export function createLabServerActions(
  status = 'RUNNING',
  isCoreLab = false,
): LabServerActions {
  const isMissing = status === 'MISSING';
  const isRunning = status === 'RUNNING';
  const actionEnabled = !isCoreLab;

  return {
    release: {
      enabled: !isMissing && actionEnabled,
      visible: true,
      tooltip: isMissing
        ? 'Cannot release a missing host'
        : isCoreLab
          ? 'Cannot release in a Shared Lab'
          : '',
      isReady: true,
    },
    deploy: {
      enabled: !isMissing && actionEnabled,
      visible: true,
      tooltip: isMissing
        ? 'Cannot deploy to a missing host'
        : isCoreLab
          ? 'Cannot deploy in a Shared Lab'
          : '',
      isReady: false,
    },
    start: {
      enabled: !isRunning && !isMissing && actionEnabled,
      visible: !isRunning && !isMissing,
      tooltip: isRunning
        ? 'Lab server is already running'
        : isCoreLab
          ? 'Cannot start in a Shared Lab'
          : '',
      isReady: true,
    },
    restart: {
      enabled: isRunning && actionEnabled,
      visible: true,
      tooltip: !isRunning
        ? 'Lab server must be running to restart'
        : isCoreLab
          ? 'Cannot restart in a Shared Lab'
          : '',
      isReady: true,
    },
    stop: {
      enabled: isRunning && actionEnabled,
      visible: true,
      tooltip: !isRunning
        ? 'Lab server is not running'
        : isCoreLab
          ? 'Cannot stop in a Shared Lab'
          : '',
      isReady: true,
    },
    updatePassThroughFlags: {
      enabled: !isMissing && actionEnabled,
      visible: true,
      tooltip: isMissing
        ? 'Cannot update flags for a missing host'
        : isCoreLab
          ? 'Cannot update flags in a Shared Lab'
          : '',
      isReady: true,
    },
  };
}
