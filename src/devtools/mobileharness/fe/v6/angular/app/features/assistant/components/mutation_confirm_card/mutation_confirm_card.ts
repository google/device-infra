import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {
  ActionConfirmationRequest,
  ActionDecisionStatus,
  ActionRiskLevel,
} from '../../../../core/models/assistant';

/**
 * Standalone human-in-the-loop confirmation card for state-changing lab actions
 * (e.g. RebootDevice, DrainHost, ReleaseLabServer).
 */
@Component({
  selector: 'app-mutation-confirm-card',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule],
  templateUrl: './mutation_confirm_card.ng.html',
  styleUrl: './mutation_confirm_card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MutationConfirmCardComponent {
  /** Action confirmation payload emitted by the assistant turn. */
  readonly action = input.required<ActionConfirmationRequest>();

  /** Whether action buttons should be disabled (e.g. while decision RPC is in flight). */
  readonly disabled = input<boolean>(false);

  /** Emits when the operator clicks "Approve Action". */
  readonly approve = output<ActionConfirmationRequest>();

  /** Emits when the operator clicks "Reject". */
  readonly reject = output<ActionConfirmationRequest>();

  readonly ActionDecisionStatus = ActionDecisionStatus;
  readonly ActionRiskLevel = ActionRiskLevel;

  /** Human-readable risk label for the badge. */
  readonly riskLabel = computed(() => {
    switch (this.action().riskLevel) {
      case ActionRiskLevel.HIGH:
        return 'HIGH RISK';
      case ActionRiskLevel.MEDIUM:
        return 'MEDIUM RISK';
      case ActionRiskLevel.LOW:
      default:
        return 'LOW RISK';
    }
  });

  /** CSS modifier class for the risk level pill. */
  readonly riskClass = computed(() => {
    switch (this.action().riskLevel) {
      case ActionRiskLevel.HIGH:
        return 'risk-badge--high';
      case ActionRiskLevel.MEDIUM:
        return 'risk-badge--medium';
      case ActionRiskLevel.LOW:
      default:
        return 'risk-badge--low';
    }
  });

  /** Formatted JSON parameters if present. */
  readonly formattedParams = computed(() => {
    const raw = this.action().parametersJson;
    if (!raw) {
      return null;
    }
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  });

  onApprove(): void {
    if (
      !this.disabled() &&
      this.action().status === ActionDecisionStatus.PENDING
    ) {
      this.approve.emit(this.action());
    }
  }

  onReject(): void {
    if (
      !this.disabled() &&
      this.action().status === ActionDecisionStatus.PENDING
    ) {
      this.reject.emit(this.action());
    }
  }
}
