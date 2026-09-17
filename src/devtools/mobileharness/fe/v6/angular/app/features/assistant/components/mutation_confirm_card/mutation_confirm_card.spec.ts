import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  ActionConfirmationRequest,
  ActionDecisionStatus,
  ActionRiskLevel,
} from '../../../../core/models/assistant';
import {MutationConfirmCardComponent} from './mutation_confirm_card';

describe('MutationConfirmCardComponent', () => {
  let fixture: ComponentFixture<MutationConfirmCardComponent>;
  let component: MutationConfirmCardComponent;

  const mockPendingAction: ActionConfirmationRequest = {
    actionId: 'action_reboot_01',
    toolName: 'RebootDevice',
    targetEntity: 'pixel8-atl-01',
    summary: 'Reboot device pixel8-atl-01 and clear ADB cache.',
    riskLevel: ActionRiskLevel.HIGH,
    status: ActionDecisionStatus.PENDING,
    parametersJson: '{"device_id": "pixel8-atl-01"}',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MutationConfirmCardComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(MutationConfirmCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('action', mockPendingAction);
    fixture.detectChanges();
  });

  it('renders tool name, target entity, summary, and risk badge', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('RebootDevice');
    expect(text).toContain('pixel8-atl-01');
    expect(text).toContain('Reboot device pixel8-atl-01 and clear ADB cache.');
    expect(text).toContain('HIGH RISK');
  });

  it('emits approve when Approve Action button is clicked', () => {
    const approveSpy = spyOn(component.approve, 'emit');
    const approveBtn = fixture.debugElement.query(By.css('.approve-button'));
    approveBtn.nativeElement.click();
    expect(approveSpy).toHaveBeenCalledWith(mockPendingAction);
  });

  it('emits reject when Reject button is clicked', () => {
    const rejectSpy = spyOn(component.reject, 'emit');
    const rejectBtn = fixture.debugElement.query(By.css('.reject-button'));
    rejectBtn.nativeElement.click();
    expect(rejectSpy).toHaveBeenCalledWith(mockPendingAction);
  });

  it('shows locked Approved status banner when status is APPROVED', () => {
    const approvedAction: ActionConfirmationRequest = {
      ...mockPendingAction,
      status: ActionDecisionStatus.APPROVED,
      resolvedBy: 'yowang@google.com',
    };
    fixture.componentRef.setInput('action', approvedAction);
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.approve-button'))).toBeNull();
    const banner = fixture.debugElement.query(
      By.css('.status-banner--approved'),
    );
    expect(banner).not.toBeNull();
    expect(banner.nativeElement.textContent).toContain('Approved');
    expect(banner.nativeElement.textContent).toContain('yowang@google.com');
  });

  it('shows locked Rejected status banner when status is REJECTED', () => {
    const rejectedAction: ActionConfirmationRequest = {
      ...mockPendingAction,
      status: ActionDecisionStatus.REJECTED,
      resolvedBy: 'yowang@google.com',
    };
    fixture.componentRef.setInput('action', rejectedAction);
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.reject-button'))).toBeNull();
    const banner = fixture.debugElement.query(
      By.css('.status-banner--rejected'),
    );
    expect(banner).not.toBeNull();
    expect(banner.nativeElement.textContent).toContain('Rejected');
  });

  it('renders MEDIUM RISK and LOW RISK labels and CSS classes and handles invalid JSON parameters', () => {
    fixture.componentRef.setInput('action', {
      ...mockPendingAction,
      riskLevel: ActionRiskLevel.MEDIUM,
      parametersJson: 'invalid-json',
    });
    fixture.detectChanges();
    expect(component.riskLabel()).toBe('MEDIUM RISK');
    expect(component.riskClass()).toBe('risk-badge--medium');
    expect(component.formattedParams()).toBe('invalid-json');

    fixture.componentRef.setInput('action', {
      ...mockPendingAction,
      riskLevel: ActionRiskLevel.LOW,
      parametersJson: undefined,
    });
    fixture.detectChanges();
    expect(component.riskLabel()).toBe('LOW RISK');
    expect(component.riskClass()).toBe('risk-badge--low');
    expect(component.formattedParams()).toBeNull();
  });

  it('does not emit approve or reject when disabled is true', () => {
    const approveSpy = spyOn(component.approve, 'emit');
    const rejectSpy = spyOn(component.reject, 'emit');
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();

    component.onApprove();
    component.onReject();
    expect(approveSpy).not.toHaveBeenCalled();
    expect(rejectSpy).not.toHaveBeenCalled();
  });
});
