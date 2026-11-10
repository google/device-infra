import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, convertToParamMap} from '@angular/router';

import {
  buildQueryString,
  isHttpUrl,
  navigateWithPreservedParams,
} from './url_utils';

describe('url_utils', () => {
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

  describe('buildQueryString', () => {
    it('should build clean query string omitting null, undefined, and empty strings', () => {
      const queryString = buildQueryString({
        'debug': 'true',
        'fleet': null,
        'f': null,
        'empty': '',
        'host_name': 'my-host',
        'missing': undefined,
      });

      expect(queryString).toBe('?debug=true&host_name=my-host');
    });

    it('should return empty string when all params are null/empty', () => {
      const queryString = buildQueryString({
        'fleet': null,
        'f': null,
      });

      expect(queryString).toBe('');
    });
  });

  describe('isHttpUrl', () => {
    it('accepts values that are a single http or https URL', () => {
      expect(
        isHttpUrl('http://sponge2/00c8c57f-8e3a-4164-b6fa-8d7744108794'),
      ).toBeTrue();
      expect(
        isHttpUrl(
          'https://ci.example.com/builds/tests/view?invocationId=I0190',
        ),
      ).toBeTrue();
      expect(
        isHttpUrl('http://mobileharness-fe/testdetailview/job-1/test-1'),
      ).toBeTrue();
    });

    it('matches the scheme case-insensitively', () => {
      expect(isHttpUrl('HTTP://sponge2/abc')).toBeTrue();
      expect(isHttpUrl('Https://example.com')).toBeTrue();
    });

    it('ignores surrounding whitespace', () => {
      expect(isHttpUrl('  http://sponge2/abc\n')).toBeTrue();
    });

    it('rejects empty and missing values', () => {
      expect(isHttpUrl('')).toBeFalse();
      expect(isHttpUrl('   ')).toBeFalse();
      expect(isHttpUrl(null)).toBeFalse();
      expect(isHttpUrl(undefined)).toBeFalse();
    });

    it('rejects text that only contains or resembles a URL', () => {
      expect(isHttpUrl('see http://sponge2/abc for details')).toBeFalse();
      expect(isHttpUrl('http://sponge2/abc and more')).toBeFalse();
      expect(isHttpUrl('httpd_enabled')).toBeFalse();
      expect(isHttpUrl('use_https_only=true')).toBeFalse();
      expect(isHttpUrl('http://')).toBeFalse();
    });

    it('rejects non-http schemes, short links and file paths', () => {
      expect(isHttpUrl('ftp://host/file')).toBeFalse();
      expect(isHttpUrl('javascript:alert(1)')).toBeFalse();
      expect(isHttpUrl('go/omnilab')).toBeFalse();
      expect(isHttpUrl('/tmp/mh_lab_gen_files/test_1/file.txt')).toBeFalse();
      expect(isHttpUrl('www.example.com')).toBeFalse();
    });
  });
});
