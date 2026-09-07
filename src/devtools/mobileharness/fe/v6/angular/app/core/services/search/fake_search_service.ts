import {inject, Injectable} from '@angular/core';
import {Observable, of} from 'rxjs';
import {APP_DATA, AppData, getAppData} from '../../models/app_data';
import {
  Cell,
  Column,
  Filter,
  FleetChipResolverRequest,
  FleetChipResolverResponse,
  FleetColumnCatalogEntry,
  FleetColumnCatalogRequest,
  FleetColumnCatalogResponse,
  FleetColumnCatalogSection,
  FleetPromotedKeysRequest,
  FleetPromotedKeysResponse,
  FleetSearchConfig,
  FleetSearchConfigRequest,
  FleetSearchRequest,
  FleetSearchResults,
  FleetSuggestion,
  FleetSuggestionRequest,
  FleetSuggestionResponse,
  FleetValueListRequest,
  FleetValueListResponse,
  Row,
  SearchEntity,
  TjsEntity,
  TjsFilter,
  TjsResolveChipsRequest,
  TjsResolveChipsResponse,
  TjsSearchConfig,
  TjsSearchConfigRequest,
  TjsSearchRequest,
  TjsSearchResponse,
  TjsSuggestion,
  TjsSuggestionRequest,
  TjsSuggestionResponse,
} from '../../models/search';
import {
  MOCK_FLEET_COLUMN_CATALOG,
  MOCK_FLEET_PROMOTED_KEYS,
  MOCK_FLEET_SEARCH_CONFIG,
  MOCK_FLEET_SEARCH_RESULTS_DEVICE,
  MOCK_FLEET_SEARCH_RESULTS_HOST,
  MOCK_FLEET_VALUE_LIST_DEFAULT,
  MOCK_FLEET_VALUE_LIST_MODEL,
  MOCK_FLEET_VALUE_LIST_OWNER,
  MOCK_FLEET_VALUE_LIST_PLAIN,
  MOCK_FLEET_VALUE_LIST_STATUS,
  MOCK_FLEET_VALUE_LIST_TYPE,
  MOCK_TJS_SEARCH_CONFIG_JOB,
  MOCK_TJS_SEARCH_CONFIG_SESSION,
  MOCK_TJS_SEARCH_CONFIG_TEST,
  MOCK_TJS_SEARCH_RESPONSE_JOB,
  MOCK_TJS_SEARCH_RESPONSE_SESSION,
  MOCK_TJS_SEARCH_RESPONSE_TEST,
} from '../mock_data';
import {SearchService} from './search_service';

/** Default user for fake data. */
export const DEFAULT_FAKE_USER = 'qiupingf';

/** Mock implementation of SearchService for frontend testing & offline fallback. */
@Injectable()
export class FakeSearchService extends SearchService {
  private readonly appData: AppData | null = inject(APP_DATA, {optional: true});

  constructor() {
    super();
  }

  private getCurrentUser(): string {
    const email = this.appData?.email || getAppData()?.email;
    if (email && !email.startsWith('tianch@')) {
      return email.split('@')[0];
    }
    return DEFAULT_FAKE_USER;
  }

  override getFleetSearchConfig(
    request: FleetSearchConfigRequest,
  ): Observable<FleetSearchConfig> {
    const isHost = request.entity === SearchEntity.SEARCH_ENTITY_HOST;
    const defaults = isHost
      ? [
          {key: 'id', displayName: 'Host Name', locked: true},
          {key: 'hostName', displayName: 'Host Name'},
          {key: 'status', displayName: 'Status'},
          {key: 'type', displayName: 'OS / Type'},
          {key: 'owner', displayName: 'Owner'},
          {key: 'model', displayName: 'Lab Type'},
        ]
      : MOCK_FLEET_SEARCH_CONFIG.columns?.defaults;

    const count = isHost
      ? MOCK_FLEET_SEARCH_RESULTS_HOST.flat?.total || 0
      : MOCK_FLEET_SEARCH_RESULTS_DEVICE.flat?.total || 0;

    return of({
      ...MOCK_FLEET_SEARCH_CONFIG,
      columns: {
        ...MOCK_FLEET_SEARCH_CONFIG.columns,
        defaults,
      },
      landing: {
        ...MOCK_FLEET_SEARCH_CONFIG.landing,
        browseAllCount: count,
      },
    });
  }

