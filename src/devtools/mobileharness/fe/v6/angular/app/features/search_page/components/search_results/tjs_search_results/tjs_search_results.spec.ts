import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideNoopAnimations} from '@angular/platform-browser/animations';
import {signal} from '@angular/core';
import {By} from '@angular/platform-browser';

import {Column, Row} from '../../../../../core/models/search';
import {TjsSearchStore} from '../../../services/tjs_search_store';
import {TjsSearchResultsComponent} from './tjs_search_results';

class MockTjsSearchStore {
  readonly hasActiveFilters = signal(false);
  readonly entity = signal('jobs');
  readonly entityLabel = signal('Jobs');
  readonly isLoading = signal(false);
  readonly rows = signal<Row[]>([]);
  readonly displayColumns = signal<Column[]>([]);
  readonly density = signal<'compact' | 'normal' | 'comfortable'>('normal');
  readonly rangeText = signal('1–10 of 20');
  readonly hasPrevPage = signal(false);
  readonly hasNextPage = signal(true);
  readonly prevPage = jasmine.createSpy('prevPage');
  readonly nextPage = jasmine.createSpy('nextPage');
  readonly resetSearchState = jasmine.createSpy('resetSearchState');
}

describe('TjsSearchResultsComponent', () => {
  let component: TjsSearchResultsComponent;
  let fixture: ComponentFixture<TjsSearchResultsComponent>;
  let mockStore: MockTjsSearchStore;

  beforeEach(async () => {
    mockStore = new MockTjsSearchStore();

    await TestBed.configureTestingModule({
      imports: [TjsSearchResultsComponent],
      providers: [
        provideNoopAnimations(),
        {provide: TjsSearchStore, useValue: mockStore},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TjsSearchResultsComponent);
    component = fixture.componentInstance;
  });

  it('renders "Add a filter to search [entity]" when no active filters', () => {
    mockStore.hasActiveFilters.set(false);
    fixture.detectChanges();

    const emptyEl = fixture.debugElement.query(By.css('.rt-empty-state'));
    expect(emptyEl).toBeTruthy();
    expect(emptyEl.nativeElement.textContent).toContain('Add a filter to search jobs.');
  });

  it('renders loading state when active filters and loading initial page', () => {
    mockStore.hasActiveFilters.set(true);
    mockStore.isLoading.set(true);
    mockStore.rows.set([]);
    fixture.detectChanges();

    expect(component.viewMode()).toBe('loading_initial');
    const loadingEl = fixture.debugElement.query(By.css('.rt-empty-state'));
    expect(loadingEl).toBeTruthy();
    expect(loadingEl.nativeElement.textContent).toContain('Loading Jobs…');
  });

  it('renders empty match state and triggers resetSearchState when "Clear all filters" is clicked', () => {
    mockStore.hasActiveFilters.set(true);
    mockStore.isLoading.set(false);
    mockStore.rows.set([]);
    fixture.detectChanges();

    expect(component.viewMode()).toBe('empty');
    const emptyEl = fixture.debugElement.query(By.css('.rt-empty-state'));
    expect(emptyEl).toBeTruthy();
    expect(emptyEl.nativeElement.textContent).toContain('No Jobs match your filters');

    const clearBtn = fixture.debugElement.query(By.css('.rt-btn-outlined'));
    expect(clearBtn).toBeTruthy();
    clearBtn.nativeElement.click();
    expect(mockStore.resetSearchState).toHaveBeenCalled();
  });

  it('renders data table and pagination when rows are present', () => {
    mockStore.hasActiveFilters.set(true);
    mockStore.isLoading.set(false);
    mockStore.displayColumns.set([
      {key: 'id', displayName: 'Job ID'},
      {key: 'name', displayName: 'Job Name'},
    ]);
    mockStore.rows.set([
      {id: 'job-1', cells: [{text: {value: 'job-1'}}, {text: {value: 'My Job'}}]},
    ]);
    fixture.detectChanges();

    expect(component.viewMode()).toBe('table');
    expect(component.columnsToDisplay()).toEqual(['id', 'name']);

    const tableEl = fixture.debugElement.query(By.css('table.results-table'));
    expect(tableEl).toBeTruthy();

    const paginationEl = fixture.debugElement.query(By.css('app-search-pagination'));
    expect(paginationEl).toBeTruthy();
  });
});
