
import {HarnessLoader} from '@angular/cdk/testing';
import {TestbedHarnessEnvironment} from '@angular/cdk/testing/testbed';
import {TestBed} from '@angular/core/testing';
import {MatButtonHarness} from '@angular/material/button/testing';
import {MatDialogModule, MatDialogRef} from '@angular/material/dialog';
import {MatTestDialogOpener, MatTestDialogOpenerModule} from '@angular/material/dialog/testing';
import {MatInputHarness} from '@angular/material/input/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {of, throwError} from 'rxjs';

import {CONFIG_SERVICE} from '../../../../../core/services/config/config_service';

import {DeviceEmpty} from './device_empty';

describe('DeviceEmpty Component', () => {
  const mockConfigService = jasmine.createSpyObj('CONFIG_SERVICE', [
    'getHostDefaultDeviceConfig',
    'getDeviceConfig',
    'checkDeviceWritePermission',
  ]);

  beforeEach(async () => {
    mockConfigService.getHostDefaultDeviceConfig.and.returnValue(of({}));
    mockConfigService.getDeviceConfig.and.returnValue(of(null));
    mockConfigService.checkDeviceWritePermission.and.returnValue(
        of({hasPermission: true, userName: 'test-user'}),
    );

    await TestBed
        .configureTestingModule({
          imports: [
            DeviceEmpty,
            NoopAnimationsModule,  // This makes test faster and more stable.
            MatDialogModule,
            MatTestDialogOpenerModule,
          ],
          providers: [
            provideRouter([]),
            {provide: CONFIG_SERVICE, useValue: mockConfigService},
          ],
        })
        .compileComponents();
  });

  async function openDialog(
      data = {
        deviceId: 'test-id',
        hostName: 'test-host',
        title: 'test-title',
      },
  ) {
    const fixture = TestBed.createComponent(
        MatTestDialogOpener.withComponent(DeviceEmpty, {data}),
    );
    fixture.detectChanges();
    const loader = TestbedHarnessEnvironment.documentRootLoader(fixture);
    const dialogRef = fixture.componentInstance.dialogRef;
    spyOn(dialogRef, 'close').and.callThrough();
    return {fixture, loader, dialogRef};
  }

  it('should be created', async () => {
    const {fixture} = await openDialog();
    expect(fixture.componentInstance.dialogRef).toBeTruthy();
    expect(
        fixture.componentInstance.dialogRef.componentInstance,
        )
        .toBeTruthy();
  });

  it('should close dialog with action "new" when "Create New Configuration" is clicked',
     async () => {
       const {loader, dialogRef} = await openDialog();

       const createNewButton =
           await (loader as TestbedHarnessEnvironment)
               .locatorFor('button.choice-card:nth-of-type(1)')();
       await createNewButton.click();

       expect(dialogRef.close).toHaveBeenCalledWith({
         action: 'new',
         deviceId: 'test-id',
         config: null,
       });
     });

  it('should close dialog with action "copy" and host config when "Copy from Host" is clicked and host config exists',
     async () => {
       const hostConfig = {owners: ['owner1']};
       mockConfigService.getHostDefaultDeviceConfig.and.returnValue(
           of(hostConfig),
       );
       const {loader, dialogRef} = await openDialog();

       const copyFromHostButton =
           await (loader as TestbedHarnessEnvironment)
               .locatorFor('button.choice-card:nth-of-type(2)')();
       await copyFromHostButton.click();

       expect(dialogRef.close).toHaveBeenCalledWith({
         action: 'copy',
         deviceId: 'test-id',
         config: hostConfig,
       });
     });

  it('should show host missing message when "Copy from Host" is clicked and host config does not exist',
     async () => {
       mockConfigService.getHostDefaultDeviceConfig.and.returnValue(of(null));
       const {loader} = await openDialog();

       const copyFromHostButton =
           await (loader as TestbedHarnessEnvironment)
               .locatorFor('button.choice-card:nth-of-type(2)')();
       await copyFromHostButton.click();

       const hostMissingContent =
           await (loader as TestbedHarnessEnvironment)
               .locatorForOptional('.host-missing-container')();
       expect(hostMissingContent).toBeTruthy();
     });

  it('should show copy from another device section when "Copy from Another Device" is clicked',
     async () => {
       const {loader} = await openDialog();

       const copyFromAnotherDeviceButton =
           await (loader as TestbedHarnessEnvironment)
               .locatorFor('button.choice-card:nth-of-type(3)')();
       await copyFromAnotherDeviceButton.click();

       const copyFromContainer =
           await (loader as TestbedHarnessEnvironment)
               .locatorForOptional('.copy-from-container')();
       expect(copyFromContainer).toBeTruthy();
     });

  describe('Copy from another device', () => {
    let loader: HarnessLoader;
    let dialogRef: MatDialogRef<DeviceEmpty>;

    beforeEach(async () => {
      const res = await openDialog();
      loader = res.loader;
      dialogRef = res.dialogRef;

      const copyFromAnotherDeviceButton =
          await (loader as TestbedHarnessEnvironment)
              .locatorFor('button.choice-card:nth-of-type(3)')();
      await copyFromAnotherDeviceButton.click();
    });

    it('should show error if load is clicked with empty UUID', async () => {
      const loadButton = await loader.getHarness(
          MatButtonHarness.with({selector: '.load-button'}),
      );
      await loadButton.click();

      const errorMessage = await (loader as TestbedHarnessEnvironment)
                               .locatorFor('.copy-error-message')();
      expect(await errorMessage.text()).toContain('Please enter a device UUID');
    });

    it('should show error if device config not found', async () => {
      mockConfigService.getDeviceConfig.and.returnValue(of(null));
      const input = await loader.getHarness(MatInputHarness);
      await input.setValue('another-id');
      const loadButton = await loader.getHarness(
          MatButtonHarness.with({selector: '.load-button'}),
      );
      await loadButton.click();

      const errorMessage = await (loader as TestbedHarnessEnvironment)
                               .locatorFor('.copy-error-message')();
      expect(await errorMessage.text())
          .toContain(
              'Device UUID not found or has no configuration',
          );
    });

    it('should show error if getDeviceConfig fails', async () => {
      mockConfigService.getDeviceConfig.and.returnValue(
          throwError(() => new Error('An error')),
      );
      const input = await loader.getHarness(MatInputHarness);
      await input.setValue('another-id');
      const loadButton = await loader.getHarness(
          MatButtonHarness.with({selector: '.load-button'}),
      );
      await loadButton.click();

      const errorMessage = await (loader as TestbedHarnessEnvironment)
                               .locatorFor('.copy-error-message')();
      expect(await errorMessage.text()).toContain('An error');
    });

    it('should close dialog with action "copy" and device config if loading succeeds',
       async () => {
         const deviceConfig = {owners: ['owner2']};
         mockConfigService.getDeviceConfig.and.returnValue(
             of({deviceConfig}),
         );
         const input = await loader.getHarness(MatInputHarness);
         await input.setValue('another-id');
         const loadButton = await loader.getHarness(
             MatButtonHarness.with({selector: '.load-button'}),
         );
         await loadButton.click();

         expect(dialogRef.close).toHaveBeenCalledWith({
           action: 'copy',
           deviceId: 'test-id',
           config: deviceConfig,
         });
       });
  });
});
