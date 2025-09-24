import {TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {MatSnackBar} from '@angular/material/snack-bar';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {Notifier} from './notifier';

describe('Notifier Service', () => {
  let service: Notifier;
  let snackBar: jasmine.SpyObj<MatSnackBar>;
  let matDialog: jasmine.SpyObj<MatDialog>;

  beforeEach(() => {
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    matDialog = jasmine.createSpyObj('MatDialog', ['open']);

    TestBed.configureTestingModule({
      imports: [
        NoopAnimationsModule,
      ],
      providers: [
        Notifier,
        {provide: MatSnackBar, useValue: snackBar},
        {provide: MatDialog, useValue: matDialog},
      ],
    });
    service = TestBed.inject(Notifier);
  });

  it('should be instantiated', () => {
    expect(service).toBeTruthy();
  });
});
