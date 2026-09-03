import {
  FleetColumnCatalogResponse,
  FleetPromotedKeysResponse,
  FleetSearchConfig,
  FleetSearchResults,
  FleetValueListResponse,
  Indicator,
  Row,
} from '@deviceinfra/app/core/models/search';
import {MOCK_DEVICE_SCENARIOS} from '../devices';
import {MOCK_HOST_SCENARIOS} from '../hosts';

function getStatusIndicator(statusText: string): Indicator {
  const sUpper = statusText.toUpperCase();
  if (
    sUpper === 'IDLE' ||
    sUpper === 'READY' ||
    sUpper === 'HEALTHY' ||
    sUpper === 'RUNNING'
  ) {
    return Indicator.INDICATOR_OK;
  }
  if (
    sUpper === 'BUSY' ||
    sUpper === 'ACTIVE' ||
    sUpper === 'DRAINING' ||
    sUpper === 'STARTING'
  ) {
    return Indicator.INDICATOR_ACTIVE;
  }
  return Indicator.INDICATOR_ERROR;
}

/** Generates mock device rows dynamically from all registered mock device scenarios. */
export function generateMockDeviceRows(): Row[] {
  return MOCK_DEVICE_SCENARIOS.map((wrapper) => {
    const scenario = wrapper.factory(0);
    const id = scenario.id || wrapper.id;
    const overview = scenario.overview;
    const hostName = overview?.host?.name || 'mh-lab-01.google.com';
    const statusText =
      overview?.healthAndActivity?.deviceStatus?.status ||
      (scenario.isQuarantined ? 'QUARANTINED' : 'IDLE');

    const type =
      overview?.healthAndActivity?.deviceTypes?.[0]?.type ||
      overview?.basicInfo?.os ||
      'ANDROID';
    const owner = overview?.permissions?.owners?.[0] || 'qiupingf@google.com';
    const model = overview?.basicInfo?.model || 'Pixel';

    return {
      id,
      cells: [
        {
          link: {
            text: id,
            target: {device: {id}},
          },
        },
        {text: {value: hostName}},
        {status: {text: statusText, indicator: getStatusIndicator(statusText)}},
        {text: {value: type}},
        {text: {value: owner}},
        {text: {value: model}},
      ],
    };
  });
}

/** Generates mock host rows dynamically from all registered mock host scenarios. */
export function generateMockHostRows(): Row[] {
  const seen = new Set<string>();
  const rows: Row[] = [];

  for (const wrapper of MOCK_HOST_SCENARIOS) {
    const scenario = wrapper.factory(0);
    const hostName = scenario.hostName || wrapper.hostName;
    if (!hostName || seen.has(hostName)) continue;
    seen.add(hostName);

    const overview = scenario.overview;
    const statusText = overview?.labServer?.connectivity?.state || 'RUNNING';

    const os = overview?.os || 'gLinux';
    const owner =
      scenario.hostConfigResult?.hostConfig?.permissions?.hostAdmins?.[0] ||
      'qiupingf@google.com';
    const model = overview?.uiLabTypes?.[0] || 'Standard';

    rows.push({
      id: hostName,
      cells: [
        {
          link: {
            text: hostName,
            target: {host: {hostName}},
          },
        },
        {text: {value: hostName}},
        {status: {text: statusText, indicator: getStatusIndicator(statusText)}},
        {text: {value: os}},
        {text: {value: owner}},
        {text: {value: model}},
      ],
    });
  }

  return rows;
}

const ALL_MOCK_DEVICE_ROWS = generateMockDeviceRows();
const ALL_MOCK_HOST_ROWS = generateMockHostRows();

/** Mock search configuration for lab fleet search. */
export const MOCK_FLEET_SEARCH_CONFIG: FleetSearchConfig = {
  columns: {
    recommended: [
      {key: 'field::status', displayName: 'Status'},
      {key: 'dim::model', displayName: 'Model'},
    ],
    defaults: [
      {key: 'id', displayName: 'Device ID / UUID', locked: true},
      {key: 'hostName', displayName: 'Host Name'},
      {key: 'status', displayName: 'Status'},
      {key: 'type', displayName: 'Device Type'},
      {key: 'owner', displayName: 'Owner'},
      {key: 'model', displayName: 'Model'},
    ],
  },
  landing: {
    enabled: true,
    browseAllCount: ALL_MOCK_DEVICE_ROWS.length,
    tryCategories: [
      {
        label: 'a value',
        examples: [{text: 'pixel 7'}, {text: 'IDLE'}, {text: 'ANDROID'}],
      },
      {
        label: 'a key',
        examples: [{text: 'Model'}, {text: 'Status'}, {text: 'Type'}],
      },
    ],
  },
};

/** Mock search results for device entity type. */
export const MOCK_FLEET_SEARCH_RESULTS_DEVICE: FleetSearchResults = {
  flat: {
    total: ALL_MOCK_DEVICE_ROWS.length,
    rangeStart: ALL_MOCK_DEVICE_ROWS.length > 0 ? 1 : 0,
    rangeEnd: ALL_MOCK_DEVICE_ROWS.length,
    rows: ALL_MOCK_DEVICE_ROWS,
  },
};

/** Mock search results for host entity type. */
export const MOCK_FLEET_SEARCH_RESULTS_HOST: FleetSearchResults = {
  flat: {
    total: ALL_MOCK_HOST_ROWS.length,
    rangeStart: ALL_MOCK_HOST_ROWS.length > 0 ? 1 : 0,
    rangeEnd: ALL_MOCK_HOST_ROWS.length,
    rows: ALL_MOCK_HOST_ROWS,
  },
};

