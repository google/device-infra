import {ChangeDetectionStrategy, Component} from '@angular/core';
import {
  ComponentFixture,
  TestBed,
  discardPeriodicTasks,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {of} from 'rxjs';

import {
  LogFetchStrategy,
  StreamingLogViewerComponent,
} from './streaming_log_viewer';

@Component({
  standalone: true,
  imports: [StreamingLogViewerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-streaming-log-viewer
    [id]="id"
    [logTitle]="title"
    [fetchStrategy]="strategy"
  ></app-streaming-log-viewer>`,
})
class TestHostComponent {
  id = 'entity_123';
  title = 'Test Log Viewer';
  strategy: LogFetchStrategy = () =>
    of({
      offset: 10,
      content: 'Log Line 1\nLog Line 2',
      isDone: true,
    });
}

describe('StreamingLogViewerComponent', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let component: StreamingLogViewerComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        StreamingLogViewerComponent,
        TestHostComponent,
        NoopAnimationsModule,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    fixture.detectChanges();
    component = fixture.debugElement.query(
      By.directive(StreamingLogViewerComponent),
    ).componentInstance;
  });

  it('should be created and render log lines', () => {
    expect(component).toBeTruthy();
    expect(component.logLines()).toEqual(['Log Line 1', 'Log Line 2']);
  });

  it('should support polling and chunk concatenation', fakeAsync(() => {
    let calls = 0;
    const pollingStrategy: LogFetchStrategy = () => {
      calls++;
      if (calls === 1) {
        return of({
          offset: 10,
          content: 'Chunk 1\n',
          isDone: false,
        });
      }
      return of({
        offset: 20,
        content: 'Chunk 2\n',
        isDone: true,
      });
    };

    const newFixture = TestBed.createComponent(TestHostComponent);
    newFixture.componentInstance.strategy = pollingStrategy;
    newFixture.detectChanges();
    const newComponent = newFixture.debugElement.query(
      By.directive(StreamingLogViewerComponent),
    ).componentInstance;

    expect(newComponent.logLines()).toEqual(['Chunk 1']);

    tick(5000);

    expect(newComponent.logLines()).toEqual(['Chunk 1', 'Chunk 2']);
    discardPeriodicTasks();
  }));
});
