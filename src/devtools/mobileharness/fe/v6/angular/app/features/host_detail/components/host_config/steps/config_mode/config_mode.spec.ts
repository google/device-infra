import {TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {ConfigMode} from './config_mode';

describe('ConfigMode Component', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ConfigMode,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(ConfigMode);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    expect(comp).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.mode-title').innerText).toBe(
      'Device Configuration Mode',
    );
  });

  describe('Editability', () => {
    it('should enable cards and buttons when editable is true', () => {
      const fixture = TestBed.createComponent(ConfigMode);
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {editable: true},
      });
      fixture.detectChanges();

      const grid = fixture.nativeElement.querySelector('.mode-grid');
      expect(grid.classList.contains('disabled')).toBeFalse();

      const buttons = fixture.nativeElement.querySelectorAll('.choice-card');
      expect(buttons.length).toBe(2);
      buttons.forEach((btn: HTMLButtonElement) => {
        expect(btn.disabled).toBeFalse();
      });
    });

    it('should disable cards and buttons when editable is false', () => {
      const fixture = TestBed.createComponent(ConfigMode);
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {editable: false},
      });
      fixture.detectChanges();

      const grid = fixture.nativeElement.querySelector('.mode-grid');
      expect(grid.classList.contains('disabled')).toBeTrue();

      const buttons = fixture.nativeElement.querySelectorAll('.choice-card');
      expect(buttons.length).toBe(2);
      buttons.forEach((btn: HTMLButtonElement) => {
        expect(btn.disabled).toBeTrue();
      });
    });

    it('should disable cards and buttons when editable is undefined (omitted)', () => {
      const fixture = TestBed.createComponent(ConfigMode);
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {}, // editable is undefined
      });
      fixture.detectChanges();

      const grid = fixture.nativeElement.querySelector('.mode-grid');
      expect(grid.classList.contains('disabled')).toBeTrue();

      const buttons = fixture.nativeElement.querySelectorAll('.choice-card');
      expect(buttons.length).toBe(2);
      buttons.forEach((btn: HTMLButtonElement) => {
        expect(btn.disabled).toBeTrue();
      });
    });
  });
});
