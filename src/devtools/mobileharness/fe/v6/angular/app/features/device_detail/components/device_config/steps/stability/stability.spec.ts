import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {Stability} from './stability';

describe('Stability Component', () => {
  let fixture: ComponentFixture<Stability>;
  let component: Stability;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        Stability,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Stability);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  describe('Editability', () => {
    it('should enable inputs when editable is true', async () => {
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {editable: true},
      });
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input');
      expect(inputs.length).toBe(2);
      inputs.forEach((input: HTMLInputElement) => {
        expect(input.disabled).toBeFalse();
      });
    });

    it('should disable inputs when editable is false', async () => {
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {editable: false},
      });
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input');
      expect(inputs.length).toBe(2);
      inputs.forEach((input: HTMLInputElement) => {
        expect(input.disabled).toBeTrue();
      });
    });

    it('should disable inputs when editable is undefined (omitted)', async () => {
      fixture.componentRef.setInput('uiStatus', {
        visible: true,
        editability: {}, // editable is undefined
      });
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input');
      expect(inputs.length).toBe(2);
      inputs.forEach((input: HTMLInputElement) => {
        expect(input.disabled).toBeTrue();
      });
    });
  });
});
