import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';
import {FakeHostService} from '../../core/services/host/fake_host_service';
import {HOST_SERVICE} from '../../core/services/host/host_service';
import {ClipboardService} from '../../shared/services/clipboard_service';
import {SnackBarService} from '../../shared/services/snackbar_service';

import {HostDetail} from './host_detail';

describe('HostDetail Component', () => {
  let fixture: ComponentFixture<HostDetail>;
  let component: HostDetail;
  let mockSnackBarService: jasmine.SpyObj<SnackBarService>;
  let mockClipboardService: jasmine.SpyObj<ClipboardService>;

  beforeEach(async () => {
    mockSnackBarService = jasmine.createSpyObj('SnackBarService', [
      'showSuccess',
      'showError',
      'showInProgress',
    ]);
    mockClipboardService = jasmine.createSpyObj('ClipboardService', [
      'copyToClipboard',
    ]);
    await TestBed.configureTestingModule({
      imports: [
        HostDetail,
        NoopAnimationsModule, // This makes test faster and more stable.
      ],
      providers: [
        provideRouter([]),
        {provide: HOST_SERVICE, useClass: FakeHostService},
        {provide: SnackBarService, useValue: mockSnackBarService},
        {provide: ClipboardService, useValue: mockClipboardService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HostDetail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
