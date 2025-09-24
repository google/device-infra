import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {RouterTestingModule} from '@angular/router/testing';

import {App} from './app';
import {APP_DATA, AppData} from './services/app_data';

describe('App Component', () => {
  let component: App;
  let fixture: ComponentFixture<App>;
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
            App,
            NoopAnimationsModule,
            RouterTestingModule,
          ],
          providers: [
            {provide: APP_DATA, useValue: appData},
          ],
        })
        .compileComponents();

    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
