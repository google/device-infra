import {ComponentFixture, TestBed} from '@angular/core/testing';

import {ClipboardService} from '../../../../../shared/services/clipboard_service';
import {WifiDetailChip} from './wifi_detail_chip';

describe('WifiDetailChip', () => {
  let fixture: ComponentFixture<WifiDetailChip>;
  let component: WifiDetailChip;
  let clipboardService: ClipboardService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WifiDetailChip],
    }).compileComponents();

    fixture = TestBed.createComponent(WifiDetailChip);
    component = fixture.componentInstance;
    clipboardService = TestBed.inject(ClipboardService);
    fixture.componentRef.setInput('wifi', {
      ssid: 'test-wifi',
      psk: 'secret-pass',
      scanSsid: false,
    });
    fixture.detectChanges();
  });

  it('should render the SSID in the chip', () => {
    const chipEl = fixture.nativeElement.querySelector('.bw-wifi-chip');
    expect(chipEl?.textContent).toContain('test-wifi');
  });

  it('should not open overlay when ssid is empty', () => {
    fixture.componentRef.setInput('wifi', {ssid: ''});
    fixture.detectChanges();

    component.open();
    expect(component.isOpen()).toBeFalse();
  });

  it('should open overlay on open() and reset state on close()', () => {
    component.open();
    expect(component.isOpen()).toBeTrue();

    component.togglePsk();
    expect(component.showPsk()).toBeTrue();

    component.close();
    expect(component.isOpen()).toBeFalse();
    expect(component.showPsk()).toBeFalse();
  });

  it('should close overlay after 200ms debounce in scheduleClose()', () => {
    jasmine.clock().install();
    try {
      component.open();
      expect(component.isOpen()).toBeTrue();

      component.scheduleClose();
      jasmine.clock().tick(100);
      expect(component.isOpen()).toBeTrue();

      jasmine.clock().tick(100);
      expect(component.isOpen()).toBeFalse();
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('should cancel scheduled close when cancelClose() is called', () => {
    jasmine.clock().install();
    try {
      component.open();
      component.scheduleClose();
      jasmine.clock().tick(100);

      component.cancelClose();
      jasmine.clock().tick(200);
      expect(component.isOpen()).toBeTrue();
    } finally {
      jasmine.clock().uninstall();
    }
  });

  describe('copyPsk', () => {
    it('should return early when psk is empty or overlay is closed', () => {
      spyOn(clipboardService, 'copyToClipboard');

      // Closed overlay
      component.copyPsk();
      expect(clipboardService.copyToClipboard).not.toHaveBeenCalled();

      // Open overlay but empty psk
      fixture.componentRef.setInput('wifi', {ssid: 'open-wifi', psk: ''});
      fixture.detectChanges();
      component.open();
      component.copyPsk();
      expect(clipboardService.copyToClipboard).not.toHaveBeenCalled();
    });

    it('should return early when clipboard copy returns false', () => {
      spyOn(clipboardService, 'copyToClipboard').and.returnValue(false);
      component.open();
      component.copyPsk();
      expect(component.copiedPsk()).toBeFalse();
    });

    it('should set copiedPsk to true and reset after 2000ms', () => {
      jasmine.clock().install();
      try {
        spyOn(clipboardService, 'copyToClipboard').and.returnValue(true);
        component.open();
        component.copyPsk();
        expect(component.copiedPsk()).toBeTrue();

        jasmine.clock().tick(2000);
        expect(component.copiedPsk()).toBeFalse();
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('should reset copyTimer when copyPsk is called repeatedly', () => {
      jasmine.clock().install();
      try {
        spyOn(clipboardService, 'copyToClipboard').and.returnValue(true);
        component.open();
        component.copyPsk();
        jasmine.clock().tick(1000);
        expect(component.copiedPsk()).toBeTrue();

        component.copyPsk();
        jasmine.clock().tick(1000);
        expect(component.copiedPsk()).toBeTrue();

        jasmine.clock().tick(1000);
        expect(component.copiedPsk()).toBeFalse();
      } finally {
        jasmine.clock().uninstall();
      }
    });
  });
});
