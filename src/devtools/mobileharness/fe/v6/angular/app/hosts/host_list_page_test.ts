import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {APP_DATA, AppData} from '../services';

import {HostListPage} from './host_list_page';

describe('HostListPage Component', () => {
  let component: HostListPage;
  let fixture: ComponentFixture<HostListPage>;
  const appData: AppData = {
    adbVersion: '1.0',
    analyticsTrackingId: '123',
    hostname: 'host',
    fileServerRoot: 'root',
    isGoogle: false,
    mttVersion: '1.0',
    netdataUrl: '',
    labConsoleServerPort: '8888',
    setupWizardCompleted: true,
    isAtsLabInstance: false,
    isOmniLabBased: false,
  };

  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            HostListPage,
            NoopAnimationsModule,
          ],
          providers: [
            {provide: APP_DATA, useValue: appData},
          ],
        })
        .compileComponents();

    fixture = TestBed.createComponent(HostListPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
