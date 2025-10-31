import {TestBed} from '@angular/core/testing';
import {MatDialogModule} from '@angular/material/dialog';
import {MatTestDialogOpener, MatTestDialogOpenerModule} from '@angular/material/dialog/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {Dialog} from './dialog';

describe('Dialog Component', () => {
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            Dialog,
            NoopAnimationsModule,  // This makes test faster and more stable.
            MatDialogModule,
            MatTestDialogOpenerModule,
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();
  });

  it('should be created', () => {
    const comp = TestBed.createComponent(
        MatTestDialogOpener.withComponent(Dialog, {
          data: {
            title: 'Test Title',
            content: 'Test Content',
          },
        }),
    );
    expect(comp).toBeTruthy();
  });
});
