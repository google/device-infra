import {DOCUMENT} from '@angular/common';
import {TestBed} from '@angular/core/testing';

import {UrlService} from './url_service';
interface MockWindow {
  location: {search: string};
  addEventListener: jasmine.Spy;
  removeEventListener: jasmine.Spy;
  parent: {postMessage: jasmine.Spy};
  self: unknown;
  top: unknown;
}

interface MockDocument {
  defaultView: MockWindow;
}

describe('UrlService', () => {
  let service: UrlService;
  let mockDocument: MockDocument;
  let mockWin: MockWindow;

  beforeEach(() => {
    mockWin = {
      location: {
        search: '',
      },
      addEventListener: jasmine.createSpy('addEventListener'),
      removeEventListener: jasmine.createSpy('removeEventListener'),
      parent: {
        postMessage: jasmine.createSpy('postMessage'),
      },
      self: {},
      top: {},
    };
    mockDocument = {
      defaultView: mockWin,
    };
  });

  describe('isInEmbeddedMode', () => {
    it('should return true if is_embedded_mode is true and inside iframe', () => {
      mockWin.location.search = '?is_embedded_mode=true';
      mockWin.self = {id: 'iframe'};
      mockWin.top = {id: 'parent'};
      TestBed.configureTestingModule({
        providers: [UrlService, {provide: DOCUMENT, useValue: mockDocument}],
      });
      service = TestBed.inject(UrlService);
      expect(service.isInEmbeddedMode()).toBeTrue();
    });

    it('should return false if is_embedded_mode is true but not in iframe', () => {
      mockWin.location.search = '?is_embedded_mode=true';
      mockWin.self = mockWin.top;
      TestBed.configureTestingModule({
        providers: [UrlService, {provide: DOCUMENT, useValue: mockDocument}],
      });
      service = TestBed.inject(UrlService);
      expect(service.isInEmbeddedMode()).toBeFalse();
    });
  });

  describe('getExternalUrl', () => {
    it('should throw error if not in embedded mode', (done) => {
      mockWin.location.search = '';
      TestBed.configureTestingModule({
        providers: [UrlService, {provide: DOCUMENT, useValue: mockDocument}],
      });
      service = TestBed.inject(UrlService);
      service
        .getExternalUrl('host_details', {'host_name': 'test-host'})
        .subscribe({
          next: () => {
            done.fail('Should have failed');
          },
          error: (error) => {
            expect(error.message).toBe(
              'Not in embedded mode, the host window is NOT available!',
            );
            done();
          },
        });
    });

    it('should return external URL string if in embedded mode', (done) => {
      mockWin.location.search = '?is_embedded_mode=true&origin=test-origin';
      mockWin.self = {id: 'iframe'};
      mockWin.top = {id: 'parent'};
      TestBed.configureTestingModule({
        providers: [UrlService, {provide: DOCUMENT, useValue: mockDocument}],
      });
      service = TestBed.inject(UrlService);

      // Capture the message listener added by the service.
      const messageListener = mockWin.addEventListener.calls
        .all()
        .find((call) => call.args[0] === 'message')?.args[1];
      expect(messageListener).toBeDefined();

      service
        .getExternalUrl('host_details', {'host_name': 'test-host'})
        .subscribe((url) => {
          expect(url).toBe('http://arsenal/hosts/test-host');
          done();
        });

      const request = mockWin.parent.postMessage.calls.mostRecent().args[0];
      messageListener({
        data: {
          type: 'GET_EXTERNAL_URL_RESPONSE',
          requestId: request.requestId,
          url: 'http://arsenal/hosts/test-host',
        },
      });
    });

    it('should throw error if parent does not respond in 5s', (done) => {
      jasmine.clock().install();
      mockWin.location.search = '?is_embedded_mode=true&origin=test-origin';
      mockWin.self = {id: 'iframe'};
      mockWin.top = {id: 'parent'};
      TestBed.configureTestingModule({
        providers: [UrlService, {provide: DOCUMENT, useValue: mockDocument}],
      });
      service = TestBed.inject(UrlService);

      service
        .getExternalUrl('host_details', {'host_name': 'test-host'})
        .subscribe({
          next: () => {
            done.fail('Should have timed out');
          },
          error: (error) => {
            expect(error.name).toBe('TimeoutError');
            jasmine.clock().uninstall();
            done();
          },
        });

      jasmine.clock().tick(5001); // Exceed 5s timeout
    });
  });

  describe('notifyNavigated', () => {
    it('should send postMessage to parent window if in embedded mode', () => {
      mockWin.location.search = '?is_embedded_mode=true&origin=test-origin';
      mockWin.self = {id: 'iframe'};
      mockWin.top = {id: 'parent'};
      TestBed.configureTestingModule({
        providers: [UrlService, {provide: DOCUMENT, useValue: mockDocument}],
      });
      service = TestBed.inject(UrlService);

      service.notifyNavigated('host_details', {'host_name': 'test-host'});

      expect(mockWin.parent.postMessage).toHaveBeenCalledWith(
        {
          type: 'NAVIGATED',
          page: 'host_details',
          params: {'host_name': 'test-host'},
        },
        'test-origin',
      );
    });

    it('should not send postMessage to parent window if not in embedded mode', () => {
      mockWin.location.search = '';
      TestBed.configureTestingModule({
        providers: [UrlService, {provide: DOCUMENT, useValue: mockDocument}],
      });
      service = TestBed.inject(UrlService);

      service.notifyNavigated('host_details', {'host_name': 'test-host'});

      expect(mockWin.parent.postMessage).not.toHaveBeenCalled();
    });
  });
});
