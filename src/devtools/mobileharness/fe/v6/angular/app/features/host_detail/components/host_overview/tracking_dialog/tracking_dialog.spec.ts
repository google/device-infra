import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of, Subject} from 'rxjs';

import {ReleaseLabServerResponse} from '@deviceinfra/app/core/models/host_action';
import {SnackBarService} from '@deviceinfra/app/shared/services/snackbar_service';
import {TrackingDialog, TrackingDialogData} from './tracking_dialog';

describe('TrackingDialog', () => {
  let component: TrackingDialog;
  let fixture: ComponentFixture<TrackingDialog>;
  let snackBarSpy: jasmine.SpyObj<SnackBarService>;
  let responseSubject: Subject<ReleaseLabServerResponse>;

  const dialogData: TrackingDialogData = {
    hostName: 'test-host',
    version: '2.0.0',
    flags: '--some_flag',
    response$: of(),
  };

  beforeEach(async () => {
    snackBarSpy = jasmine.createSpyObj('SnackBarService', [
      'showSuccess',
      'showError',
    ]);
    responseSubject = new Subject<ReleaseLabServerResponse>();

    // Bulletproof navigator.clipboard polyfill for Karma sandboxed headless Chrome
    if (!navigator.clipboard) {
      Object.defineProperty(navigator, 'clipboard', {
        value: {
          writeText: () => Promise.resolve(),
        },
        configurable: true,
      });
    }

    await TestBed.configureTestingModule({
      imports: [TrackingDialog, NoopAnimationsModule],
      providers: [
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            ...dialogData,
            response$: responseSubject.asObservable(),
          },
        },
        {provide: SnackBarService, useValue: snackBarSpy},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TrackingDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(component.isReady()).toBeFalse();
  });

  it('should handle successful release response', () => {
    const mockResponse: ReleaseLabServerResponse = {
      trackingUrl: 'http://rollout/track-123',
    };

    responseSubject.next(mockResponse);
    responseSubject.complete();

    expect(component.isReady()).toBeTrue();
    expect(component.trackingUrl()).toEqual('http://rollout/track-123');
    expect(component.errorMessage()).toEqual('');
  });

  it('should handle successful release response with empty tracking URL', () => {
    const mockResponse: ReleaseLabServerResponse = {
      trackingUrl: '',
    };

    responseSubject.next(mockResponse);
    responseSubject.complete();

    expect(component.isReady()).toBeTrue();
    expect(component.trackingUrl()).toEqual('');
    expect(component.errorMessage()).toEqual('');
  });


  it('should handle error release response', () => {
    const mockError = {
      statusText: 'Internal Server Error',
      message: 'Deploy failed',
    };

    responseSubject.error(mockError);

    expect(component.isReady()).toBeTrue();
    expect(component.trackingUrl()).toEqual('');
    expect(component.errorMessage()).toEqual('Internal Server Error');
  });

  it('should fallback to message or generic error text on error release response', () => {
    responseSubject.error({message: 'Only message exists'});

    expect(component.isReady()).toBeTrue();
    expect(component.errorMessage()).toEqual('Only message exists');
  });

  it('should fallback to default text if err details are empty', () => {
    responseSubject.error({});

    expect(component.isReady()).toBeTrue();
    expect(component.errorMessage()).toEqual('Failed to generate link');
  });

  it('should copy link to clipboard and show success snackbar', async () => {
    const clipboardSpy = spyOn(
      navigator.clipboard,
      'writeText',
    ).and.returnValue(Promise.resolve());

    component.trackingUrl.set('http://rollout/track-123');
    component.copyLink();

    expect(clipboardSpy).toHaveBeenCalledWith('http://rollout/track-123');
    expect(snackBarSpy.showSuccess).toHaveBeenCalledWith(
      'Link copied to clipboard',
    );
  });

  it('should not copy link if trackingUrl is empty', () => {
    const clipboardSpy = spyOn(navigator.clipboard, 'writeText');

    component.trackingUrl.set('');
    component.copyLink();

    expect(clipboardSpy).not.toHaveBeenCalled();
    expect(snackBarSpy.showSuccess).not.toHaveBeenCalled();
  });

  it('should open link in new tab when trackingUrl is non-empty', () => {
    const openSpy = spyOn(window, 'open');

    component.trackingUrl.set('http://rollout/track-123');
    component.openLink();

    expect(openSpy).toHaveBeenCalled();
    const openArgs = (window.open as jasmine.Spy).calls.mostRecent().args;
    expect(openArgs[0]).toBe('http://rollout/track-123');
    expect(openArgs[1]).toBe('_blank');
  });

  it('should not open link if trackingUrl is empty', () => {
    const openSpy = spyOn(window, 'open');

    component.trackingUrl.set('');
    component.openLink();

    expect(openSpy).not.toHaveBeenCalled();
  });
});
