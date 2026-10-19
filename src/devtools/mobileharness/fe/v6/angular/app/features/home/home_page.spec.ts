import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of, Subject, throwError} from 'rxjs';

import {
  HOME_SERVICE,
  HomeService,
} from '@deviceinfra/app/core/services/home';
import {MOCK_GLOBAL_SUMMARY} from '@deviceinfra/app/core/services/mock_data/home';
import {LoadingService} from '@deviceinfra/app/shared/services/loading_service';
import {HomePage} from './home_page';

describe('HomePage Component', () => {
  let component: HomePage;
  let fixture: ComponentFixture<HomePage>;
  let mockHomeService: jasmine.SpyObj<HomeService>;
  let mockLoadingService: jasmine.SpyObj<LoadingService>;

  beforeEach(async () => {
    mockHomeService = jasmine.createSpyObj('HomeService', ['getGlobalSummary']);
    mockLoadingService = jasmine.createSpyObj('LoadingService', [
      'show',
      'hide',
    ]);
    mockHomeService.getGlobalSummary.and.returnValue(of(MOCK_GLOBAL_SUMMARY));

    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, HomePage],
      providers: [
        provideRouter([
          {path: 'devices', component: HomePage},
          {path: 'hosts', component: HomePage},
        ]),
        {provide: HOME_SERVICE, useValue: mockHomeService},
        {provide: LoadingService, useValue: mockLoadingService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HomePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should call getGlobalSummary on init and populate summary', () => {
    expect(mockHomeService.getGlobalSummary).toHaveBeenCalled();
    expect(component.summary()).toEqual(MOCK_GLOBAL_SUMMARY);
    expect(component.isLoading()).toBeFalse();
    expect(fixture.nativeElement.querySelector('mat-progress-bar')).toBeNull();
  });

  it('should display Internal Labs metrics and taxonomy', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const text = compiled.textContent || '';
    expect(text).toContain('Internal Labs');
    expect(text).toContain('(Operated by Google)');
    expect(text).toContain('Lab types guide');
    expect(text).toContain('5,124');
    expect(text).toContain('151,216');
    expect(text).toContain('In Service (BUSY)');
    expect(text).toContain('In Service (IDLE)');
    expect(text).toContain('Others');

    const guideLink = compiled.querySelector(
      'a.guide-link',
    ) as HTMLAnchorElement;
    expect(guideLink).toBeTruthy();
    expect(guideLink.getAttribute('href')).toBe('http://go/omnilab-which');
    expect(guideLink.getAttribute('target')).toBe('_blank');
    expect(guideLink.getAttribute('rel')).toBe('noopener noreferrer');
    const bookIcon = compiled.querySelector('.book-icon') as HTMLElement;
    expect(bookIcon).toBeTruthy();
    expect(bookIcon.classList).toContain('material-symbols-outlined');
    expect(bookIcon.textContent?.trim()).toBe('menu_book');
    expect(compiled.querySelector('.external-icon')).toBeTruthy();
  });

  it('should display Partner Labs metrics and taxonomy', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const text = compiled.textContent || '';
    expect(text).toContain('Partner Labs');
    expect(text).toContain('(Operated by OEM Partners)');
    expect(text).toContain('12');
    expect(text).toContain('24');
    expect(text).toContain('300');
  });

  it('should render Internal Labs breakdown table expanded by default when breakdown is provided', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const tables = compiled.querySelectorAll('.breakdown-table');
    // Section 1 table is expanded by default; Section 2 is collapsed by default
    expect(tables.length).toBe(1);

    const text = compiled.textContent || '';
    expect(text).toContain('Hide Breakdown by Lab Type');
    expect(text).toContain('Core Lab');
    expect(text).toContain('Shared pool, operated by OmniLab team');
    expect(text).toContain('Satellite Lab');
    expect(text).toContain(
      'Dedicated pools, operated by OmniLab customers',
    );
    expect(text).toContain('SLaaS (Satellite-Lab-as-a-Service) Lab');
  });

  it('should toggle Partner Labs breakdown table on accordion click', () => {
    const toggleButtons = fixture.nativeElement.querySelectorAll(
      '.m3-button-toggle',
    );
    // Button 0 is Lab Type Breakdown (already expanded), Button 1 is Partner Lab Breakdown (collapsed)
    expect(toggleButtons.length).toBe(2);

    const partnerToggleBtn = toggleButtons[1] as HTMLElement;
    expect(partnerToggleBtn.textContent).toContain(
      'View Breakdown by Partner Lab',
    );
    partnerToggleBtn.click();
    fixture.detectChanges();

    const tables = fixture.nativeElement.querySelectorAll('.breakdown-table');
    expect(tables.length).toBe(2);

    const text = fixture.nativeElement.textContent || '';
    expect(text).toContain('Xiaomi');
    expect(text).toContain('Samsung');
    expect(text).toContain('Oppo');
  });

  it('should not render Lab Type breakdown table when labTypes is missing in summary', async () => {
    const summaryWithoutBreakdown = {
      ...MOCK_GLOBAL_SUMMARY,
      firstParty: {
        ...MOCK_GLOBAL_SUMMARY.firstParty,
        labTypes: undefined,
      },
    };
    mockHomeService.getGlobalSummary.and.returnValue(
      of(summaryWithoutBreakdown),
    );
    component.refresh();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('Breakdown by Lab Type');
    expect(compiled.textContent).not.toContain('Core Lab');
  });

  it('should refresh data when refresh is clicked', async () => {
    component.refresh();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(mockHomeService.getGlobalSummary).toHaveBeenCalledTimes(2);
  });

  it('should display error banner when summary request fails', async () => {
    mockHomeService.getGlobalSummary.and.returnValue(
      throwError(() => new Error('Server error')),
    );
    component.refresh();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Server error');
    const errorBanner = fixture.nativeElement.querySelector('.error-banner');
    expect(errorBanner).toBeTruthy();
  });

  it('should display last updated timestamp after summary resolves', () => {
    expect(component.lastUpdated()).toBeTruthy();
    const updatedEl = fixture.nativeElement.querySelector('.last-updated-text');
    expect(updatedEl).toBeTruthy();
    expect(updatedEl.textContent).toContain('Updated');
  });

  it('should render native links with href for stats drilldown', () => {
    const links = fixture.nativeElement.querySelectorAll('a.stat-group');
    expect(links.length).toBe(4);
    const firstPartyHostLink = links[0] as HTMLAnchorElement;
    expect(firstPartyHostLink.getAttribute('href')).toContain('/hosts');
    const firstPartyDeviceLink = links[1] as HTMLAnchorElement;
    expect(firstPartyDeviceLink.getAttribute('href')).toContain('/devices');
  });

  it('should render loading state when summary data is pending', () => {
    mockHomeService.getGlobalSummary.and.returnValue(new Subject());
    const pendingFixture = TestBed.createComponent(HomePage);
    const pendingComp = pendingFixture.componentInstance;
    pendingFixture.detectChanges();

    expect(pendingComp.isLoading()).toBeTrue();
    expect(pendingComp.summary()).toBeNull();
    const loadingEl =
      pendingFixture.nativeElement.querySelector('.loading-state');
    expect(loadingEl).toBeTruthy();
    expect(loadingEl.textContent).toContain('Loading OmniLab summary…');
    expect(
      pendingFixture.nativeElement.querySelector('mat-progress-bar'),
    ).toBeTruthy();
  });
});
