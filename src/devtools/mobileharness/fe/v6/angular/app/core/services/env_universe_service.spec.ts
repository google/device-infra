import {TestBed} from '@angular/core/testing';
import {EnvUniverseService} from './env_universe_service';
import {Environment} from './environment';

describe('EnvUniverseService', () => {
  let mockEnvironment: jasmine.SpyObj<Environment>;

  function setupService(isInternal: boolean, searchParams = '') {
    mockEnvironment = jasmine.createSpyObj('Environment', ['isGoogleInternal']);
    mockEnvironment.isGoogleInternal.and.returnValue(isInternal);

    // Save current path to avoid clearing it
    const pathname = window.location.pathname;
    window.history.pushState({}, '', `${pathname}?${searchParams}`);

    TestBed.configureTestingModule({
      providers: [
        EnvUniverseService,
        {provide: Environment, useValue: mockEnvironment},
      ],
    });

    return TestBed.inject(EnvUniverseService);
  }

  afterEach(() => {
    const pathname = window.location.pathname;
    window.history.pushState({}, '', pathname);
  });

  describe('Internal Environment', () => {
    it('should normalize to google_1p if universe is missing', () => {
      const service = setupService(true, '');
      expect(service.getUniverseString()).toBe('google_1p');
      expect(service.isGoogle1P()).toBeTrue();
      expect(service.isGoogleOEM()).toBeFalse();
      expect(service.isGoogleInternal()).toBeTrue();
      expect(service.isAts()).toBeFalse();
    });

    it('should be google_1p if universe is explicit', () => {
      const service = setupService(true, 'universe=google_1p');
      expect(service.getUniverseString()).toBe('google_1p');
      expect(service.isGoogle1P()).toBeTrue();
      expect(service.isGoogleOEM()).toBeFalse();
    });

    it('should be OEM if universe is something else', () => {
      const service = setupService(true, 'universe=oppo');
      expect(service.getUniverseString()).toBe('oppo');
      expect(service.isGoogle1P()).toBeFalse();
      expect(service.isGoogleOEM()).toBeTrue();
      expect(service.toString()).toBe('env: google internal, universe: oppo');
    });
  });

  describe('ATS Environment', () => {
    it('should be empty universe if no universe is provided', () => {
      const service = setupService(false, '');
      expect(service.getUniverseString()).toBe('');
      expect(service.isGoogle1P()).toBeFalse();
      expect(service.isGoogleOEM()).toBeFalse();
      expect(service.isGoogleInternal()).toBeFalse();
      expect(service.isAts()).toBeTrue();
      expect(service.toString()).toBe('env: ats, universe: ');
    });

    it('should throw error if universe is provided', () => {
      expect(() => {
        setupService(false, 'universe=google_1p');
      }).toThrowError('universe is NOT allowed in ATS env');
    });
  });
});
