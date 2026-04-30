import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap} from '@angular/router';

import {navigateWithPreservedParams} from './url_utils';

describe('navigateWithPreservedParams', () => {
  let mockActivatedRoute: Partial<ActivatedRoute>;

  beforeEach(() => {
    mockActivatedRoute = {
      snapshot: {
        queryParamMap: convertToParamMap({'is_embedded_mode': 'true'})
      } as unknown as ActivatedRoute['snapshot'],
    };

    TestBed.configureTestingModule({
      providers: [
        {
          provide: ActivatedRoute,
          useValue: mockActivatedRoute,
        },
      ],
    });
  });

  it('should perform client-side navigation with merged query params', () => {
    const mockRouter = jasmine.createSpyObj('Router', ['navigateByUrl']);
    const route = TestBed.inject(ActivatedRoute);

    navigateWithPreservedParams('/devices/123', mockRouter, route);

    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/devices/123?is_embedded_mode=true');
  });

  it('should not override query params present in new URL', () => {
    const mockRouter = jasmine.createSpyObj('Router', ['navigateByUrl']);
    const route = TestBed.inject(ActivatedRoute);

    navigateWithPreservedParams('/devices/123?is_embedded_mode=false', mockRouter, route);

    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/devices/123?is_embedded_mode=false');
  });
});
