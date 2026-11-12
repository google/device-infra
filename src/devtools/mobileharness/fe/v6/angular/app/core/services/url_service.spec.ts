import {DOCUMENT} from '@angular/common';
import {TestBed} from '@angular/core/testing';

import {Environment} from './environment';
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

  describe('embedded header slot (ATS host only)', () => {
    let isGoogleInternal: boolean;

    beforeEach(() => {
      isGoogleInternal = false;
    });

    function configure() {
      TestBed.configureTestingModule({
        providers: [
          UrlService,
          {provide: DOCUMENT, useValue: mockDocument},
          {
            provide: Environment,
            useValue: {isGoogleInternal: () => isGoogleInternal},
          },
        ],
      });
      service = TestBed.inject(UrlService);
    }

    function setUpEmbedded() {
      mockWin.location.search = '?is_embedded_mode=true&origin=test-origin';
      mockWin.self = {id: 'iframe'};
      mockWin.top = {id: 'parent'};
      configure();
      return mockWin.addEventListener.calls
        .all()
        .find((call) => call.args[0] === 'message')?.args[1];
    }

    it('reports ATS embedded mode only when embedded in the OSS build', () => {
      setUpEmbedded();
      expect(service.isInEmbeddedMode()).toBeTrue();
      expect(service.isInAtsEmbeddedMode()).toBeTrue();

      TestBed.resetTestingModule();
      isGoogleInternal = true;
      setUpEmbedded();
      expect(service.isInEmbeddedMode()).toBeTrue();
      expect(service.isInAtsEmbeddedMode()).toBeFalse();
    });

    it('requests the slot geometry from the parent window in ATS embedded mode', () => {
      setUpEmbedded();

      service.requestEmbeddedHeaderSlot();

      expect(mockWin.parent.postMessage).toHaveBeenCalledWith(
        {type: 'EMBEDDED_HEADER_SLOT_REQUEST'},
        'test-origin',
      );
    });

    it('does not request the slot geometry when not in embedded mode', () => {
      mockWin.location.search = '';
      configure();

      service.requestEmbeddedHeaderSlot();

      expect(mockWin.parent.postMessage).not.toHaveBeenCalled();
      expect(service.embeddedHeaderSlot()).toBeNull();
    });

    it('does not request the slot geometry in the Google-internal embedded mode', () => {
      isGoogleInternal = true;
      setUpEmbedded();

      service.requestEmbeddedHeaderSlot();

      expect(mockWin.parent.postMessage).not.toHaveBeenCalled();
    });

    it('stores the slot geometry sent by the parent window', () => {
      const messageListener = setUpEmbedded();
      expect(service.embeddedHeaderSlot()).toBeNull();

      messageListener({
        data: {
          type: 'EMBEDDED_HEADER_SLOT',
          left: 247,
          width: 900.5,
          height: 64,
          borderColor: 'rgb(224, 224, 224)',
        },
      });

      expect(service.embeddedHeaderSlot()).toEqual({
        left: 247,
        width: 900.5,
        height: 64,
        borderColor: 'rgb(224, 224, 224)',
      });
    });

    it('ignores malformed slot messages and logs an error', () => {
      const messageListener = setUpEmbedded();
      const errorSpy = spyOn(console, 'error');

      messageListener({
        data: {type: 'EMBEDDED_HEADER_SLOT', left: 'x', width: 10, height: 64},
      });
      messageListener({
        data: {type: 'EMBEDDED_HEADER_SLOT', left: 0, width: -1, height: 64},
      });

      expect(service.embeddedHeaderSlot()).toBeNull();
      expect(errorSpy).toHaveBeenCalledTimes(2);
    });

    it('logs an error for unknown message types', () => {
      const messageListener = setUpEmbedded();
      const errorSpy = spyOn(console, 'error');

      const data = {type: 'BOGUS'};
      messageListener({source: mockWin.parent, data});
      expect(errorSpy).toHaveBeenCalledOnceWith(
        'Unknown message type:',
        'BOGUS',
        data,
      );

      // Messages without a type are reported as well; empty messages are not.
      errorSpy.calls.reset();
      messageListener({source: {id: 'other'}, data: {foo: 'bar'}});
      expect(errorSpy).toHaveBeenCalledOnceWith('Unknown message type:', undefined, {
        foo: 'bar',
      });
      errorSpy.calls.reset();
      messageListener({source: mockWin.parent, data: null});
      expect(errorSpy).not.toHaveBeenCalled();
    });
  });
});
