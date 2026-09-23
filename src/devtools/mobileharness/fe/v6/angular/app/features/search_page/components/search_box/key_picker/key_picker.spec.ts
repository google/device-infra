import {signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatDialogRef} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {provideNoopAnimations} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {
  FleetFilterKeyCatalogResponse,
  FleetGroupByKeyCatalogResponse,
  SearchEntity,
} from '../../../../../core/models/search';
import {
  SEARCH_SERVICE,
  SearchService,
} from '../../../../../core/services/search/search_service';
import {SearchPageStore} from '../../../services/search_page_store';
import {KeyPickerComponent, KeyPickerDialogData} from './key_picker';

class FakeSearchPageStore {
  readonly entity = signal<string>('devices');
  readonly fleet = signal<string>('internal');
  readonly groupByKeys = signal<string[]>([]);
  readonly isLandingState = signal<boolean>(false);

  effectiveFilters = jasmine.createSpy('effectiveFilters').and.returnValue([]);
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
            displayName: 'Device Status',
            metadata: {keyDisplayName: 'Device Status'},
            coverage: {notShown: {}},
            applied: false,
          },
          {
            key: 'model',
            displayName: 'Model',
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
            displayName: 'Label',
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
            applied: false,
          },
          {
            key: 'host_name',
            displayName: 'Host Name',
            groupCount: {shown: {count: 42}},
            applied: true,
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
      const titleEl = document.querySelector('#kp-title');
      expect(titleEl?.textContent).toContain('Add a filter');
    });

    it('renders sections and entries with metadata and applied state', () => {
      const rows = document.querySelectorAll('.kp-row');
      expect(rows.length).toBe(3); // 2 built-in + 1 dimension

      // Row 1: status (unapplied, not shown coverage)
      expect(rows[0].textContent).toContain('Device Status');
      expect(rows[0].classList.contains('kp-applied')).toBeFalse();

      // Row 2: model (applied)
      expect(rows[1].textContent).toContain('Model');
      expect(rows[1].textContent).toContain('applied');
      expect(rows[1].classList.contains('kp-applied')).toBeTrue();

      // Row 3: dim::label (used by 450 devices)
      expect(rows[2].textContent).toContain('Label');
      expect(rows[2].textContent).toContain('Used by 450 devices');
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
        '.cs-searchbar input',
      ) as HTMLInputElement;
      input.value = 'label';
      input.dispatchEvent(new Event('input'));
      openerFixture.detectChanges();

      expect(component.query()).toBe('label');
    });

    it('closes on close button click', () => {
      const closeBtn = document.querySelector(
        '.cs-close',
      ) as HTMLButtonElement;
      closeBtn.click();
      expect(mockDialogRef.close).toHaveBeenCalled();
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
      const titleEl = document.querySelector('#kp-title');
      expect(titleEl?.textContent).toContain('Add a group-by');
    });

    it('renders group counts and inert applied rows', () => {
      const rows = document.querySelectorAll('.kp-row');
      expect(rows.length).toBe(2);

      // Row 1: driver (14 groups, unapplied)
      expect(rows[0].textContent).toContain('Driver');
      expect(rows[0].textContent).toContain('14 groups');
      expect(rows[0].classList.contains('kp-inert')).toBeFalse();

      // Row 2: host_name (applied, inert)
      expect(rows[1].textContent).toContain('Host Name');
      expect(rows[1].textContent).toContain('applied');
      expect(rows[1].classList.contains('kp-inert')).toBeTrue();
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
      expect(mockStore.openQuickGroupBy).toHaveBeenCalledWith('driver', 'Driver');
    });
  });
});
