import {ComponentFixture, TestBed} from '@angular/core/testing';
import {LabServerOperationContent} from './lab_server_operation_content';

describe('LabServerOperationContent', () => {
  let component: LabServerOperationContent;
  let fixture: ComponentFixture<LabServerOperationContent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LabServerOperationContent],
    }).compileComponents();

    fixture = TestBed.createComponent(LabServerOperationContent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('isWarningState', () => {
    it('should return true for STOP operation when status is DRAINING', () => {
      component.operation = 'STOP';
      component.status = 'DRAINING';
      expect(component.isWarningState()).toBeTrue();
    });

    it('should return false for STOP operation when status is RUNNING', () => {
      component.operation = 'STOP';
      component.status = 'RUNNING';
      expect(component.isWarningState()).toBeFalse();
    });

    it('should return false when operation is not STOP', () => {
      component.operation = 'RESTART';
      component.status = 'DRAINING';
      expect(component.isWarningState()).toBeFalse();
    });
  });
});
