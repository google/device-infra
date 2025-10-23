import {ComponentFixture, TestBed} from '@angular/core/testing';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {Stability} from './stability';

describe('Stability Component', () => {
  let fixture: ComponentFixture<Stability>;
  let component: Stability;
  beforeEach(async () => {
    await TestBed
        .configureTestingModule({
          imports: [
            Stability,
            NoopAnimationsModule,  // This makes test faster and more stable.
          ],
          providers: [provideRouter([])],
        })
        .compileComponents();

    fixture = TestBed.createComponent(Stability);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should be created', () => {
    expect(component).toBeTruthy();
  });
});
