import {
  FleetColumnCatalogResponse,
  FleetPromotedKeysResponse,
  FleetSearchConfig,
  FleetSearchResults,
  FleetSuggestion,
  FleetSuggestionResponse,
  FleetValueListResponse,
  Indicator,
} from '@deviceinfra/app/core/models/search';

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
    browseAllCount: 14,
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
    total: 3,
    rangeStart: 1,
    rangeEnd: 3,
    rows: [
      {
        id: 'device_12345',
        cells: [
          {
            link: {
              text: 'device_12345',
              target: {device: {id: 'device_12345'}},
            },
          },
          {text: {value: 'mh-lab-01.google.com'}},
          {status: {text: 'IDLE', indicator: Indicator.INDICATOR_OK}},
          {text: {value: 'ANDROID'}},
          {text: {value: 'qiupingf@google.com'}},
          {text: {value: 'Pixel 7'}},
        ],
      },
      {
        id: 'device_67890',
        cells: [
          {
            link: {
              text: 'device_67890',
              target: {device: {id: 'device_67890'}},
            },
          },
          {text: {value: 'mh-lab-02.google.com'}},
          {status: {text: 'BUSY', indicator: Indicator.INDICATOR_ACTIVE}},
          {text: {value: 'ANDROID'}},
          {text: {value: 'dev@google.com'}},
          {text: {value: 'Pixel 8'}},
        ],
      },
      {
        id: 'device_abcde',
        cells: [
          {
            link: {
              text: 'device_abcde',
              target: {device: {id: 'device_abcde'}},
            },
          },
          {text: {value: 'mh-lab-01.google.com'}},
          {status: {text: 'OFFLINE', indicator: Indicator.INDICATOR_ERROR}},
          {text: {value: 'IOS'}},
          {text: {value: 'qiupingf@google.com'}},
          {text: {value: 'iPhone 14'}},
        ],
      },
    ],
  },
};

/** Mock search results for host entity type. */
export const MOCK_FLEET_SEARCH_RESULTS_HOST: FleetSearchResults = {
  flat: {
    total: 2,
    rangeStart: 1,
    rangeEnd: 2,
    rows: [
      {
        id: 'mh-lab-01.google.com',
        cells: [
          {
            link: {
              text: 'mh-lab-01.google.com',
              target: {host: {hostName: 'mh-lab-01.google.com'}},
            },
          },
          {text: {value: 'mh-lab-01.google.com'}},
          {status: {text: 'HEALTHY', indicator: Indicator.INDICATOR_OK}},
          {text: {value: 'LINUX'}},
          {text: {value: 'qiupingf@google.com'}},
          {text: {value: 'Standard'}},
        ],
      },
      {
        id: 'mh-lab-02.google.com',
        cells: [
          {
            link: {
              text: 'mh-lab-02.google.com',
              target: {host: {hostName: 'mh-lab-02.google.com'}},
            },
          },
          {text: {value: 'mh-lab-02.google.com'}},
          {status: {text: 'BUSY', indicator: Indicator.INDICATOR_ACTIVE}},
          {text: {value: 'LINUX'}},
          {text: {value: 'dev@google.com'}},
          {text: {value: 'High Memory'}},
        ],
      },
    ],
  },
};

/** Mock default suggestions for device search. */
export const MOCK_FLEET_SUGGESTIONS_DEVICE_DEFAULT: FleetSuggestion[] = [
  {
    label: 'Add filter',
    mainText: [
      {text: 'Status is ', emphasized: false},
      {text: 'IDLE', emphasized: true},
    ],
    count: 95584,
    applyFilter: {
      pillKey: 'Status',
      pillCondition: 'IDLE',
      resultingFilter: {
        key: 'field::status',
        simple: {values: [{value: 'IDLE'}]},
      },
    },
  },
  {
    label: 'Add filter',
    mainText: [
      {text: 'Model is ', emphasized: false},
      {text: 'Pixel 7', emphasized: true},
    ],
    count: 5,
    applyFilter: {
      pillKey: 'Model',
      pillCondition: 'Pixel 7',
      resultingFilter: {
        key: 'dim::model',
        simple: {values: [{value: 'Pixel 7'}]},
      },
    },
  },
  {
    openPicker: {
      key: 'field::type',
      metadata: {keyDisplayName: 'Type', canUseAdvanced: true, isPlural: true},
    },
  },
  {
    openPicker: {
      key: 'field::owners',
      metadata: {keyDisplayName: 'Owner', canUseAdvanced: true, isPlural: true},
    },
  },
  {
    openPicker: {
      key: 'dim::pool',
      metadata: {keyDisplayName: 'Pool', canUseAdvanced: true, isPlural: false},
    },
  },
  {
    openPicker: {
      key: 'host::host_name',
      metadata: {
        keyDisplayName: 'Host Name',
        canUseAdvanced: true,
        isPlural: false,
      },
    },
  },
  {
    openPicker: {
      key: 'field::quarantined',
      metadata: {
        keyDisplayName: 'Quarantine',
        canUseAdvanced: false,
        isPlural: false,
      },
    },
  },
  {
    openPicker: {
      key: 'dim::os',
      metadata: {keyDisplayName: 'OS', canUseAdvanced: true, isPlural: false},
    },
  },
];

/** Mock suggestion response for device search. */
export const MOCK_FLEET_SUGGESTION_RESPONSE_DEVICE: FleetSuggestionResponse = {
  items: MOCK_FLEET_SUGGESTIONS_DEVICE_DEFAULT,
};

/** Mock suggestion items for host search. */
export const MOCK_FLEET_SUGGESTIONS_HOST_DEFAULT: FleetSuggestion[] = [
  {
    label: 'Add filter',
    mainText: [
      {text: 'Status is ', emphasized: false},
      {text: 'RUNNING', emphasized: true},
    ],
    count: 15,
    applyFilter: {
      pillKey: 'Status',
      pillCondition: 'RUNNING',
    },
  },
  {
    label: 'Add filter',
    mainText: [
      {text: 'Host contains ', emphasized: false},
      {text: 'host', emphasized: true},
    ],
    count: 3,
    applyFilter: {
      pillKey: 'Host Name',
      pillCondition: 'host',
    },
  },
];

/** Mock suggestion response for host search. */
export const MOCK_FLEET_SUGGESTION_RESPONSE_HOST: FleetSuggestionResponse = {
  items: MOCK_FLEET_SUGGESTIONS_HOST_DEFAULT,
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
