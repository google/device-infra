import {
  DaemonServerState,
  LabServerReleaseState,
  UiLabType,
} from '../../../models/host_overview';
import {MockHostScenario} from '../models';
import {OVERVIEW_01} from './overview_01';
import {createHostActions, createLabServerActions} from './ui_status_utils';

/** Factory for dynamic host refresh scenario. */
export function overviewRefreshFactory(callCount = 0): MockHostScenario {
  // Simulate failure on the 5th call
  if (callCount % 5 === 0 && callCount > 0) {
    throw new Error(
      `Simulated failure on 5th refresh call for host: refresh-host-name.`,
    );
  }

  const isEven = callCount % 2 === 0;

  const derivedUiLabTypes: UiLabType[] = isEven
    ? ['SATELLITE']
    : ['SATELLITE', 'FUSION'];
  const isCoreLab = derivedUiLabTypes.includes('CORE');
  const isFusionLab = derivedUiLabTypes.includes('FUSION');
  const derivedHostStatus = isEven ? 'MISSING' : 'RUNNING';

  const actions = createHostActions(derivedHostStatus, isCoreLab, isFusionLab);

  const baseOverview = OVERVIEW_01().overview;
  if (!baseOverview) {
    throw new Error('Base overview OVERVIEW_01().overview is undefined');
  }

  const connectivityState = isEven
    ? 'MISSING'
    : baseOverview.labServer?.connectivity?.state || 'RUNNING';
  const activityState: LabServerReleaseState = isEven
    ? 'LAB_SERVER_RELEASE_STATE_STOPPED'
    : baseOverview.daemonServer?.labServerReleaseStatus?.state ||
      'LAB_SERVER_RELEASE_STATE_RUNNING';
  const daemonState: DaemonServerState = isEven
    ? 'MISSING'
    : baseOverview.daemonServer?.status?.state || 'RUNNING';

  const labServerActions = createLabServerActions(
    connectivityState,
    activityState,
    daemonState,
    derivedUiLabTypes,
  );

  const overview = {
    ...baseOverview,
    hostName: 'refresh-host-name',
    os: isEven ? 'Linux (Alternate)' : baseOverview.os,
    uiLabTypes: derivedUiLabTypes,
    labServer: {
      ...baseOverview.labServer,
      version: isEven ? 'v4.99.9 (Updated)' : baseOverview.labServer.version,
      connectivity: {
        ...baseOverview.labServer.connectivity,
        state: isEven ? 'MISSING' : baseOverview.labServer.connectivity.state,
        title: isEven ? 'Missing' : baseOverview.labServer.connectivity.title,
      },
      passThroughFlags: isEven
        ? '--alt_flag=true'
        : baseOverview.labServer.passThroughFlags,
      actions: labServerActions,
    },
    daemonServer: {
      ...baseOverview.daemonServer,
      version: isEven ? 'v5.0.0' : baseOverview.daemonServer.version,
      status: {
        ...baseOverview.daemonServer.status,
        state: isEven ? 'MISSING' : baseOverview.daemonServer.status.state,
        title: isEven ? 'Missing' : baseOverview.daemonServer.status.title,
      },
      labServerReleaseStatus: baseOverview.daemonServer.labServerReleaseStatus
        ? {
            ...baseOverview.daemonServer.labServerReleaseStatus,
            state: (isEven
              ? 'LAB_SERVER_RELEASE_STATE_STOPPED'
              : baseOverview.daemonServer.labServerReleaseStatus?.state ||
                'LAB_SERVER_RELEASE_STATE_RUNNING') as LabServerReleaseState,
            title: isEven
              ? 'Stopped'
              : baseOverview.daemonServer.labServerReleaseStatus?.title ||
                'Running',
          }
        : undefined,
    },
    properties: {
      ...baseOverview.properties,
      'Refresh Count': String(callCount),
      'Last Refreshed At': new Date().toLocaleTimeString(),
      'Simulated State': isEven ? 'Even Refresh' : 'Odd Refresh',
    },
  };

  return {
    ...OVERVIEW_01(),
    scenarioName: 'Host Refresh Test Scenario',
    hostName: 'refresh-host-name',
    overview,
    actions,
  };
}
