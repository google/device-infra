import {TestBed} from '@angular/core/testing';
import {GetGlobalSummaryRequest, GlobalSummary} from '../../models/home';
import {MOCK_GLOBAL_SUMMARY} from '../mock_data/home';
import {FakeHomeService} from './fake_home_service';
import {HOME_SERVICE} from './home_service';

describe('FakeHomeService', () => {
  let service: FakeHomeService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {provide: HOME_SERVICE, useClass: FakeHomeService},
        FakeHomeService,
      ],
    });
    service = TestBed.inject(FakeHomeService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return mock global summary without request argument', (done) => {
    service.getGlobalSummary().subscribe((summary: GlobalSummary) => {
      expect(summary).toEqual(MOCK_GLOBAL_SUMMARY);
      done();
    });
  });

  it('should return mock global summary with request argument', (done) => {
    const request: GetGlobalSummaryRequest = {};
    service.getGlobalSummary(request).subscribe((summary: GlobalSummary) => {
      expect(summary).toEqual(MOCK_GLOBAL_SUMMARY);
      done();
    });
  });

  it('should be injectable via HOME_SERVICE token', () => {
    const tokenService = TestBed.inject(HOME_SERVICE);
    expect(tokenService).toBeInstanceOf(FakeHomeService);
  });
});
