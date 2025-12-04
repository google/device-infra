import {Clipboard} from '@angular/cdk/clipboard';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {
  MatTestDialogOpener,
  MatTestDialogOpenerModule,
} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {FlashDialogData} from '../../../../core/models/device_action';
import {SnackBarService} from '../../../../shared/services/snackbar_service';
import {FlashDialog} from './flash_dialog';

describe('FlashDialog', () => {
  let component: FlashDialog;
  let fixture: ComponentFixture<MatTestDialogOpener<FlashDialog>>;
  let clipboard: Clipboard;
  let snackBarService: SnackBarService;

  const dialogData: FlashDialogData = {
    deviceId: 'test-device',
    hostName: 'test-host',
    deviceType: 'AndroidRealDevice',
    requiredDimensions: 'pool=test',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        FlashDialog,
        NoopAnimationsModule, // This makes test faster and more stable.
        MatTestDialogOpenerModule,
      ],
      providers: [
        {provide: MAT_DIALOG_DATA, useValue: dialogData},
        {
          provide: SnackBarService,
          useValue: jasmine.createSpyObj('SnackBarService', [
            'showSuccess',
            'showError',
          ]),
        },
        Clipboard,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(
      MatTestDialogOpener.withComponent(FlashDialog, {data: dialogData}),
    );
    fixture.detectChanges();
    component = fixture.componentInstance.dialogRef.componentInstance;
    clipboard = TestBed.inject(Clipboard);
    snackBarService = TestBed.inject(SnackBarService);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should generate the correct initial command', () => {
    const expectedCommand = `tools/android/tab/cli/device_flash.sh \\\n --uuid="test-device" \\\n --hostname="test-host" \\\n --device_type="AndroidRealDevice" \\\n --required_dimensions="pool=test" \\\n --branch="git_main" \\\n --build_id="12345678" \\\n --build_target="husky-next-userdebug"`;
    expect(component.command()).toEqual(expectedCommand);
  });

  it('should update the command when buildId changes', () => {
    component.buildId = '98765432';
    component.updateCommand();
    fixture.detectChanges();
    expect(component.command()).toContain('--build_id="98765432"');
  });

  it('should call clipboard.copy and show success snackbar on copy', () => {
    spyOn(clipboard, 'copy').and.returnValue(true);
    component.copyCommand();
    expect(clipboard.copy).toHaveBeenCalledWith(component.command());
    expect(snackBarService.showSuccess).toHaveBeenCalledWith(
      'Command copied to clipboard!',
    );
  });
});
