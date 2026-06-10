import {Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {FormControl, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {ToggleSwitch} from './toggle_switch';

@Component({
  standalone: true,
  imports: [ToggleSwitch, ReactiveFormsModule],
  template: `
    <form [formGroup]="form">
      <toggle-switch formControlName="toggle"></toggle-switch>
    </form>
  `,
})
class TestHostComponent {
  form = new FormGroup({
    toggle: new FormControl(false),
  });
}

describe('ToggleSwitch Component', () => {
  let component: ToggleSwitch;
  let fixture: ComponentFixture<ToggleSwitch>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ToggleSwitch,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ToggleSwitch);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });

  it('should update form control value on toggle', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [TestHostComponent, NoopAnimationsModule],
    }).compileComponents();

    const hostFixture = TestBed.createComponent(TestHostComponent);
    const hostComponent = hostFixture.componentInstance;
    hostFixture.detectChanges();

    const toggleEl = hostFixture.nativeElement.querySelector('.toggle-switch');
    expect(hostComponent.form.get('toggle')?.value).toBeFalse();
    expect(hostComponent.form.get('toggle')?.touched).toBeFalse();

    toggleEl.click();
    hostFixture.detectChanges();

    expect(hostComponent.form.get('toggle')?.value).toBeTrue();
    expect(hostComponent.form.get('toggle')?.touched).toBeTrue();
  });
});
