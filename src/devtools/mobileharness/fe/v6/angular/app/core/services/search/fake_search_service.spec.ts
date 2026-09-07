import {TestBed} from '@angular/core/testing';
import {APP_DATA, AppData} from '../../models/app_data';
import {
  Filter,
  FleetChipResolverResponse,
  FleetColumnCatalogResponse,
  FleetPromotedKeysResponse,
  FleetSearchConfig,
  FleetSearchResults,
  FleetSuggestionResponse,
  FleetValueListResponse,
  SearchEntity,
  TjsEntity,
  TjsFilter,
  TjsResolveChipsResponse,
  TjsSearchConfig,
  TjsSearchResponse,
  TjsSuggestionResponse,
} from '../../models/search';
import {FakeSearchService} from './fake_search_service';
import {SEARCH_SERVICE} from './search_service';

describe('FakeSearchService', () => {
  let service: FakeSearchService;
  const mockAppData: AppData = {
    email: 'qiupingf@google.com',
  } as AppData;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {provide: APP_DATA, useValue: mockAppData},
        {provide: SEARCH_SERVICE, useClass: FakeSearchService},
      ],
    });
    service = TestBed.inject(SEARCH_SERVICE) as FakeSearchService;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getFleetSearchConfig', () => {
    it('should return host config with host defaults and host total count', (done) => {
      service
        .getFleetSearchConfig({entity: SearchEntity.SEARCH_ENTITY_HOST})
        .subscribe((config: FleetSearchConfig) => {
          expect(config.columns?.defaults).toBeDefined();
          const keys = config.columns!.defaults!.map((c) => c.key);
          expect(keys).toContain('id');
          expect(keys).toContain('hostName');
          expect(keys).toContain('status');
          expect(keys).toContain('type');
          expect(keys).toContain('owner');
          expect(keys).toContain('model');
          expect(config.landing?.browseAllCount).toBeGreaterThanOrEqual(0);
          done();
        });
    });

    it('should return device config with device defaults and device total count', (done) => {
      service
        .getFleetSearchConfig({entity: SearchEntity.SEARCH_ENTITY_DEVICE})
        .subscribe((config: FleetSearchConfig) => {
          expect(config.columns?.defaults).toBeDefined();
          expect(config.landing?.browseAllCount).toBeGreaterThanOrEqual(0);
          done();
        });
    });
  });

  describe('searchFleet', () => {
    it('should return device rows when entity is SEARCH_ENTITY_DEVICE', (done) => {
      service
        .searchFleet({entity: SearchEntity.SEARCH_ENTITY_DEVICE})
        .subscribe((res: FleetSearchResults) => {
          expect(res.flat).toBeDefined();
          expect(res.flat!.total).toBeGreaterThan(0);
          expect(res.flat!.rows?.length).toBeGreaterThan(0);
          expect(res.flat!.rangeStart).toBe(1);
          done();
        });
    });

    it('should return host rows when entity is SEARCH_ENTITY_HOST', (done) => {
      service
        .searchFleet({entity: SearchEntity.SEARCH_ENTITY_HOST})
        .subscribe((res: FleetSearchResults) => {
          expect(res.flat).toBeDefined();
          expect(res.flat!.total).toBeGreaterThan(0);
          expect(res.flat!.rows?.length).toBeGreaterThan(0);
          done();
        });
    });

    it('should return empty rows for unknown entity', (done) => {
      service
        .searchFleet({entity: SearchEntity.SEARCH_ENTITY_UNSPECIFIED})
        .subscribe((res: FleetSearchResults) => {
          expect(res.flat?.total).toBe(0);
          expect(res.flat?.rows?.length).toBe(0);
          expect(res.flat?.rangeStart).toBe(0);
          done();
        });
    });

    it('should filter rows by field::status (non-negated)', (done) => {
      const filters: Filter[] = [
        {
          key: 'field::status',
          simple: {
            values: [{value: 'IDLE'}],
            negated: false,
          },
        },
      ];
      service
        .searchFleet({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          filters,
        })
        .subscribe((res: FleetSearchResults) => {
          expect(res.flat).toBeDefined();
          for (const row of res.flat!.rows || []) {
            const status = (row.cells?.[2]?.status?.text || '').toUpperCase();
            expect(status).toBe('IDLE');
          }
          done();
        });
    });

    it('should filter rows by field::status (negated)', (done) => {
      const filters: Filter[] = [
        {
          key: 'field::status',
          simple: {
            values: [{value: 'IDLE'}],
            negated: true,
          },
        },
      ];
      service
        .searchFleet({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          filters,
        })
        .subscribe((res: FleetSearchResults) => {
          expect(res.flat).toBeDefined();
          for (const row of res.flat!.rows || []) {
            const status = (row.cells?.[2]?.status?.text || '').toUpperCase();
            expect(status).not.toBe('IDLE');
          }
          done();
        });
    });

    it('should filter rows by host::ats_controller', (done) => {
      const filters: Filter[] = [
        {
          key: 'host::ats_controller',
          simple: {
            values: [{value: 'lab'}],
          },
        },
      ];
      service
        .searchFleet({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          filters,
        })
        .subscribe((res: FleetSearchResults) => {
          expect(res.flat).toBeDefined();
          for (const row of res.flat!.rows || []) {
            const host = (row.cells?.[1]?.text?.value || '').toLowerCase();
            expect(host).toContain('lab');
          }
          done();
        });
    });

    it('should pass through unrecognized filter keys', (done) => {
      const filters: Filter[] = [
        {
          key: 'unknown::key',
          simple: {
            values: [{value: 'anything'}],
          },
        },
      ];
      service
        .searchFleet({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          filters,
        })
        .subscribe((res: FleetSearchResults) => {
          expect(res.flat?.total).toBeGreaterThan(0);
          done();
        });
    });

    it('should handle pagination with pageSize and pageToken', (done) => {
      service
        .searchFleet({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          flat: {
            page: {
              pageSize: 2,
              pageToken: '1',
            },
          },
        })
        .subscribe((res: FleetSearchResults) => {
          expect(res.flat?.rows?.length).toBeLessThanOrEqual(2);
          expect(res.flat?.rangeStart).toBe(2);
          expect(res.flat?.prevPageToken).toBe('0');
          done();
        });
    });

    it('should not return nextPageToken when offset + pageSize exceeds total', (done) => {
      service
        .searchFleet({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          flat: {
            page: {
              pageSize: 1000,
            },
          },
        })
        .subscribe((res: FleetSearchResults) => {
          expect(res.flat?.nextPageToken).toBeUndefined();
          expect(res.flat?.prevPageToken).toBeUndefined();
          done();
        });
    });
  });

  describe('getFleetSuggestions', () => {
    it('should return default key suggestions when input is empty', (done) => {
      service
        .getFleetSuggestions({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          input: '',
        })
        .subscribe((res: FleetSuggestionResponse) => {
          expect(res.items).toBeDefined();
          expect(res.items!.length).toBe(8);
          expect(res.items![0].label).toBe('Add filter');
          expect(res.items![0].mainText?.[0]?.text).toBe('Status');
          done();
        });
    });

    it('should label active filters as Modify when input is empty', (done) => {
      service
        .getFleetSuggestions({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          input: '',
          filters: [{key: 'field::status'}],
        })
        .subscribe((res: FleetSuggestionResponse) => {
          expect(res.items).toBeDefined();
          const statusItem = res.items!.find(
            (item) => item.openPicker?.key === 'field::status',
          );
          expect(statusItem?.label).toBe('Modify Status');
          done();
        });
    });

    it('should match known values by value and handle pluralization', (done) => {
      service
        .getFleetSuggestions({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          input: 'android',
        })
        .subscribe((res: FleetSuggestionResponse) => {
          expect(res.items).toBeDefined();
          expect(res.items!.length).toBeGreaterThan(0);
          const item = res.items![0];
          expect(item.mainText?.[0]?.text).toBe('Type are ');
          expect(item.mainText?.[1]?.text).toBe('ANDROID');
          done();
        });
    });

    it('should match known values by name and singularization', (done) => {
      service
        .getFleetSuggestions({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          input: 'model',
        })
        .subscribe((res: FleetSuggestionResponse) => {
          expect(res.items).toBeDefined();
          expect(res.items!.length).toBeGreaterThan(0);
          const item = res.items![0];
          expect(item.mainText?.[0]?.text).toBe('Model is ');
          done();
        });
    });

    it('should label active filter matching known value as Modify', (done) => {
      service
        .getFleetSuggestions({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          input: 'pixel 8',
          filters: [{key: 'dim::model'}],
        })
        .subscribe((res: FleetSuggestionResponse) => {
          expect(res.items).toBeDefined();
          expect(res.items!.length).toBeGreaterThan(0);
          expect(res.items![0].label).toBe('Modify Model');
          done();
        });
    });

    it('should fallback to search_query filter when input matches no known values', (done) => {
      service
        .getFleetSuggestions({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          input: 'random_custom_query_12345',
        })
        .subscribe((res: FleetSuggestionResponse) => {
          expect(res.items).toBeDefined();
          expect(res.items!.length).toBe(1);
          const fallback = res.items![0];
          expect(fallback.label).toBe('Add filter');
          expect(fallback.mainText?.[0]?.text).toBe('Search ');
          expect(fallback.mainText?.[1]?.text).toBe(
            'random_custom_query_12345',
          );
          expect(fallback.applyFilter?.resultingFilter?.key).toBe(
            'search_query',
          );
          done();
        });
    });
  });

  describe('resolveFleetChips', () => {
    it('should map filters to filterChips and groupByKeys to groupByChips', (done) => {
      service
        .resolveFleetChips({
          filters: [
            {
              key: 'field::status',
              simple: {values: [{value: 'IDLE'}]},
            },
          ],
          groupByKeys: ['dim::model'],
        })
        .subscribe((res: FleetChipResolverResponse) => {
          expect(res.filterChips).toBeDefined();
          expect(res.filterChips!.length).toBe(1);
          expect(res.filterChips![0].valid?.pillKey).toBe('field::status');
          expect(res.filterChips![0].valid?.pillCondition).toBe('IDLE');

          expect(res.groupByChips).toBeDefined();
          expect(res.groupByChips!.length).toBe(1);
          expect(res.groupByChips![0].valid?.pillKey).toBe('dim::model');
          expect(res.groupByChips![0].valid?.displayName).toBe('dim::model');
          done();
        });
    });

    it('should handle empty filters and groupByKeys', (done) => {
      service
        .resolveFleetChips({})
        .subscribe((res: FleetChipResolverResponse) => {
          expect(res.filterChips).toEqual([]);
          expect(res.groupByChips).toEqual([]);
          done();
        });
    });

    it('should return invalid result for unknown keys', (done) => {
      service
        .resolveFleetChips({
          filters: [{key: 'invalid_key'}],
          groupByKeys: ['unknown_key'],
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        })
        .subscribe((res: FleetChipResolverResponse) => {
          expect(res.filterChips?.[0].invalid?.reason).toContain(
            'Unknown device filter key: invalid_key',
          );
          expect(res.groupByChips?.[0].invalid?.reason).toContain(
            'Unknown device group-by key: unknown_key',
          );
          done();
        });
    });
  });

  describe('getFleetValueList', () => {
    it('should return plain value list for uuid, device_id, and host_name', (done) => {
      service
        .getFleetValueList({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          key: 'device_id',
        })
        .subscribe((res: FleetValueListResponse) => {
          expect(res.plain).toBeDefined();
          done();
        });
    });

    it('should return counted value list for status', (done) => {
      service
        .getFleetValueList({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          key: 'field::status',
        })
        .subscribe((res: FleetValueListResponse) => {
          expect(res.counted).toBeDefined();
          done();
        });
    });

    it('should return counted value list for model', (done) => {
      service
        .getFleetValueList({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          key: 'dim::model',
        })
        .subscribe((res: FleetValueListResponse) => {
          expect(res.counted).toBeDefined();
          done();
        });
    });

    it('should return counted value list for type', (done) => {
      service
        .getFleetValueList({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          key: 'field::type',
        })
        .subscribe((res: FleetValueListResponse) => {
          expect(res.counted).toBeDefined();
          done();
        });
    });

    it('should return counted value list for owner', (done) => {
      service
        .getFleetValueList({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          key: 'field::owner',
        })
        .subscribe((res: FleetValueListResponse) => {
          expect(res.counted).toBeDefined();
          done();
        });
    });

    it('should return default counted value list for unknown key', (done) => {
      service
        .getFleetValueList({
          entity: SearchEntity.SEARCH_ENTITY_DEVICE,
          key: 'other_unknown_key',
        })
        .subscribe((res: FleetValueListResponse) => {
          expect(res.counted).toBeDefined();
          done();
        });
    });
  });

  describe('getFleetPromotedKeys', () => {
    it('should return promoted keys', (done) => {
      service
        .getFleetPromotedKeys({entity: SearchEntity.SEARCH_ENTITY_DEVICE})
        .subscribe((res: FleetPromotedKeysResponse) => {
          expect(res.filterKeys).toBeDefined();
          expect(res.groupByKeys).toBeDefined();
          done();
        });
    });
  });

  describe('getFleetColumnCatalog', () => {
    it('should return column catalog sections', (done) => {
      service
        .getFleetColumnCatalog({entity: SearchEntity.SEARCH_ENTITY_DEVICE})
        .subscribe((res: FleetColumnCatalogResponse) => {
          expect(res.sections).toBeDefined();
          expect(res.sections!.length).toBeGreaterThan(0);
          done();
        });
    });
  });

  describe('getTjsSearchConfig', () => {
    it('should return defaultChips with User filter set to current user for tests', (done) => {
      service
        .getTjsSearchConfig({entity: TjsEntity.TJS_ENTITY_TEST})
        .subscribe((config: TjsSearchConfig) => {
          expect(config.defaultChips).toBeDefined();
          expect(config.defaultChips!.length).toBe(1);
          const chip = config.defaultChips![0];
          expect(chip.pillKey).toBe('User');
          expect(chip.pillCondition).toBe('qiupingf');
          expect(chip.filter?.key).toBe('user');
          expect(chip.filter?.stringValue?.value).toBe('qiupingf');
          done();
        });
    });

    it('should return defaultChips with User filter set to current user for jobs', (done) => {
      service
        .getTjsSearchConfig({entity: TjsEntity.TJS_ENTITY_JOB})
        .subscribe((config: TjsSearchConfig) => {
          expect(config.defaultChips).toBeDefined();
          expect(config.defaultChips!.length).toBe(1);
          const chip = config.defaultChips![0];
          expect(chip.pillKey).toBe('User');
          expect(chip.pillCondition).toBe('qiupingf');
          expect(chip.filter?.key).toBe('user');
          expect(chip.filter?.stringValue?.value).toBe('qiupingf');
          done();
        });
    });

    it('should return defaultChips with User filter set to current user for sessions', (done) => {
      service
        .getTjsSearchConfig({entity: TjsEntity.TJS_ENTITY_SESSION})
        .subscribe((config: TjsSearchConfig) => {
          expect(config.defaultChips).toBeDefined();
          expect(config.defaultChips!.length).toBe(1);
          const chip = config.defaultChips![0];
          expect(chip.pillKey).toBe('User');
          expect(chip.pillCondition).toBe('qiupingf');
          expect(chip.filter?.key).toBe('user');
          expect(chip.filter?.stringValue?.value).toBe('qiupingf');
          done();
        });
    });
  });

  describe('searchTjs', () => {
    it('should filter tests by current user', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_TEST,
          filters: [{key: 'user', stringValue: {value: 'qiupingf'}}],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBe(3);
          for (const row of res.rows!) {
            expect(row.cells?.[2]?.text?.value).toBe('qiupingf');
          }
          done();
        });
    });

    it('should filter jobs by current user', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_JOB,
          filters: [{key: 'user', stringValue: {value: 'qiupingf'}}],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBe(2);
          for (const row of res.rows!) {
            expect(row.cells?.[2]?.text?.value).toBe('qiupingf');
          }
          done();
        });
    });

    it('should filter sessions by current user', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_SESSION,
          filters: [{key: 'user', stringValue: {value: 'qiupingf'}}],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBe(2);
          for (const row of res.rows!) {
            expect(row.cells?.[1]?.text?.value).toBe('qiupingf');
          }
          done();
        });
    });

    it('should filter by other user (dev_user)', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_JOB,
          filters: [{key: 'user', stringValue: {value: 'dev_user'}}],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBe(1);
          expect(res.rows![0].cells?.[2]?.text?.value).toBe('dev_user');
          done();
        });
    });

    it('should return all rows when no filter is provided', (done) => {
      service
        .searchTjs({entity: TjsEntity.TJS_ENTITY_JOB})
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBe(3);
          done();
        });
    });

    it('should filter by multiple criteria (user and status)', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_JOB,
          filters: [
            {key: 'user', stringValue: {value: 'qiupingf'}},
            {key: 'status', enumValues: {values: ['RUNNING']}},
          ],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBe(1);
          expect(res.rows![0].cells?.[2]?.text?.value).toBe('qiupingf');
          expect(res.rows![0].cells?.[3]?.status?.text).toBe('RUNNING');
          done();
        });
    });

    it('should filter by id matching cell link text', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_JOB,
          filters: [{key: 'id', stringValue: {value: 'j_987'}}],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBeGreaterThan(0);
          done();
        });
    });

    it('should fallback to row.id when column does not exist', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_JOB,
          filters: [{key: 'test_id', stringValue: {value: 'job_1'}}],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBeGreaterThan(0);
          done();
        });
    });

    it('should filter by devices / device_id / device', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_JOB,
          filters: [{key: 'device_id', stringValue: {value: 'pixel'}}],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          done();
        });
    });

    it('should filter by name matching name column or user column', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_JOB,
          filters: [{key: 'name', stringValue: {value: 'qiupingf'}}],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBeGreaterThan(0);
          done();
        });
    });

    it('should return empty when name filter does not match', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_JOB,
          filters: [
            {key: 'name', stringValue: {value: 'nonexistent_test_xyz'}},
          ],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBe(0);
          done();
        });
    });

    it('should handle filter with empty filter key or empty string value as true', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_JOB,
          filters: [
            {key: ''},
            {key: 'name', stringValue: {value: ''}},
            {key: 'id', stringValue: {value: ''}},
            {key: 'user', stringValue: {value: ''}},
          ],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBe(3);
          done();
        });
    });

    it('should filter by namedValue in filter', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_TEST,
          filters: [
            {
              key: 'user',
              namedValue: {name: 'user', value: 'qiupingf'},
            },
          ],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBe(3);
          done();
        });
    });

    it('should pass through filter with non-matching column key', (done) => {
      service
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_JOB,
          filters: [
            {key: 'unknown_dimension_key', stringValue: {value: 'abc'}},
          ],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          done();
        });
    });
  });

  describe('getTjsSuggestions', () => {
    it('should return User and Result suggestions for TEST entity when input is empty', (done) => {
      service
        .getTjsSuggestions({
          entity: TjsEntity.TJS_ENTITY_TEST,
          input: '',
        })
        .subscribe((res: TjsSuggestionResponse) => {
          expect(res.items).toBeDefined();
          expect(res.items!.length).toBe(2);
          expect(res.items![0].applyFilter?.filter?.key).toBe('user');
          expect(res.items![1].applyFilter?.filter?.key).toBe('result');
          expect(res.items![1].applyFilter?.filter?.enumValues?.values).toEqual(
            ['PASS'],
          );
          done();
        });
    });

    it('should return User and Status suggestions for JOB entity when input is empty', (done) => {
      service
        .getTjsSuggestions({
          entity: TjsEntity.TJS_ENTITY_JOB,
          input: '',
        })
        .subscribe((res: TjsSuggestionResponse) => {
          expect(res.items).toBeDefined();
          expect(res.items!.length).toBe(2);
          expect(res.items![0].applyFilter?.filter?.key).toBe('user');
          expect(res.items![1].applyFilter?.filter?.key).toBe('status');
          expect(res.items![1].applyFilter?.filter?.enumValues?.values).toEqual(
            ['RUNNING'],
          );
          done();
        });
    });

    it('should return only User suggestion for SESSION entity when input is empty', (done) => {
      service
        .getTjsSuggestions({
          entity: TjsEntity.TJS_ENTITY_SESSION,
          input: '',
        })
        .subscribe((res: TjsSuggestionResponse) => {
          expect(res.items).toBeDefined();
          expect(res.items!.length).toBe(1);
          expect(res.items![0].applyFilter?.filter?.key).toBe('user');
          done();
        });
    });

    it('should match user filter when input includes user', (done) => {
      service
        .getTjsSuggestions({
          entity: TjsEntity.TJS_ENTITY_JOB,
          input: 'user',
        })
        .subscribe((res: TjsSuggestionResponse) => {
          expect(res.items).toBeDefined();
          const userItem = res.items!.find(
            (i) => i.applyFilter?.filter?.key === 'user',
          );
          expect(userItem).toBeDefined();
          done();
        });
    });

    it('should match result filter for PASS, FAIL, ERROR', (done) => {
      service
        .getTjsSuggestions({
          entity: TjsEntity.TJS_ENTITY_TEST,
          input: 'fail',
        })
        .subscribe((res: TjsSuggestionResponse) => {
          expect(res.items).toBeDefined();
          const resultItem = res.items!.find(
            (i) => i.applyFilter?.filter?.key === 'result',
          );
          expect(resultItem).toBeDefined();
          expect(resultItem?.applyFilter?.filter?.enumValues?.values).toEqual([
            'FAIL',
          ]);
          done();
        });
    });

    it('should match status filter for RUNNING, DONE', (done) => {
      service
        .getTjsSuggestions({
          entity: TjsEntity.TJS_ENTITY_JOB,
          input: 'running',
        })
        .subscribe((res: TjsSuggestionResponse) => {
          expect(res.items).toBeDefined();
          const statusItem = res.items!.find(
            (i) => i.applyFilter?.filter?.key === 'status',
          );
          expect(statusItem).toBeDefined();
          expect(statusItem?.applyFilter?.filter?.enumValues?.values).toEqual([
            'RUNNING',
          ]);
          done();
        });
    });

    it('should fallback to name filter when input matches no known values', (done) => {
      service
        .getTjsSuggestions({
          entity: TjsEntity.TJS_ENTITY_TEST,
          input: 'my_unique_test_suite_123',
        })
        .subscribe((res: TjsSuggestionResponse) => {
          expect(res.items).toBeDefined();
          expect(res.items!.length).toBe(1);
          expect(res.items![0].applyFilter?.filter?.key).toBe('name');
          expect(res.items![0].applyFilter?.filter?.stringValue?.value).toBe(
            'my_unique_test_suite_123',
          );
          done();
        });
    });
  });

  describe('resolveTjsChips', () => {
    it('should resolve stringValue, enumValues, namedValue, and timeRange chips', (done) => {
      const filters: TjsFilter[] = [
        {key: 'user', stringValue: {value: 'qiupingf'}},
        {key: 'status', enumValues: {values: ['RUNNING', 'DONE']}},
        {
          key: 'custom',
          namedValue: {name: 'tag', value: 'smoke'},
        },
        {
          key: 'create_time',
          timeRange: {from: '2026-01-01', to: '2026-01-02'},
        },
      ];

      service
        .resolveTjsChips({filters})
        .subscribe((res: TjsResolveChipsResponse) => {
          expect(res.chips).toBeDefined();
          expect(res.chips!.length).toBe(4);

          expect(res.chips![0].pillKey).toBe('User');
          expect(res.chips![0].pillCondition).toBe('qiupingf');

          expect(res.chips![1].pillKey).toBe('Status');
          expect(res.chips![1].pillCondition).toBe('RUNNING, DONE');

          expect(res.chips![2].pillKey).toBe('Custom');
          expect(res.chips![2].pillCondition).toBe('tag:smoke');

          expect(res.chips![3].pillKey).toBe('Create Time');
          expect(res.chips![3].pillCondition).toBe('2026-01-01 ~ 2026-01-02');
          done();
        });
    });

    it('should handle empty filters', (done) => {
      service.resolveTjsChips({}).subscribe((res: TjsResolveChipsResponse) => {
        expect(res.chips).toEqual([]);
        done();
      });
    });
  });

  describe('custom user via APP_DATA', () => {
    let customService: FakeSearchService;

    beforeEach(() => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          {provide: APP_DATA, useValue: {email: 'custom_user@google.com'}},
          {provide: SEARCH_SERVICE, useClass: FakeSearchService},
        ],
      });
      customService = TestBed.inject(SEARCH_SERVICE) as FakeSearchService;
    });

    it('should use custom user in getTjsSearchConfig', (done) => {
      customService
        .getTjsSearchConfig({entity: TjsEntity.TJS_ENTITY_TEST})
        .subscribe((config: TjsSearchConfig) => {
          expect(config.defaultChips![0].pillCondition).toBe('custom_user');
          expect(config.defaultChips![0].filter?.stringValue?.value).toBe(
            'custom_user',
          );
          done();
        });
    });

    it('should map mock rows and filter by custom user', (done) => {
      customService
        .searchTjs({
          entity: TjsEntity.TJS_ENTITY_TEST,
          filters: [{key: 'user', stringValue: {value: 'custom_user'}}],
        })
        .subscribe((res: TjsSearchResponse) => {
          expect(res.rows).toBeDefined();
          expect(res.rows!.length).toBe(3);
          for (const row of res.rows!) {
            expect(row.cells?.[2]?.text?.value).toBe('custom_user');
          }
          done();
        });
    });
  });

  describe('default user with tianch dev placeholder in APP_DATA', () => {
    let devPlaceholderService: FakeSearchService;

    beforeEach(() => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [
          {provide: APP_DATA, useValue: {email: 'tianch@google.com'}},
          {provide: SEARCH_SERVICE, useClass: FakeSearchService},
        ],
      });
      devPlaceholderService = TestBed.inject(
        SEARCH_SERVICE,
      ) as FakeSearchService;
    });

    it('should default to qiupingf even when dev placeholder is present', (done) => {
      devPlaceholderService
        .getTjsSearchConfig({entity: TjsEntity.TJS_ENTITY_TEST})
        .subscribe((config: TjsSearchConfig) => {
          expect(config.defaultChips![0].pillCondition).toBe('qiupingf');
          expect(config.defaultChips![0].filter?.stringValue?.value).toBe(
            'qiupingf',
          );
          done();
        });
    });
  });

  describe('default user without APP_DATA injection', () => {
    let defaultService: FakeSearchService;

    beforeEach(() => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [{provide: SEARCH_SERVICE, useClass: FakeSearchService}],
      });
      defaultService = TestBed.inject(SEARCH_SERVICE) as FakeSearchService;
    });

    it('should default to qiupingf from getAppData', (done) => {
      defaultService
        .getTjsSearchConfig({entity: TjsEntity.TJS_ENTITY_TEST})
        .subscribe((config: TjsSearchConfig) => {
          expect(config.defaultChips![0].pillCondition).toBe('qiupingf');
          expect(config.defaultChips![0].filter?.stringValue?.value).toBe(
            'qiupingf',
          );
          done();
        });
    });
  });
});
