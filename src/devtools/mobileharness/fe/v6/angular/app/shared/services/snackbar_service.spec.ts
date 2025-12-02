import {TestBed} from '@angular/core/testing';
import {MatSnackBar, MatSnackBarRef} from '@angular/material/snack-bar';

import {SnackBar} from '../components/snackbar/snackbar';

import {SnackBarService} from './snackbar_service';

describe('SnackBarService', () => {
  let service: SnackBarService;
  let snackBarSpy: jasmine.SpyObj<MatSnackBar>;

  beforeEach(() => {
    const spy = jasmine.createSpyObj('MatSnackBar', ['openFromComponent']);

    TestBed.configureTestingModule({
      providers: [SnackBarService, {provide: MatSnackBar, useValue: spy}],
    });
    service = TestBed.inject(SnackBarService);
    snackBarSpy = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call MatSnackBar.openFromComponent with correct parameters for showInfo', () => {
    const message = 'Info message';
    service.showInfo(message);
    expect(snackBarSpy.openFromComponent).toHaveBeenCalledWith(SnackBar, {
      duration: 4000,
      verticalPosition: 'bottom',
      horizontalPosition: 'center',
      panelClass: ['custom-snackbar', 'snackbar-info'],
      data: {message, icon: 'info', type: 'info'},
    });
  });

  it('should call MatSnackBar.openFromComponent with correct parameters for showSuccess', () => {
    const message = 'Success message';
    service.showSuccess(message);
    expect(snackBarSpy.openFromComponent).toHaveBeenCalledWith(SnackBar, {
      duration: 4000,
      verticalPosition: 'bottom',
      horizontalPosition: 'center',
      panelClass: ['custom-snackbar', 'snackbar-success'],
      data: {message, icon: 'check_circle', type: 'success'},
    });
  });

  it('should call MatSnackBar.openFromComponent with correct parameters for showError', () => {
    const message = 'Error message';
    service.showError(message);
    expect(snackBarSpy.openFromComponent).toHaveBeenCalledWith(SnackBar, {
      duration: 4000,
      panelClass: ['custom-snackbar', 'snackbar-error'],
      verticalPosition: 'bottom',
      horizontalPosition: 'center',
      data: {message, icon: 'error', type: 'error'},
    });
  });

  it('should call MatSnackBar.openFromComponent with correct parameters for showInProgress', () => {
    const message = 'In progress message';
    service.showInProgress(message);
    expect(snackBarSpy.openFromComponent).toHaveBeenCalledWith(SnackBar, {
      verticalPosition: 'bottom',
      horizontalPosition: 'center',
      panelClass: ['custom-snackbar', 'snackbar-inprogress'],
      data: {message, icon: 'sync', type: 'inprogress'},
    });
  });

  it('should return a MatSnackBarRef from showInProgress', () => {
    const message = 'Loading...';
    const mockSnackBarRef = jasmine.createSpyObj<MatSnackBarRef<SnackBar>>([
      'dismiss',
    ]);
    snackBarSpy.openFromComponent.and.returnValue(mockSnackBarRef);

    const result = service.showInProgress(message);

    expect(result).toBe(mockSnackBarRef);
    expect(snackBarSpy.openFromComponent).toHaveBeenCalled();
  });
});
