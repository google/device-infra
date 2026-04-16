import {TestBed} from '@angular/core/testing';
import {LoadingService, resetInitialSpinnerRemovedForTest} from './loading_service';

describe('LoadingService', () => {
  let service: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(LoadingService);
    resetInitialSpinnerRemovedForTest();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should set isLoading to true on show()', () => {
    service.show();
    expect(service.isLoading()).toBeTrue();
  });

  it('should set isLoading to false on hide()', () => {
    service.show();
    service.hide();
    expect(service.isLoading()).toBeFalse();
  });

  it('should remove initial spinner on hide()', () => {
    // Create a dummy element in DOM
    const spinner = document.createElement('div');
    spinner.id = 'initial-loading-overlay';
    document.body.appendChild(spinner);

    expect(document.getElementById('initial-loading-overlay')).toBeTruthy();

    service.hide();

    expect(document.getElementById('initial-loading-overlay')).toBeNull();
  });

  it('should only remove initial spinner once', () => {
    const spinner = document.createElement('div');
    spinner.id = 'initial-loading-overlay';
    document.body.appendChild(spinner);

    service.hide();
    expect(document.getElementById('initial-loading-overlay')).toBeNull();

    // Create it again
    const spinner2 = document.createElement('div');
    spinner2.id = 'initial-loading-overlay';
    document.body.appendChild(spinner2);

    // Call hide again, it should NOT remove it this time because flag is set
    service.hide();
    expect(document.getElementById('initial-loading-overlay')).toBeTruthy();

    // Clean up
    spinner2.remove();
  });
});