/** Mock promoted filter and group keys for fleet. */
export const MOCK_FLEET_PROMOTED_KEYS: FleetPromotedKeysResponse = {
  filterKeys: [
    {
      key: 'field::status',
      metadata: {
        keyDisplayName: 'Status',
        canUseAdvanced: true,
        isPlural: false,
      },
    },
    {
      key: 'dim::model',
      metadata: {
        keyDisplayName: 'Model',
        canUseAdvanced: true,
        isPlural: false,
      },
    },
    {
      key: 'field::type',
      metadata: {keyDisplayName: 'Type', canUseAdvanced: true, isPlural: true},
    },
    {
      key: 'field::owners',
      metadata: {
        keyDisplayName: 'Owner',
        canUseAdvanced: true,
        isPlural: true,
      },
    },
    {
      key: 'dim::pool',
      metadata: {
        keyDisplayName: 'Pool',
        canUseAdvanced: true,
        isPlural: false,
      },
    },
    {
      key: 'host::host_name',
      metadata: {
        keyDisplayName: 'Host Name',
        canUseAdvanced: true,
        isPlural: false,
      },
    },
    {
      key: 'field::quarantined',
      metadata: {
        keyDisplayName: 'Quarantine',
        canUseAdvanced: false,
        isPlural: false,
      },
    },
    {
      key: 'dim::os',
      metadata: {
        keyDisplayName: 'OS',
        canUseAdvanced: true,
        isPlural: false,
      },
    },
  ],
  groupByKeys: [
    {key: 'status', displayName: 'Status', groupCount: 4},
    {key: 'model', displayName: 'Model', groupCount: 15},
    {key: 'type', displayName: 'Type', groupCount: 3},
    {key: 'pool', displayName: 'Pool', groupCount: 8},
  ],
};

/** Mock column catalog response. */
export const MOCK_FLEET_COLUMN_CATALOG: FleetColumnCatalogResponse = {
  sections: [
    {
      heading: 'Suggested for you',
      entries: [
        {key: 'status', displayName: 'Status', deviceCount: 14},
        {key: 'model', displayName: 'Model', deviceCount: 14},
      ],
    },
  ],
};

/** Mock status values checklist selector. */
export const MOCK_FLEET_VALUE_LIST_STATUS: FleetValueListResponse = {
  counted: {
    values: [
      {value: 'IDLE', displayLabel: 'IDLE', filtered: 8, total: 10},
      {value: 'BUSY', displayLabel: 'BUSY', filtered: 5, total: 6},
      {value: 'OFFLINE', displayLabel: 'OFFLINE', filtered: 1, total: 2},
      {value: 'INIT', displayLabel: 'INIT', filtered: 0, total: 1},
    ],
  },
};

/** Mock model values checklist selector. */
export const MOCK_FLEET_VALUE_LIST_MODEL: FleetValueListResponse = {
  counted: {
    values: [
      {value: 'Pixel 7', displayLabel: 'Pixel 7', filtered: 6, total: 8},
      {value: 'Pixel 8', displayLabel: 'Pixel 8', filtered: 4, total: 5},
      {value: 'Pixel 6', displayLabel: 'Pixel 6', filtered: 2, total: 3},
      {
        value: 'Galaxy S23',
        displayLabel: 'Galaxy S23',
        filtered: 1,
        total: 2,
      },
    ],
    noValueEntry: {filtered: 1, total: 2},
  },
};

/** Mock type values checklist selector. */
export const MOCK_FLEET_VALUE_LIST_TYPE: FleetValueListResponse = {
  counted: {
    values: [
      {
        value: 'ANDROID',
        displayLabel: 'ANDROID',
        filtered: 12,
        total: 15,
      },
      {value: 'IOS', displayLabel: 'IOS', filtered: 2, total: 3},
      {
        value: 'EMULATOR',
        displayLabel: 'EMULATOR',
        filtered: 1,
        total: 1,
      },
    ],
  },
};

/** Mock owner values checklist selector. */
export const MOCK_FLEET_VALUE_LIST_OWNER: FleetValueListResponse = {
  counted: {
    values: [
      {
        value: 'qiupingf@google.com',
        displayLabel: 'qiupingf@google.com',
        filtered: 10,
        total: 12,
      },
      {
        value: 'dev@google.com',
        displayLabel: 'dev@google.com',
        filtered: 4,
        total: 5,
      },
    ],
    noValueEntry: {filtered: 2, total: 3},
  },
};

/** Mock fallback counted values checklist selector. */
export const MOCK_FLEET_VALUE_LIST_DEFAULT: FleetValueListResponse = {
  counted: {
    values: [
      {value: 'Option A', displayLabel: 'Option A', filtered: 5, total: 8},
      {value: 'Option B', displayLabel: 'Option B', filtered: 3, total: 4},
    ],
    noValueEntry: {filtered: 1, total: 1},
  },
};

/** Mock plain text values suggestion/selector list. */
export const MOCK_FLEET_VALUE_LIST_PLAIN: FleetValueListResponse = {
  plain: {
    values: [
      {value: 'device_uuid_001', displayLabel: 'device_uuid_001'},
      {value: 'device_uuid_002', displayLabel: 'device_uuid_002'},
      {value: 'device_uuid_003', displayLabel: 'device_uuid_003'},
    ],
  },
};
