import {TemplateRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';

import {WizardStepper} from './wizard_stepper';

describe('WizardStepper Component', () => {
  let component: WizardStepper;
  let fixture: ComponentFixture<WizardStepper>;

  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            WizardStepper,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [
            provideRouter([]),
            {
              provide: CONFIG_SERVICE,
              useValue: {
                checkHostWritePermission: () => of({hasPermission: true}),
                checkDeviceWritePermission: () => of({hasPermission: true}),
              },
            },
          ],
        })
        .compileComponents();

    fixture = TestBed.createComponent(WizardStepper);
    component = fixture.componentInstance;
  });

  it('should be created', () => {
    component.WIZARD_STEPS = [
      {id: 'test', label: 'test', template: null as unknown as TemplateRef<{}>}
    ];
    component.currentStep = 'test';
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });
});