  override searchFleet(
    request: FleetSearchRequest,
  ): Observable<FleetSearchResults> {
    let allRows: Row[] = [];
    if (request.entity === SearchEntity.SEARCH_ENTITY_DEVICE) {
      allRows = MOCK_FLEET_SEARCH_RESULTS_DEVICE.flat?.rows || [];
    } else if (request.entity === SearchEntity.SEARCH_ENTITY_HOST) {
      allRows = MOCK_FLEET_SEARCH_RESULTS_HOST.flat?.rows || [];
    }

    let filteredRows = allRows;
    if (request.filters && request.filters.length > 0) {
      filteredRows = allRows.filter((row) => {
        return request.filters!.every((f) => {
          if (f.key === 'field::status' && f.simple) {
            const statusCell = (
              row.cells?.[2]?.status?.text || ''
            ).toUpperCase();
            const vals = (f.simple.values || []).map((v) =>
              (v.value || '').toUpperCase(),
            );
            if (f.simple.negated) {
              return !vals.includes(statusCell);
            }
            return vals.includes(statusCell);
          }
          if (f.key === 'host::ats_controller' && f.simple) {
            const hostCell = (row.cells?.[1]?.text?.value || '').toLowerCase();
            const vals = (f.simple.values || []).map((v) =>
              (v.value || '').toLowerCase(),
            );
            return vals.some((v) => hostCell.includes(v));
          }
          return true;
        });
      });
    }

    const total = filteredRows.length;
    const pageSize = request.flat?.page?.pageSize || 25;
    const pageToken = request.flat?.page?.pageToken;
    const offset = pageToken ? Number(pageToken) || 0 : 0;
    const pageRows = filteredRows.slice(offset, offset + pageSize);
    const rangeStart = total > 0 ? offset + 1 : 0;
    const rangeEnd = Math.min(offset + pageRows.length, total);
    const nextPageToken =
      offset + pageSize < total ? String(offset + pageSize) : undefined;
    const prevPageToken =
      offset > 0 ? String(Math.max(0, offset - pageSize)) : undefined;

    return of({
      flat: {
        total,
        rangeStart,
        rangeEnd,
        rows: pageRows,
        nextPageToken,
        prevPageToken,
      },
    });
  }

