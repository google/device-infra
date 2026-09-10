import {CommonModule} from '@angular/common';
import {Component, signal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MatButtonModule} from '@angular/material/button';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {By} from '@angular/platform-browser';
import {provideNoopAnimations} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {EntityType, SearchPageConfig} from './models';
import {SearchPage} from './search_page';
import {SearchPageStore} from './services/search_page_store';
import {LoadingService} from '../../shared/services/loading_service';

@Component({
  selector: 'app-search-box',
  standalone: true,
  template: '',
})
class StubSearchBox {
  readonly focusInput = jasmine.createSpy('focusInput');
}

@Component({
  selector: 'app-fleet-search-results',
  standalone: true,
  template: '',
})
class StubFleetSearchResults {}

@Component({
  selector: 'app-tjs-search-results',
  standalone: true,
  template: '',
})
class StubTjsSearchResults {}

class MockSearchPageStore {
  readonly isLandingState = signal(true);
  readonly isConfigLoading = signal(false);
  readonly searchConfig = signal<SearchPageConfig | undefined>(undefined);
  readonly isTjs = signal(false);
  readonly entity = signal<EntityType>('devices');
  readonly searchQuery = signal('');
  readonly showSuggestions = signal(false);
  readonly browseAll = signal(false);
  readonly executeSearch = jasmine.createSpy('executeSearch');
}

class MockLoadingService {
  readonly hide = jasmine.createSpy('hide');
}

describe('SearchPage', () => {
  let component: SearchPage;
  let fixture: ComponentFixture<SearchPage>;
  let mockStore: MockSearchPageStore;
  let mockLoadingService: MockLoadingService;

  beforeEach(async () => {
    mockStore = new MockSearchPageStore();
    mockLoadingService = new MockLoadingService();

    await TestBed.configureTestingModule({
      imports: [SearchPage],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        {provide: SearchPageStore, useValue: mockStore},
        {provide: LoadingService, useValue: mockLoadingService},
      ],
    })
      .overrideComponent(SearchPage, {
        set: {
          imports: [
            CommonModule,
            MatButtonModule,
            MatProgressSpinnerModule,
            StubSearchBox,
            StubFleetSearchResults,
            StubTjsSearchResults,
          ],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(SearchPage);
    component = fixture.componentInstance;
  });

  it('hides loading indicator on init', () => {
    fixture.detectChanges();
    expect(mockLoadingService.hide).toHaveBeenCalled();
  });

  it('computes landing_loading when in landing state with config loading and no config', () => {
    mockStore.isLandingState.set(true);
    mockStore.isConfigLoading.set(true);
    mockStore.searchConfig.set(undefined);
    fixture.detectChanges();

    expect(component.pageViewMode()).toBe('landing_loading');
    const spinner = fixture.debugElement.query(
      By.css('.rt-landing-loading mat-spinner'),
    );
    expect(spinner).toBeTruthy();
  });

  it('computes landing_launcher when in landing state with config loaded', () => {
    mockStore.isLandingState.set(true);
    mockStore.isConfigLoading.set(false);
    mockStore.searchConfig.set({
      landing: {
        tryCategories: [
          {
            label: 'Device Model',
            examples: [{text: 'model: Pixel 9'}],
          },
        ],
        browseAllCount: 42,
      },
    });
    fixture.detectChanges();

    expect(component.pageViewMode()).toBe('landing_launcher');
    const label = fixture.debugElement.query(By.css('.rt-try-cat-label'));
    expect(label).toBeTruthy();
    expect(label.nativeElement.textContent).toContain('Device Model');

    const browseBtn = fixture.debugElement.query(By.css('.browse-all-btn'));
    expect(browseBtn).toBeTruthy();
    expect(browseBtn.nativeElement.textContent).toContain(
      'Browse all 42 devices',
    );
  });

  it('computes fleet_results when not in landing state and entity is fleet (!isTjs)', () => {
    mockStore.isLandingState.set(false);
    mockStore.isTjs.set(false);
    fixture.detectChanges();

    expect(component.pageViewMode()).toBe('fleet_results');
    const fleetEl = fixture.debugElement.query(
      By.css('app-fleet-search-results'),
    );
    expect(fleetEl).toBeTruthy();
  });

  it('computes tjs_results when not in landing state and entity is TJS (isTjs)', () => {
    mockStore.isLandingState.set(false);
    mockStore.isTjs.set(true);
    fixture.detectChanges();

    expect(component.pageViewMode()).toBe('tjs_results');
    const tjsEl = fixture.debugElement.query(By.css('app-tjs-search-results'));
    expect(tjsEl).toBeTruthy();
  });

  it('triggers executeSearch when onBrowseAll is called', () => {
    component.onBrowseAll();
    expect(mockStore.browseAll()).toBeTrue();
    expect(mockStore.executeSearch).toHaveBeenCalled();
  });
});
