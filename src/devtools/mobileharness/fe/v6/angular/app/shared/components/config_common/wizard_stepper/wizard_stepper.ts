import {StepperSelectionEvent} from '@angular/cdk/stepper';
import {CommonModule} from '@angular/common';
import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output, signal, TemplateRef} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatDialogModule} from '@angular/material/dialog';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatStepperModule} from '@angular/material/stepper';

import {Dialog} from '../dialog/dialog';

/**
 * Wizard step.
 */
export interface WizardStep {
  id: string;
  label: string;
  template: TemplateRef<{}>;
}

/**
 * Wizard stepper component.
 *
 * This component is used to render a wizard stepper. It is a generic component
 * that can be used to render any wizard stepper.
 */
@Component({
  selector: 'app-wizard-stepper',
  standalone: true,
  templateUrl: './wizard_stepper.ng.html',
  styleUrl: './wizard_stepper.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatStepperModule,
    Dialog,
  ],
})
export class WizardStepper implements OnInit {
  @Input() title = '';
  @Input() type: 'device'|'host' = 'device';
  @Input() width = '72rem';

  @Input() footerType: 'normal'|'permission-check'|'none' = 'permission-check';

  @Input() WIZARD_STEPS: WizardStep[] = [];

  @Input() nextStepDisabled = false;
  @Input() applyChangesDisabled = false;

  @Input() currentStep = '';
  @Output() readonly currentStepChange = new EventEmitter<string>();

  @Output() readonly apply = new EventEmitter<void>();

  verifying = signal(false);

  currentStepIndex = signal(0);

  subtitle = signal('');

  ngOnInit() {
    this.currentStepIndex.set(
        this.WIZARD_STEPS.findIndex((step) => step.id === this.currentStep),
    );

    this.subtitle.set(this.getSubtitle());
  }

  getSubtitle() {
    const step = this.currentStepIndex() + 1;
    const label = this.WIZARD_STEPS[this.currentStepIndex()].label;
    return `Step ${step} of ${this.WIZARD_STEPS.length}: ${label}`;
  }

  setCurrentStep(): void {
    this.subtitle.set(this.getSubtitle());

    this.currentStep = this.WIZARD_STEPS[this.currentStepIndex()].id;
    this.currentStepChange.emit(this.currentStep);
  }

  onStepChange(event: StepperSelectionEvent) {
    this.currentStepIndex.set(event.selectedIndex);
    this.setCurrentStep();
  }

  previousStep() {
    this.currentStepIndex.set(this.currentStepIndex() - 1);
    this.setCurrentStep();
  }

  nextStep() {
    this.currentStepIndex.set(this.currentStepIndex() + 1);
    this.setCurrentStep();
  }

  applyChanges() {
    this.apply.emit();
  }
}
