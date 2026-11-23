import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {MatDialog} from '@angular/material/dialog';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {UrlService} from '@deviceinfra/app/core/services/url_service';

import {EmbeddedBackdropService} from './embedded_backdrop_service';

@Component({
  selector: 'test-dialog-content',
  standalone: true,
  template: 'Dialog content',
})
class TestDialogContent {}

describe('EmbeddedBackdropService', () => {
  let urlService: jasmine.SpyObj<UrlService>;
  let dialog: MatDialog;
  let service: EmbeddedBackdropService;

  beforeEach(() => {
    urlService = jasmine.createSpyObj<UrlService>('UrlService', [
      'notifyEmbeddedBackdropChanged',
    ]);
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule],
      providers: [{provide: UrlService, useValue: urlService}],
    });
    dialog = TestBed.inject(MatDialog);
    service = TestBed.inject(EmbeddedBackdropService);
  });

  afterEach(() => {
    dialog.closeAll();
  });

  it('tells the host when a dialog opens and when it starts closing', () => {
    service.start();

    const dialogRef = dialog.open(TestDialogContent);
    expect(urlService.notifyEmbeddedBackdropChanged.calls.allArgs()).toEqual([
      [true],
    ]);

    dialogRef.close();
    expect(urlService.notifyEmbeddedBackdropChanged.calls.allArgs()).toEqual([
      [true],
      [false],
    ]);
  });

  it('reports stacked dialogs as a single backdrop', () => {
    service.start();

    const firstRef = dialog.open(TestDialogContent);
    const secondRef = dialog.open(TestDialogContent);
    secondRef.close();
    expect(urlService.notifyEmbeddedBackdropChanged.calls.allArgs()).toEqual([
      [true],
    ]);

    firstRef.close();
    expect(urlService.notifyEmbeddedBackdropChanged.calls.allArgs()).toEqual([
      [true],
      [false],
    ]);
  });

  it('starts only once', () => {
    service.start();
    service.start();

    dialog.open(TestDialogContent);

    expect(urlService.notifyEmbeddedBackdropChanged).toHaveBeenCalledOnceWith(
      true,
    );
  });

  it('does nothing until started', () => {
    dialog.open(TestDialogContent).close();

    expect(urlService.notifyEmbeddedBackdropChanged).not.toHaveBeenCalled();
  });
});