  override getFleetSuggestions(
    request: FleetSuggestionRequest,
  ): Observable<FleetSuggestionResponse> {
    const input = (request.input || '').trim().toLowerCase();
    const activeFilters = request.filters || [];
    const activeKeys = new Set(activeFilters.map((f) => f.key.toLowerCase()));

    if (!input) {
      const keys = [
        {key: 'field::status', name: 'Status', isPlural: false},
        {key: 'dim::model', name: 'Model', isPlural: false},
        {key: 'field::type', name: 'Type', isPlural: true},
        {key: 'field::owners', name: 'Owner', isPlural: true},
        {key: 'dim::pool', name: 'Pool', isPlural: false},
        {key: 'host::host_name', name: 'Host Name', isPlural: false},
        {key: 'field::quarantined', name: 'Quarantine', isPlural: false},
        {key: 'dim::os', name: 'OS', isPlural: false},
      ];

      const items: FleetSuggestion[] = keys.map((k) => {
        const isActive = activeKeys.has(k.key.toLowerCase());
        return {
          label: isActive ? `Modify ${k.name}` : 'Add filter',
          mainText: [{text: k.name, emphasized: false}],
          openPicker: {
            key: k.key,
            metadata: {
              keyDisplayName: k.name,
              canUseAdvanced: true,
              isPlural: k.isPlural,
            },
          },
        };
      });

      return of({items});
    }

    const items: FleetSuggestion[] = [];
    const knownValues = [
      {key: 'dim::model', name: 'Model', val: 'Pixel 8', isPlural: false},
      {key: 'dim::model', name: 'Model', val: 'Pixel 7', isPlural: false},
      {key: 'field::status', name: 'Status', val: 'IDLE', isPlural: false},
      {key: 'field::status', name: 'Status', val: 'BUSY', isPlural: false},
      {key: 'field::status', name: 'Status', val: 'OFFLINE', isPlural: false},
      {key: 'field::type', name: 'Type', val: 'ANDROID', isPlural: true},
      {key: 'field::type', name: 'Type', val: 'IOS', isPlural: true},
    ];

    for (const kv of knownValues) {
      if (
        kv.val.toLowerCase().includes(input) ||
        kv.name.toLowerCase().includes(input)
      ) {
        const isActive = activeKeys.has(kv.key.toLowerCase());
        items.push({
          label: isActive ? `Modify ${kv.name}` : 'Add filter',
          mainText: [
            {
              text: `${kv.name} ${kv.isPlural ? 'are' : 'is'} `,
              emphasized: false,
            },
            {text: kv.val, emphasized: true},
          ],
          count: 10,
          applyFilter: {
            pillKey: kv.name,
            pillCondition: kv.val,
            resultingFilter: {
              key: kv.key,
              simple: {values: [{value: kv.val}]},
            },
            metadata: {
              keyDisplayName: kv.name,
              canUseAdvanced: true,
              isPlural: kv.isPlural,
            },
          },
        });
      }
    }

    if (items.length === 0) {
      items.push({
        label: 'Add filter',
        mainText: [
          {text: 'Search ', emphasized: false},
          {text: request.input, emphasized: true},
        ],
        applyFilter: {
          pillKey: 'Search',
          pillCondition: request.input,
          resultingFilter: {
            key: 'search_query',
            simple: {values: [{value: request.input}]},
          },
        },
      });
    }

    return of({items});
  }

  override resolveFleetChips(
    request: FleetChipResolverRequest,
  ): Observable<FleetChipResolverResponse> {
    return of({
      filterChips: (request.filters || []).map((f) => {
        if (
          f.key === 'invalid_key' ||
          f.key === 'unknown_key' ||
          f.key === 'bad_dim'
        ) {
          return {
            invalid: {
              reason: `Unknown ${request.entity === SearchEntity.SEARCH_ENTITY_HOST ? 'host' : 'device'} filter key: ${f.key}`,
            },
          };
        }

        const pillKey = f.key;
        const cleanKey = f.key.includes('::') ? f.key.split('::')[1] : f.key;
        const displayTitle =
          cleanKey.charAt(0).toUpperCase() +
          cleanKey.slice(1).replace(/_/g, ' ');

        return {
          valid: {
            pillKey,
            pillCondition: this.formatFleetFilterCondition(f),
            metadata: {
              key: f.key,
              keyDisplayName: displayTitle,
              canUseAdvanced: true,
              isPlural: false,
            },
          },
        };
      }),
      groupByChips: (request.groupByKeys || []).map((k) => {
        if (k === 'invalid_key' || k === 'unknown_key' || k === 'bad_group') {
          return {
            invalid: {
              reason: `Unknown ${request.entity === SearchEntity.SEARCH_ENTITY_HOST ? 'host' : 'device'} group-by key: ${k}`,
            },
          };
        }
        return {
          valid: {
            pillKey: k,
            displayName: k,
          },
        };
      }),
    });
  }

