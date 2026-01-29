import {TestBed} from '@angular/core/testing';

import {Environment} from './environment';

describe('Environment', () => {
  let service: Environment;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Environment);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  const description = 'should return false for isGoogleInternal in OSS';
  const expectedIsGoogleInternal = false;

  it(description, () => {
    expect(service.isGoogleInternal()).toBe(expectedIsGoogleInternal);
  });
});
