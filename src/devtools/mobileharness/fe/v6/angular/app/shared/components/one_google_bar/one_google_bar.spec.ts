import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {
  APP_DATA,
  type AppData,
} from '@deviceinfra/app/core/models/app_data';
import {OneGoogleBar} from './one_google_bar';

describe('OneGoogleBar Component', () => {
  let component: OneGoogleBar;
  let fixture: ComponentFixture<OneGoogleBar>;

  const appData: AppData = {
    labConsoleServerPort: '9000',
    overrideLabConsoleServerUrl: '',
    applicationId: 'mobile-harness-fe',
    adbVersion: '1.0',
    mttVersion: '1.0',
    labconsoleVersion: '2026.10.21 (cl/123456789)',
    masterVersion: '5.29.0',
    email: 'tianch@google.com',
    userDisplayName: 'Chen Tian',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, OneGoogleBar],
      providers: [{provide: APP_DATA, useValue: appData}],
    }).compileComponents();

    fixture = TestBed.createComponent(OneGoogleBar);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should redirect to Google Logout with current URL when logout is called', () => {
    const redirectSpy = spyOn(component, 'redirectToUrl');
    component.logout();
    expect(redirectSpy).toHaveBeenCalledWith(
      `https://accounts.google.com/Logout?continue=${encodeURIComponent(window.location.href)}`,
    );
  });

  it('should redirect to Google AccountChooser with current URL when switchAccount is called', () => {
    const redirectSpy = spyOn(component, 'redirectToUrl');
    component.switchAccount();
    expect(redirectSpy).toHaveBeenCalledWith(
      `https://accounts.google.com/AccountChooser?continue=${encodeURIComponent(window.location.href)}&faa=1`,
    );
  });

  it('should format account button label and user initial with fallback on avatar error', async () => {
    expect(component.getAccountButtonLabel()).toBe(
      'Google Account: Chen Tian (tianch@google.com)',
    );
    expect(component.getUserInitial()).toBe('C');

    expect(component.avatarLoadFailed).toBeFalse();
    component.onAvatarError();
    expect(component.avatarLoadFailed).toBeTrue();

    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, OneGoogleBar],
      providers: [
        {
          provide: APP_DATA,
          useValue: {
            ...appData,
            userDisplayName: '',
          },
        },
      ],
    }).compileComponents();

    const emailOnlyComponent =
      TestBed.createComponent(OneGoogleBar).componentInstance;
    expect(emailOnlyComponent.getAccountButtonLabel()).toBe(
      'Google Account: tianch@google.com',
    );
    expect(emailOnlyComponent.getUserInitial()).toBe('T');
  });
});
