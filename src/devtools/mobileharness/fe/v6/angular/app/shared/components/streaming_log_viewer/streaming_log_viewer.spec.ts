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
import {VariableHeightVirtualScrollStrategy} from './variable_height_scroll_strategy';

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

  it('should scroll continuously through multi-row wrapped lines with zero offset jump', async () => {
    // Build a 140-line log containing two heavily wrapped lines (#17 = 11k chars,
    // #60 = 5k chars) followed by a 30-frame stack trace (#61..#90) that used to
    // jump out of view when #17/#60 unmounted under `itemSize="20"`.
    const lines: string[] = [];
    for (let i = 0; i < 140; i++) {
      if (i === 17) {
        lines.push(`Line ${i}: ` + 'A'.repeat(11000));
      } else if (i === 60) {
        lines.push(`Line ${i}: ` + 'B'.repeat(5000));
      } else if (i >= 61 && i <= 90) {
        lines.push(`StackTraceFrame_${i}: at com.google.MobileHarness.run()`);
      } else {
        lines.push(`Short log line ${i}`);
      }
    }

    const wrapFixture = TestBed.createComponent(TestHostComponent);
    const hostEl = wrapFixture.nativeElement as HTMLElement;
    hostEl.style.display = 'block';
    hostEl.style.width = '960px';
    wrapFixture.componentInstance.strategy = () =>
      of({
        offset: lines.join('\n').length,
        content: lines.join('\n') + '\n',
        isDone: true,
      });

    wrapFixture.detectChanges();
    await wrapFixture.whenStable();
    wrapFixture.detectChanges();

    const viewerDebugEl = wrapFixture.debugElement.query(
      By.directive(StreamingLogViewerComponent),
    );
    const viewer =
      viewerDebugEl.componentInstance as StreamingLogViewerComponent;
    const strategy = viewerDebugEl.injector.get(
      VariableHeightVirtualScrollStrategy,
    );
    const viewport = viewer.logViewport()!;
    const viewportEl = viewport.elementRef.nativeElement;

    const fixedHeightWouldBe = lines.length * 20;
    const totalHeight = strategy.totalContentHeight();
    expect(totalHeight).toBeGreaterThan(fixedHeightWouldBe + 1500);
    expect(strategy.getHeightForIndex(17)).toBeGreaterThan(1000);
    expect(strategy.getHeightForIndex(60)).toBeGreaterThan(500);

    const seenStackFrames = new Set<number>();
    const maxScroll = Math.max(0, totalHeight - viewportEl.clientHeight);

    for (let scrollTop = 0; scrollTop <= maxScroll; scrollTop += 200) {
      viewport.scrollToOffset(scrollTop);
      // Invoke `onContentScrolled()` directly because CDK's `animationFrameScheduler`
      // runs outside Angular zone and does not tick under headless Xvfb without vsync.
      strategy.onContentScrolled();
      wrapFixture.detectChanges();
      await wrapFixture.whenStable();
      wrapFixture.detectChanges();

      // Assert the core coordinate invariant: `translateY` (`offsetToRenderedContentStart`)
      // must always equal the exact sum of heights of unmounted lines `[0 .. range.start)`.
      const range = viewport.getRenderedRange();
      let expectedOffset = 0;
      for (let idx = 0; idx < range.start; idx++) {
        expectedOffset += strategy.getHeightForIndex(idx);
      }
      expect(viewport.getOffsetToRenderedContentStart()).toBe(expectedOffset);

      const visibleBottom = scrollTop + viewportEl.clientHeight;
      for (let idx = range.start; idx < range.end; idx++) {
        const lineTop = strategy.getOffsetForIndex(idx);
        const lineBottom = lineTop + strategy.getHeightForIndex(idx);
        if (lineBottom > scrollTop && lineTop < visibleBottom) {
          if (idx >= 61 && idx <= 90) {
            seenStackFrames.add(idx);
          }
        }
      }
    }

    for (let frameIdx = 61; frameIdx <= 90; frameIdx++) {
      expect(seenStackFrames.has(frameIdx))
        .withContext(`Expected stack trace line ${frameIdx} to be reachable`)
        .toBeTrue();
    }

    // Scroll back upward and verify offsets remain continuous with zero jump.
    for (let scrollTop = maxScroll; scrollTop >= 0; scrollTop -= 250) {
      viewport.scrollToOffset(scrollTop);
      strategy.onContentScrolled();
      wrapFixture.detectChanges();
      await wrapFixture.whenStable();
      wrapFixture.detectChanges();

      // Assert the core coordinate invariant: `translateY` (`offsetToRenderedContentStart`)
      // must always equal the exact sum of heights of unmounted lines `[0 .. range.start)`.
      const range = viewport.getRenderedRange();
      expect(viewport.getOffsetToRenderedContentStart()).toBe(
        strategy.getOffsetForIndex(range.start),
      );
    }
  });

  it('should reset cached wrapped heights when a chunk has reset=true', fakeAsync(() => {
    let callCount = 0;
    const resetFixture = TestBed.createComponent(TestHostComponent);
    const hostEl = resetFixture.nativeElement as HTMLElement;
    hostEl.style.display = 'block';
    hostEl.style.width = '960px';
    resetFixture.componentInstance.strategy = () => {
      callCount++;
      if (callCount === 1) {
        return of({
          offset: 5000,
          content: 'X'.repeat(5000) + '\nSecond line\n',
          isDone: false,
        });
      }
      return of({
        offset: 6,
        content: 'Short\n',
        isDone: true,
        reset: true,
      });
    };

    resetFixture.detectChanges();
    tick();
    resetFixture.detectChanges();

    const viewerDebugEl = resetFixture.debugElement.query(
      By.directive(StreamingLogViewerComponent),
    );
    const strategy = viewerDebugEl.injector.get(
      VariableHeightVirtualScrollStrategy,
    );
    expect(strategy.totalContentHeight()).toBeGreaterThan(500);

    tick(5000);
    resetFixture.detectChanges();

    expect(strategy.totalContentHeight()).toBe(20);
    expect(viewerDebugEl.componentInstance.viewportHeight()).toBe(100);
    discardPeriodicTasks();
  }));

  it('should scroll to the true wrapped bottom when initialScrollPosition is bottom', fakeAsync(() => {
    const lines: string[] = [];
    for (let i = 0; i < 60; i++) {
      lines.push(i === 10 ? 'W'.repeat(8000) : `Line ${i}`);
    }

    const bottomFixture = TestBed.createComponent(StreamingLogViewerComponent);
    const hostEl = bottomFixture.nativeElement as HTMLElement;
    hostEl.style.display = 'block';
    hostEl.style.width = '960px';
    bottomFixture.componentRef.setInput('id', 'test_bottom');
    bottomFixture.componentRef.setInput('initialScrollPosition', 'bottom');
    bottomFixture.componentRef.setInput('fetchStrategy', () =>
      of({
        offset: lines.join('\n').length,
        content: lines.join('\n') + '\n',
        isDone: true,
      }),
    );

    bottomFixture.detectChanges();
    tick(20);
    bottomFixture.detectChanges();

    const viewer = bottomFixture.componentInstance;
    const viewport = viewer.logViewport()!;
    const viewportEl = viewport.elementRef.nativeElement;
    const strategy = bottomFixture.debugElement.injector.get(
      VariableHeightVirtualScrollStrategy,
    );
    strategy.onContentScrolled();
    tick(20);
    bottomFixture.detectChanges();

    const expectedTotalHeight = strategy.totalContentHeight();
    expect(expectedTotalHeight).toBeGreaterThan(lines.length * 20 + 800);
    expect(
      Math.abs(
        viewportEl.scrollTop + viewportEl.clientHeight - expectedTotalHeight,
      ),
    ).toBeLessThanOrEqual(2);
    expect(viewport.getRenderedRange().end).toBe(lines.length);
    discardPeriodicTasks();
  }));

  it('should handle tab expansion and upward scroll reconciliation without shifting visible line', async () => {
    const lines: string[] = [];
    for (let i = 0; i < 80; i++) {
      lines.push(
        i === 5
          ? '\t\t\tIndented\twith\ttabs\t' + 'CJK宽字符测试'.repeat(60)
          : `Row ${i}`,
      );
    }

    const cjkFixture = TestBed.createComponent(StreamingLogViewerComponent);
    const hostEl = cjkFixture.nativeElement as HTMLElement;
    hostEl.style.display = 'block';
    hostEl.style.width = '800px';
    cjkFixture.componentRef.setInput('id', 'test_cjk');
    cjkFixture.componentRef.setInput('fetchStrategy', () =>
      of({
        offset: 1000,
        content: lines.join('\n') + '\n',
        isDone: true,
      }),
    );

    cjkFixture.detectChanges();
    await cjkFixture.whenStable();
    cjkFixture.detectChanges();

    const viewer = cjkFixture.componentInstance;
    const viewport = viewer.logViewport()!;
    const strategy = cjkFixture.debugElement.injector.get(
      VariableHeightVirtualScrollStrategy,
    );

    // Verify binary-search boundaries on current prefix sums.
    const line5Offset = strategy.getOffsetForIndex(5);
    const line5Height = strategy.getHeightForIndex(5);
    const line6Offset = strategy.getOffsetForIndex(6);
    expect(line6Offset).toBe(line5Offset + line5Height);
    expect(strategy.findIndexAtOffset(0)).toBe(0);
    expect(strategy.findIndexAtOffset(line5Offset)).toBe(5);
    expect(strategy.findIndexAtOffset(line6Offset - 1)).toBe(5);
    expect(strategy.findIndexAtOffset(line6Offset)).toBe(6);

    const range = viewport.getRenderedRange();
    expect(viewport.getOffsetToRenderedContentStart()).toBe(
      strategy.getOffsetForIndex(range.start),
    );
  });
});