  private formatFleetFilterCondition(f: Filter): string {
    if (f.simple?.values && f.simple.values.length > 0) {
      return f.simple.values.map((v) => v.value).join(', ');
    }
    const complex = f.complex;
    if (complex?.startsWith) {
      return `^${complex.startsWith.value}`;
    }
    if (complex?.containsSubstring) {
      return complex.containsSubstring.negated
        ? `!contains:${complex.containsSubstring.value}`
        : `contains:${complex.containsSubstring.value}`;
    }
    if (complex?.matchesRegex) {
      return complex.matchesRegex.negated
        ? `!/${complex.matchesRegex.value}/`
        : `/${complex.matchesRegex.value}/`;
    }
    if (complex?.matchesExactly) {
      return (complex.matchesExactly.values || []).join(', ');
    }
    if (complex?.matchesAtLeast) {
      return (complex.matchesAtLeast.values || []).join(', ');
    }
    return f.key || '';
  }

  override getFleetValueList(
    request: FleetValueListRequest,
  ): Observable<FleetValueListResponse> {
    const k = (request.key || '').toLowerCase();
    if (
      k.includes('uuid') ||
      k.includes('device_id') ||
      k.includes('host_name')
    ) {
      return of(MOCK_FLEET_VALUE_LIST_PLAIN);
    }
    if (k.includes('status')) {
      return of(MOCK_FLEET_VALUE_LIST_STATUS);
    }
    if (k.includes('model')) {
      return of(MOCK_FLEET_VALUE_LIST_MODEL);
    }
    if (k.includes('type')) {
      return of(MOCK_FLEET_VALUE_LIST_TYPE);
    }
    if (k.includes('owner')) {
      return of(MOCK_FLEET_VALUE_LIST_OWNER);
    }
    return of(MOCK_FLEET_VALUE_LIST_DEFAULT);
  }

  override getFleetPromotedKeys(
    request: FleetPromotedKeysRequest,
  ): Observable<FleetPromotedKeysResponse> {
    return of(MOCK_FLEET_PROMOTED_KEYS);
  }

  override getFleetColumnCatalog(
    request: FleetColumnCatalogRequest,
  ): Observable<FleetColumnCatalogResponse> {
    const rawSections = (MOCK_FLEET_COLUMN_CATALOG.sections || []).map((s) => ({
      ...s,
      entries: [...(s.entries || [])],
    }));

    // Dynamically build "Suggested for you" section from request.filters and request.recentKeys if provided
    const suggestedEntries: FleetColumnCatalogEntry[] = [];
    const addedKeys = new Set<string>();

    if (request.filters && request.filters.length > 0) {
      for (const f of request.filters) {
        if (!addedKeys.has(f.key)) {
          addedKeys.add(f.key);
          suggestedEntries.push({
            key: f.key,
            displayName: f.key.startsWith('dim::')
              ? f.key
                  .slice(5)
                  .replace(/[_-]+/g, ' ')
                  .replace(/\b\w/g, (c) => c.toUpperCase())
              : f.key
                  .replace(/[_-]+/g, ' ')
                  .replace(/\b\w/g, (c) => c.toUpperCase()),
            reason: 'Active filter',
          });
        }
      }
    }

    if (request.recentKeys && request.recentKeys.length > 0) {
      for (const k of request.recentKeys) {
        if (!addedKeys.has(k)) {
          addedKeys.add(k);
          suggestedEntries.push({
            key: k,
            displayName: k.startsWith('dim::')
              ? k
                  .slice(5)
                  .replace(/[_-]+/g, ' ')
                  .replace(/\b\w/g, (c) => c.toUpperCase())
              : k
                  .replace(/[_-]+/g, ' ')
                  .replace(/\b\w/g, (c) => c.toUpperCase()),
            reason: 'Recently used',
          });
        }
      }
    }

    const sections: FleetColumnCatalogSection[] = [];
    if (suggestedEntries.length > 0) {
      sections.push({
        heading: 'Suggested for you',
        entries: suggestedEntries,
        totalAvailable: 0,
      });
    } else {
      const defaultSuggested = rawSections.find(
        (s) => s.heading === 'Suggested for you',
      );
      if (defaultSuggested) {
        sections.push(defaultSuggested);
      }
    }

    for (const s of rawSections) {
      if (s.heading !== 'Suggested for you') {
        sections.push(s);
      }
    }

    if (!request.query) {
      return of({sections});
    }

    const q = request.query.toLowerCase().trim();
    const filteredSections = sections
      .map((s) => ({
        ...s,
        entries: (s.entries || []).filter(
          (e: FleetColumnCatalogEntry) =>
            e.displayName.toLowerCase().includes(q) ||
            e.key.toLowerCase().includes(q),
        ),
      }))
      .filter((s) => s.entries && s.entries.length > 0);
    return of({sections: filteredSections});
  }

