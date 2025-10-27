import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {RouterTestingModule} from '@angular/router/testing';

import {App} from './app';
import {APP_DATA, type AppData} from './core/models/app_data';

describe('App Component', () => {
  let component: App;
  let fixture: ComponentFixture<App>;
  const appData: AppData = {
    adbVersion: '1.0',
    mttVersion: '1.0',
    isDevMode: false,
    labconsoleVersion: '1.0',
    overrideLabConsoleServerUrl: 'https://example.com',
    uiPlatform: 'BoqWebLite',
    applicationId: 'example-staing',
    email: 'tianch@google.com',
    userDisplayName: 'Chen Tian',
    labConsoleServerPort: '8888',
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
            // TODO: add this mocked test data
            {
              provide: APP_DATA,
              useValue: appData,
            },
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
