import {ComponentFixture, TestBed} from '@angular/core/testing';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  TestbedConfigViewer,
  TestbedConfigViewerData,
} from './testbed_config_viewer';

describe('TestbedConfigViewer', () => {
  let fixture: ComponentFixture<MatTestDialogOpener<TestbedConfigViewer>>;
  let component: TestbedConfigViewer;
  const dialogData: TestbedConfigViewerData = {
    deviceId: 'test-device-id',
    yamlContent: '- line1\n- line2',
    codeSearchLink: 'some/path/to/config.yaml',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        TestbedConfigViewer,
        NoopAnimationsModule,
        MatTestDialogOpenerModule,
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(TestbedConfigViewer, {
        data: dialogData,
      }),
    );
    component = fixture.componentInstance.dialogRef.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