  override getTjsSearchConfig(
    request: TjsSearchConfigRequest,
  ): Observable<TjsSearchConfig> {
    const entity = request.entity;
    let baseConfig: TjsSearchConfig;
    if (entity === TjsEntity.TJS_ENTITY_TEST) {
      baseConfig = MOCK_TJS_SEARCH_CONFIG_TEST;
    } else if (entity === TjsEntity.TJS_ENTITY_JOB) {
      baseConfig = MOCK_TJS_SEARCH_CONFIG_JOB;
    } else {
      baseConfig = MOCK_TJS_SEARCH_CONFIG_SESSION;
    }

    const currentUser = this.getCurrentUser();
    const defaultChips = baseConfig.defaultChips?.map((chip) => {
      if (chip.filter?.key === 'user') {
        return {
          ...chip,
          pillCondition: currentUser,
          filter: {
            ...chip.filter,
            stringValue: {value: currentUser},
          },
        };
      }
      return chip;
    });

    return of({
      ...baseConfig,
      defaultChips,
    });
  }

  override searchTjs(request: TjsSearchRequest): Observable<TjsSearchResponse> {
    const entity = request.entity;
    let baseResponse: TjsSearchResponse;
    if (entity === TjsEntity.TJS_ENTITY_TEST) {
      baseResponse = MOCK_TJS_SEARCH_RESPONSE_TEST;
    } else if (entity === TjsEntity.TJS_ENTITY_JOB) {
      baseResponse = MOCK_TJS_SEARCH_RESPONSE_JOB;
    } else {
      baseResponse = MOCK_TJS_SEARCH_RESPONSE_SESSION;
    }

    const columns: Column[] = baseResponse.columns || [];
    let rows: Row[] = baseResponse.rows || [];

    const currentUser = this.getCurrentUser();
    const userColIdx = columns.findIndex((c) => c.key === 'user');
    const actualUserColIdx = columns.findIndex((c) => c.key === 'actual_user');

    if (currentUser !== 'qiupingf') {
      rows = rows.map((row) => {
        const cells = row.cells ? [...row.cells] : [];
        if (
          userColIdx !== -1 &&
          cells[userColIdx]?.text?.value === 'qiupingf'
        ) {
          cells[userColIdx] = {
            ...cells[userColIdx],
            text: {value: currentUser},
          };
        }
        if (
          actualUserColIdx !== -1 &&
          cells[actualUserColIdx]?.text?.value === 'qiupingf'
        ) {
          cells[actualUserColIdx] = {
            ...cells[actualUserColIdx],
            text: {value: currentUser},
          };
        }
        return {...row, cells};
      });
    }

    let filteredRows = rows;
    if (request.filters && request.filters.length > 0) {
      filteredRows = rows.filter((row) =>
        request.filters!.every((filter) =>
          this.matchesTjsFilter(row, filter, columns),
        ),
      );
    }

    return of({
      columns,
      rows: filteredRows,
    });
  }

