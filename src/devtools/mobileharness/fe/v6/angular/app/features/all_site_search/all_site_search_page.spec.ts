import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {
  ActivatedRoute,
  convertToParamMap,
  ParamMap,
  provideRouter,
  Router,
} from '@angular/router';
import {BehaviorSubject, Subject} from 'rxjs';

import {
  AllSiteIdType,
  ResolveAllSiteQueryRequest,
  ResolveAllSiteQueryResponse,
} from '../../core/models/search';
import {CommonParamsService} from '../../core/services/common_params_service';
import {
  SEARCH_SERVICE,
  SearchService,
} from '../../core/services/search/search_service';
import {AllSiteSearchPage} from './all_site_search_page';

describe('AllSiteSearchPage', () => {
  let component: AllSiteSearchPage;
  let fixture: ComponentFixture<AllSiteSearchPage>;
  let router: Router;
  let queryParamMap$: BehaviorSubject<ParamMap>;
  let resolveSubject$: Subject<ResolveAllSiteQueryResponse>;
  let mockSearchService: jasmine.SpyObj<SearchService>;
  let commonParamsService: CommonParamsService;

  beforeEach(async () => {
    queryParamMap$ = new BehaviorSubject<ParamMap>(
      convertToParamMap({'q': 'device-id-for-1-hit-1'}),
    );
    resolveSubject$ = new Subject<ResolveAllSiteQueryResponse>();
    mockSearchService = jasmine.createSpyObj<SearchService>('SearchService', [
      'resolveAllSiteQuery',
    ]);
    mockSearchService.resolveAllSiteQuery.and.callFake(() =>
      resolveSubject$.asObservable(),
    );

    await TestBed.configureTestingModule({
      imports: [AllSiteSearchPage],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParamMap$.asObservable(),
            snapshot: {
              queryParams: {'q': 'device-id-for-1-hit-1'},
              queryParamMap: queryParamMap$.value,
            },
          },
        },
        {provide: SEARCH_SERVICE, useValue: mockSearchService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AllSiteSearchPage);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    commonParamsService = TestBed.inject(CommonParamsService);
    spyOn(commonParamsService, 'getCommonParams').and.returnValue({
      'fake_data': 'true',
    });
    spyOn(commonParamsService, 'isEmbeddedMode').and.returnValue(false);
  });

  it('renders loading state while resolveAllSiteQuery is in flight', () => {
    fixture.detectChanges();

    expect(component.viewState()).toBe('loading');
    expect(mockSearchService.resolveAllSiteQuery).toHaveBeenCalledWith({
      query: 'device-id-for-1-hit-1',
      anyId: {},
    });

    const loadingTitle = fixture.debugElement.query(
      By.css('.all-site-loading-title'),
    );
    expect(loadingTitle).toBeTruthy();
    expect(loadingTitle.nativeElement.textContent).toContain(
      'Resolving "device-id-for-1-hit-1"...',
    );
  });

  it('redirects with replaceUrl: true to Entity Detail page on 1-Hit and strips q/scope', () => {
    const navSpy = spyOn(router, 'navigate');
    fixture.detectChanges();

    resolveSubject$.next({
      matches: [
        {
          device: {
            deviceId: 'device-id-for-1-hit-1',
            hostName: 'host-for-1-hit-1.example.com',
            universe: 'google_1p',
          },
        },
      ],
    });

    expect(navSpy).toHaveBeenCalledWith(['/devices/device-id-for-1-hit-1'], {
      queryParams: {
        'fake_data': 'true',
        'host_name': 'host-for-1-hit-1.example.com',
      },
      replaceUrl: true,
    });
  });

  it('renders 404 Not Found card and 5 fallback chips when 0 matches are returned', () => {
    queryParamMap$.next(convertToParamMap({'q': 'unknown_id_999'}));
    fixture.detectChanges();

    resolveSubject$.next({matches: []});
    fixture.detectChanges();

    expect(component.viewState()).toBe('not_found');
    const titleEl = fixture.debugElement.query(
      By.css('.all-site-not-found-title'),
    );
    expect(titleEl.nativeElement.textContent).toContain(
      'No exact match for "unknown_id_999"',
    );

    const chips = fixture.debugElement.queryAll(
      By.css('.all-site-fallback-chips .m3-entity-assist-chip'),
    );
    expect(chips.length).toBe(5);
    expect(chips[0].nativeElement.getAttribute('href')).toContain(
      '/devices?fake_data=true&q=unknown_id_999',
    );
  });

  it('renders Ambiguous disambiguation card and candidates when >1 matches are returned', () => {
    queryParamMap$.next(convertToParamMap({'q': 'host-name-for-ambiguous-1'}));
    fixture.detectChanges();

    resolveSubject$.next({
      matches: [
        {
          device: {
            deviceId: 'host-name-for-ambiguous-1',
            hostName: 'host-name-for-ambiguous-1.example.com',
            model: 'LinuxTestbedDevice',
          },
        },
        {
          host: {
            hostName: 'host-name-for-ambiguous-1.example.com',
            attachedDeviceCount: 2,
            hostIp: '172.16.42.18',
          },
        },
      ],
    });
    fixture.detectChanges();

    expect(component.viewState()).toBe('ambiguous');
    const cards = fixture.debugElement.queryAll(
      By.css('.all-site-candidate-card'),
    );
    expect(cards.length).toBe(2);
    expect(cards[0].nativeElement.textContent).toContain('Device');
    expect(cards[0].nativeElement.textContent).toContain(
      'Model: LinuxTestbedDevice',
    );
    expect(cards[1].nativeElement.textContent).toContain('Host');
    expect(cards[1].nativeElement.textContent).toContain(
      '2 attached devices · IP: 172.16.42.18',
    );
  });

  it('sends scoped idType when scope query parameter is set and supports Try across Any ID', () => {
    const navSpy = spyOn(router, 'navigate');
    queryParamMap$.next(
      convertToParamMap({'q': 'host-name-for-ambiguous-1', 'scope': 'test'}),
    );
    fixture.detectChanges();

    const expectedReq: ResolveAllSiteQueryRequest = {
      query: 'host-name-for-ambiguous-1',
      idType: AllSiteIdType.ID_TYPE_TEST_ID,
    };
    expect(mockSearchService.resolveAllSiteQuery).toHaveBeenCalledWith(
      expectedReq,
    );

    resolveSubject$.next({matches: []});
    fixture.detectChanges();

    const tryAnyBtn = fixture.debugElement.query(
      By.css('[data-action="try-any-id"]'),
    );
    expect(tryAnyBtn).toBeTruthy();
    tryAnyBtn.nativeElement.click();

    expect(navSpy).toHaveBeenCalledWith(['/search'], {
      queryParams: {
        'fake_data': 'true',
        'q': 'host-name-for-ambiguous-1',
      },
    });
  });
});
