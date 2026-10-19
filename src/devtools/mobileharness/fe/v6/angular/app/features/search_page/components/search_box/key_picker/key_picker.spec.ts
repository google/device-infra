import {signal} from '@angular/core';
import {
  ComponentFixture,
  fakeAsync,
  TestBed,
  tick,
} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {provideNoopAnimations} from '@angular/platform-browser/animations';
import {of, throwError} from 'rxjs';

import {
  Fleet,
  FleetFilterKeyCatalogResponse,
  FleetGroupByKeyCatalogResponse,
  SearchEntity,
} from '../../../../../core/models/search';
import {
  SEARCH_SERVICE,
  SearchService,
} from '../../../../../core/services/search/search_service';
import {KeyPickerDialogData} from '../../../models';
import {SearchPageStore} from '../../../services/search_page_store';
import {KeyPickerComponent} from './key_picker';

class FakeSearchPageStore {
  readonly entity = signal<string>('devices');
  readonly fleet = signal<string>('internal');
  readonly groupByKeys = signal<string[]>([]);

  openQuickFilter = jasmine.createSpy('openQuickFilter');
  openQuickGroupBy = jasmine.createSpy('openQuickGroupBy');
}

describe('KeyPickerComponent', () => {
  let openerFixture: ComponentFixture<MatTestDialogOpener<KeyPickerComponent>>;
  let component: KeyPickerComponent;
  let mockSearchService: jasmine.SpyObj<SearchService>;
  let mockDialogRef: MatDialogRef<KeyPickerComponent>;
  let mockStore: FakeSearchPageStore;

  const mockFilterCatalog: FleetFilterKeyCatalogResponse = {
    sections: [
      {
        heading: 'Built-in fields',
        totalAvailable: 0,
        entries: [
          {
            key: 'status',
            metadata: {keyDisplayName: 'Device Status'},
            coverage: {notShown: {}},
            applied: false,
          },
          {
            key: 'model',
            metadata: {keyDisplayName: 'Model'},
            coverage: {shown: {count: 850}},
            applied: true,
          },
        ],
      },
      {
        heading: 'Dimensions',
        totalAvailable: 1583,
        entries: [
          {
            key: 'dim::label',
            metadata: {keyDisplayName: 'Label'},
            coverage: {shown: {count: 450}},
            applied: false,
          },
        ],
      },
    ],
  };

  const mockGroupByCatalog: FleetGroupByKeyCatalogResponse = {
    sections: [
      {
        heading: 'Built-in fields',
        totalAvailable: 0,
        entries: [
          {
            key: 'driver',
            displayName: 'Driver',
            groupCount: {shown: {count: 14}},
            coverage: {notShown: {}},
            applied: false,
          },
          {
            key: 'host_name',
            displayName: 'Host Name',
            groupCount: {shown: {count: 42}},
            coverage: {notShown: {}},
            applied: true,
          },
        ],
      },
      {
        heading: 'Dimensions',
        totalAvailable: 1583,
        entries: [
          {
            key: 'dim::os_version',
            displayName: 'OS Version',
            groupCount: {shown: {count: 12}},
            coverage: {shown: {count: 1200}},
            applied: false,
          },
        ],
      },
    ],
  };

  async function setup(dialogData: KeyPickerDialogData) {
    mockSearchService = jasmine.createSpyObj<SearchService>('SearchService', [
      'getFleetFilterKeyCatalog',
      'getFleetGroupByKeyCatalog',
    ]);
    mockSearchService.getFleetFilterKeyCatalog.and.returnValue(
      of(mockFilterCatalog),
    );
    mockSearchService.getFleetGroupByKeyCatalog.and.returnValue(
      of(mockGroupByCatalog),
    );

    mockStore = new FakeSearchPageStore();

    await TestBed.configureTestingModule({
      imports: [KeyPickerComponent, MatTestDialogOpenerModule],
      providers: [
        provideNoopAnimations(),
        {provide: SEARCH_SERVICE, useValue: mockSearchService},
        {provide: SearchPageStore, useValue: mockStore},
      ],
    }).compileComponents();

    openerFixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(KeyPickerComponent, {
        data: dialogData,
      }),
    );
    component = openerFixture.componentInstance.dialogRef.componentInstance;
    mockDialogRef = openerFixture.componentInstance.dialogRef;
    spyOn(mockDialogRef, 'close');
    openerFixture.detectChanges();
    await openerFixture.whenStable();
    openerFixture.detectChanges();
  }

  afterEach(() => {
    mockDialogRef?.close();
    openerFixture?.destroy();
  });

  describe('filter mode', () => {
    beforeEach(async () => {
      await setup({
        mode: 'filter',
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
      });
    });

    it('displays "Add a filter" title and loads filter catalog', () => {
      expect(component.dialogTitle()).toBe('Add a filter');
      expect(mockSearchService.getFleetFilterKeyCatalog).toHaveBeenCalled();
      const titleEl = document.querySelector('.config-title');
      expect(titleEl?.textContent).toContain('Add a filter');
    });

    it('renders sections and entries with metadata and applied state', () => {
      const rows = document.querySelectorAll('.kp-row');
      expect(rows.length).toBe(3); // 2 built-in + 1 dimension

      // Row 1: status (unapplied, not shown coverage)
      expect(rows[0].textContent).toContain('Device Status');
      expect(rows[0].classList.contains('kp-applied')).toBeFalse();

      // Row 2: model (applied, coverage omitted when applied, text applied without chip)
      expect(rows[1].textContent).toContain('Model');
      expect(rows[1].querySelector('.kp-applied-meta')?.textContent).toBe(
        'applied',
      );
      expect(rows[1].querySelector('.kp-badge-applied')).toBeNull();
      expect(rows[1].textContent).not.toContain('Used in 850 devices');
      expect(rows[1].classList.contains('kp-applied')).toBeTrue();

      // Row 3: dim::label (used in 450 devices)
      expect(rows[2].textContent).toContain('Label');
      expect(rows[2].textContent).toContain('Used in 450 devices');
    });

    it('picks a key, closes dialog, and opens quick filter', () => {
      const rows = document.querySelectorAll<HTMLElement>('.kp-row');
      rows[0].click();
      openerFixture.detectChanges();

      expect(mockDialogRef.close).toHaveBeenCalled();
      expect(mockStore.openQuickFilter).toHaveBeenCalledWith(
        'status',
        'Device Status',
        {keyDisplayName: 'Device Status'},
      );
    });

    it('searches keys when input value changes', () => {
      const input = document.querySelector(
        '.kp-searchbar input',
      ) as HTMLInputElement;
      input.value = 'label';
      input.dispatchEvent(new Event('input'));
      openerFixture.detectChanges();

      expect(component.query()).toBe('label');
    });

    it('debounces search query before triggering RPC and suppresses intermediate typing', fakeAsync(() => {
      const input = document.querySelector(
        '.kp-searchbar input',
      ) as HTMLInputElement;

      // Simulate continuous typing: 'd' -> 'de' -> 'dev' with 150ms intervals (< 300ms)
      input.value = 'd';
      input.dispatchEvent(new Event('input'));
      openerFixture.detectChanges();
      tick(150);

      input.value = 'de';
      input.dispatchEvent(new Event('input'));
      openerFixture.detectChanges();
      tick(150);

      input.value = 'dev';
      input.dispatchEvent(new Event('input'));
      openerFixture.detectChanges();

      expect(component.isDebouncing()).toBeTrue();
      expect(
        mockSearchService.getFleetFilterKeyCatalog,
      ).not.toHaveBeenCalledWith(jasmine.objectContaining({query: 'd'}));
      expect(
        mockSearchService.getFleetFilterKeyCatalog,
      ).not.toHaveBeenCalledWith(jasmine.objectContaining({query: 'de'}));

      // Wait until 300ms debounce expires
      tick(300);
      openerFixture.detectChanges();

      expect(component.debouncedQuery()).toBe('dev');
      expect(component.isDebouncing()).toBeFalse();
      expect(mockSearchService.getFleetFilterKeyCatalog).toHaveBeenCalledWith(
        jasmine.objectContaining({query: 'dev'}),
      );
    }));

    it('clears query immediately without debounce delay on clearSearch', fakeAsync(() => {
      component.onSearch('model');
      tick(300);
      expect(component.debouncedQuery()).toBe('model');

      component.clearSearch();
      tick(0);
      openerFixture.detectChanges();

      expect(component.query()).toBe('');
      expect(component.debouncedQuery()).toBe('');
      expect(component.isDebouncing()).toBeFalse();
    }));

    it('renders clear button when query is present and clicking it clears search', () => {
      const input = document.querySelector(
        '.kp-searchbar input',
      ) as HTMLInputElement;
      input.value = 'status';
      input.dispatchEvent(new Event('input'));
      openerFixture.detectChanges();

      const clearBtn = document.querySelector(
        '.kp-search-clear',
      ) as HTMLButtonElement;
      expect(clearBtn).not.toBeNull();

      clearBtn.click();
      openerFixture.detectChanges();

      expect(component.query()).toBe('');
      expect(document.querySelector('.kp-search-clear')).toBeNull();
    });

    it('picks an entry via Space keypress', () => {
      const rows = document.querySelectorAll<HTMLElement>('.kp-row');
      rows[0].dispatchEvent(
        new KeyboardEvent('keydown', {key: ' ', bubbles: true}),
      );
      openerFixture.detectChanges();

      expect(mockDialogRef.close).toHaveBeenCalled();
      expect(mockStore.openQuickFilter).toHaveBeenCalledWith(
        'status',
        'Device Status',
        {keyDisplayName: 'Device Status'},
      );
    });

    it('displays no key matches message when catalog returns empty sections', fakeAsync(() => {
      mockSearchService.getFleetFilterKeyCatalog.and.returnValue(
        of({sections: []}),
      );
      component.onSearch('xyz');
      tick(300);
      openerFixture.detectChanges();

      const emptyEl = document.querySelector('.kp-empty');
      expect(emptyEl?.textContent).toContain('No key matches “xyz”');
    }));

    it('displays no keys available message when query is empty and catalog has no sections', async () => {
      mockSearchService.getFleetFilterKeyCatalog.and.returnValue(
        of({sections: []}),
      );
      component.retry();
      TestBed.tick();
      await openerFixture.whenStable();
      TestBed.tick();
      openerFixture.detectChanges();

      const emptyEl = document.querySelector('.kp-empty');
      expect(emptyEl?.textContent).toContain('No keys available');
    });

    it('falls back to default error message when error has empty message', async () => {
      mockSearchService.getFleetFilterKeyCatalog.and.returnValue(
        throwError(() => new Error('')),
      );
      component.retry();
      TestBed.tick();
      await openerFixture.whenStable();
      TestBed.tick();

      expect(component.errorMessage()).toBe('Failed to load keys.');
    });

    it('closes on close button click', () => {
      const closeBtn = document.querySelector(
        '.close-button',
      ) as HTMLButtonElement;
      closeBtn.click();
      expect(mockDialogRef.close).toHaveBeenCalled();
    });

    it('renders error state and retries fetching catalog on retry click', async () => {
      mockSearchService.getFleetFilterKeyCatalog.and.returnValue(
        throwError(() => new Error('RPC failure')),
      );
      component.retry();
      TestBed.tick();
      await openerFixture.whenStable();
      TestBed.tick();

      expect(component.errorMessage()).toBe('RPC failure');
      const errorEl = document.querySelector('.error-banner');
      expect(errorEl).not.toBeNull();
      expect(errorEl?.textContent).toContain('RPC failure');

      const retryBtn = errorEl?.querySelector(
        '.retry-btn',
      ) as HTMLButtonElement;
      expect(retryBtn).not.toBeNull();

      mockSearchService.getFleetFilterKeyCatalog.and.returnValue(
        of(mockFilterCatalog),
      );
      retryBtn.click();
      TestBed.tick();
      await openerFixture.whenStable();
      TestBed.tick();

      expect(component.errorMessage()).toBeNull();
      expect(document.querySelector('.error-banner')).toBeNull();
      const rows = document.querySelectorAll('.kp-row');
      expect(rows.length).toBe(3);
    });
  });

  describe('groupby mode', () => {
    beforeEach(async () => {
      await setup({
        mode: 'groupby',
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
      });
    });

    it('displays "Add a group-by" title and loads group-by catalog', () => {
      expect(component.dialogTitle()).toBe('Add a group-by');
      expect(mockSearchService.getFleetGroupByKeyCatalog).toHaveBeenCalled();
      const titleEl = document.querySelector('.config-title');
      expect(titleEl?.textContent).toContain('Add a group-by');
    });

    it('renders group counts, coverage counts, and inert applied rows', () => {
      const rows = document.querySelectorAll('.kp-row');
      expect(rows.length).toBe(3);

      // Row 1: driver (14 groups, unapplied, coverage notShown)
      expect(rows[0].querySelector('.kp-row-name')?.textContent).toContain(
        'Driver',
      );
      expect(rows[0].querySelector('.kp-group-count')?.textContent).toBe(
        '14 groups',
      );
      expect(
        rows[0].querySelector('.kp-row-meta-group .kp-group-count'),
      ).not.toBeNull();
      expect(rows[0].querySelector('.kp-group-pill')).toBeNull();
      expect(rows[0].querySelector('.kp-meta-dot')).toBeNull();
      expect(rows[0].querySelector('.kp-row-meta')).toBeNull();
      expect(rows[0].classList.contains('kp-inert')).toBeFalse();

      // Row 2: host_name (applied, inert, groups and coverage omitted, text applied without chip)
      expect(rows[1].querySelector('.kp-row-name')?.textContent).toContain(
        'Host Name',
      );
      expect(rows[1].querySelector('.kp-group-count')).toBeNull();
      expect(rows[1].querySelector('.kp-meta-dot')).toBeNull();
      expect(rows[1].querySelector('.kp-applied-meta')?.textContent).toBe(
        'applied',
      );
      expect(rows[1].querySelector('.kp-badge-applied')).toBeNull();
      expect(rows[1].querySelector('.kp-row-meta')?.textContent).toBe(
        'applied',
      );
      expect(rows[1].classList.contains('kp-inert')).toBeTrue();

      // Row 3: dim::os_version (12 groups, coverage shown: 1,200 devices, separated by dot)
      expect(rows[2].querySelector('.kp-row-name')?.textContent).toContain(
        'OS Version',
      );
      expect(rows[2].querySelector('.kp-group-count')?.textContent).toBe(
        '12 groups',
      );
      expect(rows[2].querySelector('.kp-row-meta')?.textContent).toBe(
        'Used in 1,200 devices',
      );
      expect(rows[2].querySelector('.kp-meta-dot')?.textContent).toBe('·');
      expect(rows[2].classList.contains('kp-inert')).toBeFalse();
    });

    it('does not trigger pick when clicking an inert applied row', () => {
      const rows = document.querySelectorAll<HTMLElement>('.kp-row');
      rows[1].click(); // host_name (applied)
      openerFixture.detectChanges();

      expect(mockDialogRef.close).not.toHaveBeenCalled();
      expect(mockStore.openQuickGroupBy).not.toHaveBeenCalled();
    });

    it('picks an unapplied group-by, closes dialog, and opens quick group-by', () => {
      const rows = document.querySelectorAll<HTMLElement>('.kp-row');
      rows[0].click(); // driver
      openerFixture.detectChanges();

      expect(mockDialogRef.close).toHaveBeenCalled();
      expect(mockStore.openQuickGroupBy).toHaveBeenCalledWith(
        'driver',
        'Driver',
      );
    });

    it('picks an unapplied group-by via Enter keypress', () => {
      const rows = document.querySelectorAll<HTMLElement>('.kp-row');
      rows[0].dispatchEvent(
        new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}),
      );
      openerFixture.detectChanges();

      expect(mockDialogRef.close).toHaveBeenCalled();
      expect(mockStore.openQuickGroupBy).toHaveBeenCalledWith(
        'driver',
        'Driver',
      );
    });
  });

  describe('initialization and options', () => {
    it('falls back to store entity and fleet when not provided in dialog data', async () => {
      await setup({mode: 'filter'});
      expect(component.entity()).toBe(SearchEntity.SEARCH_ENTITY_DEVICE);
      expect(component.fleet()).toBe(Fleet.FLEET_SELF);
    });

    it('forwards activeFilters and activeGroupBy when provided', async () => {
      const activeFilters = [
        {key: 'status', simple: {values: [{value: 'idle'}]}},
      ];
      await setup({
        mode: 'groupby',
        entity: SearchEntity.SEARCH_ENTITY_DEVICE,
        activeFilters,
        activeGroupBy: ['host_name'],
      });
      expect(mockSearchService.getFleetGroupByKeyCatalog).toHaveBeenCalledWith(
        jasmine.objectContaining({
          filters: activeFilters,
          groupBy: ['host_name'],
        }),
      );
    });
  });
});