  private matchesTjsFilter(
    row: Row,
    filter: TjsFilter,
    columns: Column[],
  ): boolean {
    const filterKey = (filter.key || '').toLowerCase().trim();
    if (!filterKey) return true;

    let colIndex = columns.findIndex((c) => c.key.toLowerCase() === filterKey);
    if (colIndex === -1 && filterKey === 'id') {
      colIndex = columns.findIndex(
        (c) =>
          c.key.toLowerCase() === 'job_id' ||
          c.key.toLowerCase() === 'session_id' ||
          c.key.toLowerCase() === 'test_id',
      );
    }
    if (
      colIndex === -1 &&
      (filterKey === 'devices' ||
        filterKey === 'device_id' ||
        filterKey === 'device')
    ) {
      colIndex = columns.findIndex((c) => c.key.toLowerCase() === 'devices');
    }

    if (filterKey === 'name') {
      const targetVal = (filter.stringValue?.value || '').toLowerCase().trim();
      if (!targetVal) return true;

      // Check against 'name' column if present
      if (colIndex !== -1) {
        const nameValues = this.extractCellValues(row.cells?.[colIndex]);
        if (nameValues.some((v) => v.toLowerCase().includes(targetVal))) {
          return true;
        }
      }
      // Also allow matching against user column (in case 'name' refers to username)
      const userColIdx = columns.findIndex(
        (c) => c.key.toLowerCase() === 'user',
      );
      if (userColIdx !== -1) {
        const userValues = this.extractCellValues(row.cells?.[userColIdx]);
        if (
          userValues.some(
            (v) =>
              v.toLowerCase() === targetVal ||
              v.toLowerCase().includes(targetVal),
          )
        ) {
          return true;
        }
      }
      return false;
    }

    if (colIndex === -1) {
      if (
        filterKey === 'id' ||
        filterKey === 'job_id' ||
        filterKey === 'session_id' ||
        filterKey === 'test_id'
      ) {
        const targetVal = (filter.stringValue?.value || '')
          .toLowerCase()
          .trim();
        return targetVal
          ? (row.id || '').toLowerCase().includes(targetVal)
          : true;
      }
      return true;
    }

    const cellValues = this.extractCellValues(row.cells?.[colIndex]);
    if (cellValues.length === 0) return false;

    if (filter.stringValue?.value !== undefined) {
      const targetVal = filter.stringValue.value.toLowerCase().trim();
      if (!targetVal) return true;

      if (filterKey === 'user' || filterKey === 'actual_user') {
        return cellValues.some((v) => v.toLowerCase().trim() === targetVal);
      }

      return cellValues.some((v) => v.toLowerCase().includes(targetVal));
    }

    if (filter.enumValues?.values && filter.enumValues.values.length > 0) {
      const targetEnumVals = filter.enumValues.values.map((v: string) =>
        v.toUpperCase().trim(),
      );
      return cellValues.some((v) =>
        targetEnumVals.includes(v.toUpperCase().trim()),
      );
    }

    if (filter.namedValue) {
      const targetPair =
        `${filter.namedValue.name}:${filter.namedValue.value}`.toLowerCase();
      const targetVal = filter.namedValue.value.toLowerCase();
      return cellValues.some(
        (v) =>
          v.toLowerCase().includes(targetPair) ||
          v.toLowerCase().includes(targetVal),
      );
    }

    return true;
  }

  private extractCellValues(cell?: Cell): string[] {
    if (!cell) return [];
    if (cell.text?.value !== undefined) return [cell.text.value];
    if (cell.link?.text !== undefined) return [cell.link.text];
    if (cell.status?.text !== undefined) return [cell.status.text];
    if (cell.chips?.values) return cell.chips.values;
    if (cell.multiLink?.entries) {
      return cell.multiLink.entries.map((e) => e.text);
    }
    return [];
  }

