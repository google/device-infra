import {Injectable} from '@angular/core';
import {Observable, of} from 'rxjs';
import {
  FleetChipResolverRequest,
  FleetChipResolverResponse,
  FleetColumnCatalogRequest,
  FleetColumnCatalogResponse,
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
  SearchEntity,
  TjsEntity,
  TjsResolveChipsRequest,
  TjsResolveChipsResponse,
  TjsSearchConfig,
  TjsSearchConfigRequest,
  TjsSearchRequest,
  TjsSearchResponse,
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
  MOCK_TJS_SUGGESTION_RESPONSE_JOB,
  MOCK_TJS_SUGGESTION_RESPONSE_SESSION,
  MOCK_TJS_SUGGESTION_RESPONSE_TEST,
} from '../mock_data';
import {SearchService} from './search_service';

/** Mock implementation of SearchService for frontend testing & offline fallback. */
@Injectable()
export class FakeSearchService extends SearchService {
  constructor() {
    super();
  }

  override getFleetSearchConfig(
    request: FleetSearchConfigRequest,
  ): Observable<FleetSearchConfig> {
    return of(MOCK_FLEET_SEARCH_CONFIG);
  }

  override searchFleet(
    request: FleetSearchRequest,
  ): Observable<FleetSearchResults> {
    if (request.entity === SearchEntity.SEARCH_ENTITY_DEVICE) {
      return of(MOCK_FLEET_SEARCH_RESULTS_DEVICE);
    }
    if (request.entity === SearchEntity.SEARCH_ENTITY_HOST) {
      return of(MOCK_FLEET_SEARCH_RESULTS_HOST);
    }
    return of({
      flat: {
        total: 0,
        rangeStart: 0,
        rangeEnd: 0,
        rows: [],
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
      filterChips: (request.filters || []).map((f) => ({
        pillKey: f.key,
        pillCondition: f.simple?.values?.[0]?.value || '',
      })),
      groupByChips: (request.groupByKeys || []).map((k) => ({
        pillKey: k,
        displayName: k,
      })),
    });
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
    return of(MOCK_FLEET_COLUMN_CATALOG);
  }

  override getTjsSearchConfig(
    request: TjsSearchConfigRequest,
  ): Observable<TjsSearchConfig> {
    const entity = request.entity;
    if (entity === TjsEntity.TJS_ENTITY_TEST) {
      return of(MOCK_TJS_SEARCH_CONFIG_TEST);
    }
    if (entity === TjsEntity.TJS_ENTITY_JOB) {
      return of(MOCK_TJS_SEARCH_CONFIG_JOB);
    }
    return of(MOCK_TJS_SEARCH_CONFIG_SESSION);
  }

  override searchTjs(request: TjsSearchRequest): Observable<TjsSearchResponse> {
    const entity = request.entity;
    if (entity === TjsEntity.TJS_ENTITY_TEST) {
      return of(MOCK_TJS_SEARCH_RESPONSE_TEST);
    }
    if (entity === TjsEntity.TJS_ENTITY_JOB) {
      return of(MOCK_TJS_SEARCH_RESPONSE_JOB);
    }
    return of(MOCK_TJS_SEARCH_RESPONSE_SESSION);
  }

  override getTjsSuggestions(
    request: TjsSuggestionRequest,
  ): Observable<TjsSuggestionResponse> {
    const entity = request.entity;
    if (entity === TjsEntity.TJS_ENTITY_TEST) {
      return of(MOCK_TJS_SUGGESTION_RESPONSE_TEST);
    }
    if (entity === TjsEntity.TJS_ENTITY_JOB) {
      return of(MOCK_TJS_SUGGESTION_RESPONSE_JOB);
    }
    return of(MOCK_TJS_SUGGESTION_RESPONSE_SESSION);
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
        condition = `${f.timeRange.from || ''} ~ ${f.timeRange.to || ''}`.trim();
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
