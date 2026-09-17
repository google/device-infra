import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MAT_DIALOG_DATA, MatDialog} from '@angular/material/dialog';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';
import {provideRouter} from '@angular/router';

import {Turn, TurnStatus} from '../../../../core/models/assistant';
import {ClipboardService} from '../../../../shared/services/clipboard_service';
import {
  formatJsonString,
  ToolInspectionDialogComponent,
} from './tool_inspection_dialog';
import {
  formatElapsedDuration,
  parseLinkTarget,
  TurnViewComponent,
} from './turn_view';

@Component({
  standalone: true,
  imports: [TurnViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-turn-view [turn]="turn" />`,
})
class TestHostComponent {
  turn: Turn = {
    turnId: 'turn-1',
    userPrompt: 'Check Pixel 8 fleet health',
    status: TurnStatus.RUNNING,
    thoughts: 'Querying device inventory for model pixel-8...',
    responseMarkdown: 'Found **12 devices** ready.',
    toolExecutions: [
      {
        callId: 'call-1',
        toolName: 'SearchDevices',
        argumentsJson: '{"model":"pixel-8"}',
        success: true,
        resultJson: '{"count":12}',
      },
    ],
    outboundLinks: [
      {
        title: 'View 12 Pixel 8 Devices',
        url: '/devices?filter=model:pixel-8',
      },
    ],
  };
}

describe('TurnViewComponent', () => {
  describe('parseLinkTarget', () => {
    it('returns root path for empty string', () => {
      expect(parseLinkTarget('')).toEqual({
        path: '/',
        queryParams: {},
        isExternal: false,
      });
    });

    it('parses internal URL with query parameters', () => {
      const target = parseLinkTarget(
        '/devices?filter=model:pixel-8&status=ready',
      );
      expect(target).toEqual({
        path: '/devices',
        queryParams: {
          'filter': 'model:pixel-8',
          'status': 'ready',
        },
        isExternal: false,
      });
    });

    it('parses external HTTP URLs', () => {
      const target = parseLinkTarget('https://sponge2/12345');
      expect(target).toEqual({
        path: 'https://sponge2/12345',
        queryParams: {},
        isExternal: true,
      });
    });
  });

  describe('formatElapsedDuration', () => {
    it('formats elapsed time in seconds', () => {
      const start = '2026-09-09T10:00:00.000Z';
      const end = '2026-09-09T10:00:03.200Z';
      expect(formatElapsedDuration(start, end)).toBe('3.2s');
    });

    it('returns null for missing or invalid dates', () => {
      expect(formatElapsedDuration(undefined, undefined)).toBeNull();
      expect(formatElapsedDuration('invalid', 'invalid')).toBeNull();
    });
  });

  describe('formatJsonString', () => {
    it('formats valid JSON with 2-space indentation', () => {
      expect(formatJsonString(undefined)).toBe('{}');
      expect(formatJsonString('{"a":1,"b":2}')).toBe(
        '{\n  "a": 1,\n  "b": 2\n}',
      );
    });

    it('returns raw text if not valid JSON', () => {
      expect(formatJsonString('plain text error')).toBe('plain text error');
    });
  });

  describe('Component rendering and interactions', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;
    let dialogSpy: jasmine.SpyObj<MatDialog>;

    beforeEach(async () => {
      dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);

      await TestBed.configureTestingModule({
        imports: [TurnViewComponent, TestHostComponent, NoopAnimationsModule],
        providers: [
          provideRouter([]),
          {provide: MatDialog, useValue: dialogSpy},
        ],
      }).compileComponents();

      fixture = TestBed.createComponent(TestHostComponent);
      host = fixture.componentInstance;
    });

    it('renders user prompt bubble', () => {
      fixture.detectChanges();

      const promptBubble = fixture.debugElement.query(
        By.css('.user-prompt-bubble'),
      );
      expect(promptBubble).toBeTruthy();
      expect(promptBubble.nativeElement.textContent.trim()).toBe(
        'Check Pixel 8 fleet health',
      );
    });

    it('auto-expands Thought Tray while turn is RUNNING and supports manual toggle', () => {
      host.turn = {
        ...host.turn,
        status: TurnStatus.RUNNING,
      };
      fixture.detectChanges();

      const turnView = fixture.debugElement.query(
        By.directive(TurnViewComponent),
      ).componentInstance as TurnViewComponent;
      expect(turnView.isThoughtExpanded()).toBeTrue();
      expect(turnView.thoughtHeaderLabel()).toBe(
        'Thinking & executing tools...',
      );

      turnView.onThoughtToggle(false);
      expect(turnView.isThoughtExpanded()).toBeFalse();
    });

    it('auto-collapses Thought Tray when turn is COMPLETED and displays elapsed duration or default label', () => {
      host.turn = {
        ...host.turn,
        status: TurnStatus.COMPLETED,
        startTime: '2026-09-09T10:00:00.000Z',
        endTime: '2026-09-09T10:00:04.500Z',
      };
      fixture.detectChanges();

      const turnView = fixture.debugElement.query(
        By.directive(TurnViewComponent),
      ).componentInstance as TurnViewComponent;
      expect(turnView.isThoughtExpanded()).toBeFalse();
      expect(turnView.thoughtHeaderLabel()).toBe('Thought for 4.5s');

      const standaloneTurnFixture = TestBed.createComponent(TurnViewComponent);
      standaloneTurnFixture.componentRef.setInput('turn', {
        ...host.turn,
        status: TurnStatus.COMPLETED,
        startTime: undefined,
        endTime: undefined,
      });
      standaloneTurnFixture.detectChanges();
      expect(standaloneTurnFixture.componentInstance.thoughtHeaderLabel()).toBe(
        'Reasoning & Tool Trace',
      );
    });

    it('opens ToolInspectionDialogComponent when a tool chip is clicked', () => {
      fixture.detectChanges();

      const toolChip = fixture.debugElement.query(By.css('.tool-chip'));
      expect(toolChip).toBeTruthy();
      expect(toolChip.nativeElement.textContent).toContain('SearchDevices');

      toolChip.nativeElement.click();

      expect(dialogSpy.open).toHaveBeenCalledWith(
        ToolInspectionDialogComponent,
        jasmine.objectContaining({
          data: host.turn.toolExecutions![0],
        }),
      );
    });

    it('renders outbound deep-link chips', () => {
      fixture.detectChanges();

      const outboundChip = fixture.debugElement.query(
        By.css('.outbound-link-chip'),
      );
      expect(outboundChip).toBeTruthy();
      expect(outboundChip.nativeElement.textContent).toContain(
        'View 12 Pixel 8 Devices',
      );
    });
  });

  describe('ToolInspectionDialogComponent', () => {
    it('formats arguments and result and copies them to clipboard', async () => {
      const clipboardSpy = jasmine.createSpyObj('ClipboardService', [
        'copyToClipboard',
      ]);
      clipboardSpy.copyToClipboard.and.returnValue(true);

      await TestBed.configureTestingModule({
        imports: [ToolInspectionDialogComponent, NoopAnimationsModule],
        providers: [
          {
            provide: MAT_DIALOG_DATA,
            useValue: {
              callId: 'call-1',
              toolName: 'SearchDevices',
              argumentsJson: '{"model":"pixel-8"}',
              success: true,
              resultJson: '{"count":12}',
            },
          },
          {provide: ClipboardService, useValue: clipboardSpy},
        ],
      }).compileComponents();

      const dialogFixture = TestBed.createComponent(
        ToolInspectionDialogComponent,
      );
      const comp = dialogFixture.componentInstance;
      dialogFixture.detectChanges();

      expect(comp.formattedArguments()).toContain('"model": "pixel-8"');
      expect(comp.formattedResult()).toContain('"count": 12');

      comp.copyArguments();
      expect(comp.copiedArgs()).toBeTrue();

      comp.copyResult();
      expect(comp.copiedResult()).toBeTrue();
    });

    it('displays errorMessage when resultJson is empty', async () => {
      const clipboardSpy = jasmine.createSpyObj('ClipboardService', [
        'copyToClipboard',
      ]);
      clipboardSpy.copyToClipboard.and.returnValue(true);

      await TestBed.configureTestingModule({
        imports: [ToolInspectionDialogComponent, NoopAnimationsModule],
        providers: [
          {
            provide: MAT_DIALOG_DATA,
            useValue: {
              callId: 'call-err',
              toolName: 'SearchDevices',
              argumentsJson: '{}',
              success: false,
              errorMessage: 'RPC failed',
            },
          },
          {provide: ClipboardService, useValue: clipboardSpy},
        ],
      }).compileComponents();

      const dialogFixture = TestBed.createComponent(
        ToolInspectionDialogComponent,
      );
      const comp = dialogFixture.componentInstance;
      dialogFixture.detectChanges();

      expect(comp.formattedResult()).toBe('RPC failed');
    });
  });
});