  override getTjsSuggestions(
    request: TjsSuggestionRequest,
  ): Observable<TjsSuggestionResponse> {
    const currentUser = this.getCurrentUser();
    const input = (request.input || '').trim().toLowerCase();

    const items: TjsSuggestion[] = [];

    if (!input) {
      items.push({
        label: 'Add filter',
        mainText: [
          {text: 'User is ', emphasized: false},
          {text: currentUser, emphasized: true},
        ],
        applyFilter: {
          pillKey: 'User',
          pillCondition: currentUser,
          keyDisplayName: 'User',
          filter: {
            key: 'user',
            stringValue: {value: currentUser},
          },
        },
      });
      if (request.entity === TjsEntity.TJS_ENTITY_TEST) {
        items.push({
          label: 'Add filter',
          mainText: [
            {text: 'Result is ', emphasized: false},
            {text: 'PASS', emphasized: true},
          ],
          applyFilter: {
            pillKey: 'Result',
            pillCondition: 'PASS',
            keyDisplayName: 'Result',
            filter: {
              key: 'result',
              enumValues: {values: ['PASS']},
            },
          },
        });
      } else if (request.entity === TjsEntity.TJS_ENTITY_JOB) {
        items.push({
          label: 'Add filter',
          mainText: [
            {text: 'Status is ', emphasized: false},
            {text: 'RUNNING', emphasized: true},
          ],
          applyFilter: {
            pillKey: 'Status',
            pillCondition: 'RUNNING',
            keyDisplayName: 'Status',
            filter: {
              key: 'status',
              enumValues: {values: ['RUNNING']},
            },
          },
        });
      }
    } else {
      if (currentUser.toLowerCase().includes(input) || 'user'.includes(input)) {
        items.push({
          label: 'Add filter',
          mainText: [
            {text: 'User is ', emphasized: false},
            {text: currentUser, emphasized: true},
          ],
          applyFilter: {
            pillKey: 'User',
            pillCondition: currentUser,
            keyDisplayName: 'User',
            filter: {
              key: 'user',
              stringValue: {value: currentUser},
            },
          },
        });
      }
      const knownEnums = ['RUNNING', 'DONE', 'PASS', 'FAIL', 'ERROR'];
      for (const val of knownEnums) {
        if (val.toLowerCase().includes(input)) {
          const isResult = val === 'PASS' || val === 'FAIL' || val === 'ERROR';
          const key = isResult ? 'result' : 'status';
          const label = isResult ? 'Result' : 'Status';
          items.push({
            label: 'Add filter',
            mainText: [
              {text: `${label} is `, emphasized: false},
              {text: val, emphasized: true},
            ],
            applyFilter: {
              pillKey: label,
              pillCondition: val,
              keyDisplayName: label,
              filter: {
                key,
                enumValues: {values: [val]},
              },
            },
          });
        }
      }
      if (items.length === 0) {
        items.push({
          label: 'Add filter',
          mainText: [
            {text: 'Search ', emphasized: false},
            {text: request.input, emphasized: true},
          ],
          applyFilter: {
            pillKey: 'Name',
            pillCondition: request.input,
            keyDisplayName: 'Name',
            filter: {
              key: 'name',
              stringValue: {value: request.input},
            },
          },
        });
      }
    }

    return of({items});
  }

  override resolveTjsChips(
    request: TjsResolveChipsRequest,
  ): Observable<TjsResolveChipsResponse> {
    const filters = request.filters || [];
    const resolvedChips = filters.map((f) => {
      const key = f.key;
      let pillKey = key.charAt(0).toUpperCase() + key.slice(1);
      if (key === 'create_time') {
        pillKey = 'Create Time';
      }

      let condition = '';
      if (f.stringValue) {
        condition = f.stringValue.value;
      } else if (f.enumValues && f.enumValues.values) {
        condition = f.enumValues.values.join(', ');
      } else if (f.namedValue) {
        condition = `${f.namedValue.name}:${f.namedValue.value}`;
      } else if (f.timeRange) {
        condition =
          `${f.timeRange.from || ''} ~ ${f.timeRange.to || ''}`.trim();
      }

      return {
        pillKey,
        pillCondition: condition,
        keyDisplayName: pillKey,
      };
    });
    return of({chips: resolvedChips});
  }
}
