import {DOCUMENT} from '@angular/common';
import {TestBed} from '@angular/core/testing';

import {DebugService} from './debug_service';

interface MockWindow {
  location: {search: string};
}

interface MockDocument {
  defaultView: MockWindow;
}

describe('DebugService', () => {
  let service: DebugService;
  let mockDocument: MockDocument;
  let mockWin: MockWindow;

  beforeEach(() => {
    mockWin = {
      location: {
        search: '',
      },
    };
    mockDocument = {
      defaultView: mockWin,
    };
  });

  it('should be created', () => {
    TestBed.configureTestingModule({
      providers: [DebugService, {provide: DOCUMENT, useValue: mockDocument}],
    });
    service = TestBed.inject(DebugService);
    expect(service).toBeTruthy();
  });

  describe('isDebug', () => {
    it('should return true if debug is true in query params', () => {
      mockWin.location.search = '?debug=true';
      TestBed.configureTestingModule({
        providers: [DebugService, {provide: DOCUMENT, useValue: mockDocument}],
      });
      service = TestBed.inject(DebugService);
      expect(service.isDebug()).toBeTrue();
    });

    it('should return false if debug is not true in query params', () => {
      mockWin.location.search = '?debug=false';
      TestBed.configureTestingModule({
        providers: [DebugService, {provide: DOCUMENT, useValue: mockDocument}],
      });
      service = TestBed.inject(DebugService);
      expect(service.isDebug()).toBeFalse();
    });

    it('should return false if debug is missing from query params', () => {
      mockWin.location.search = '';
      TestBed.configureTestingModule({
        providers: [DebugService, {provide: DOCUMENT, useValue: mockDocument}],
      });
      service = TestBed.inject(DebugService);
      expect(service.isDebug()).toBeFalse();
    });
  });
});
