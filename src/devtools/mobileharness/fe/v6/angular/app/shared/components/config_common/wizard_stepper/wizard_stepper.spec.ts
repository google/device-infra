import {TemplateRef} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../core/services/config/config_service';

import {WizardStep, WizardStepper} from './wizard_stepper';

describe('WizardStepper Component', () => {
  let component: WizardStepper;
  let fixture: ComponentFixture<WizardStepper>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        WizardStepper,
        NoopAnimationsModule, // This makes test faster and more stable.
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
    }).compileComponents();

    fixture = TestBed.createComponent(WizardStepper);
    component = fixture.componentInstance;
  });

  it('should be created', () => {
    component.WIZARD_STEPS = [
      {id: 'test', label: 'test', template: null as unknown as TemplateRef<{}>},
    ];
    component.currentStep = 'test';
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  interface WizardStepperWithPrivate {
    syncStepIndex(): void;
  }

  it('should find and set step index by currentStep ID on init', () => {
    component.WIZARD_STEPS = [
      {
        id: 'step1',
        label: 'Step 1',
        template: null as unknown as TemplateRef<{}>,
      },
      {
        id: 'step2',
        label: 'Step 2',
        template: null as unknown as TemplateRef<{}>,
      },
    ];
    component.currentStep = 'step2';

    fixture.detectChanges();

    expect(component.currentStepIndex()).toBe(1);
    expect(component.subtitle()).toBe('Step 2 of 2: Step 2');
  });

  it('should transition back to previous steps correctly', () => {
    component.WIZARD_STEPS = [
      {
        id: 'step1',
        label: 'Step 1',
        template: null as unknown as TemplateRef<{}>,
      },
      {
        id: 'step2',
        label: 'Step 2',
        template: null as unknown as TemplateRef<{}>,
      },
    ];
    component.currentStep = 'step2';
    (component as unknown as WizardStepperWithPrivate).syncStepIndex();
    expect(component.currentStepIndex()).toBe(1);

    component.currentStep = 'step1';
    (component as unknown as WizardStepperWithPrivate).syncStepIndex();
    expect(component.currentStepIndex()).toBe(0);
    expect(component.subtitle()).toBe('Step 1 of 2: Step 1');
  });

  it('should not throw error if WIZARD_STEPS is undefined in syncStepIndex', () => {
    component.WIZARD_STEPS = undefined as unknown as WizardStep[];
    component.currentStep = 'test';
    expect(() => {
      (component as unknown as WizardStepperWithPrivate).syncStepIndex();
    }).not.toThrow();
  });
});
