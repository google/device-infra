import {ChangeDetectionStrategy, Component} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {NoopAnimationsModule} from '@angular/platform-browser/animations';

import {ClipboardService} from '../../services/clipboard_service';
import {
  MarkdownViewerComponent,
  parseInline,
  parseMarkdown,
} from './markdown_viewer';

@Component({
  standalone: true,
  imports: [MarkdownViewerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-markdown-viewer [content]="content" />`,
})
class TestHostComponent {
  content = '';
}

describe('MarkdownViewerComponent', () => {
  describe('parseInline', () => {
    it('returns empty array for empty or falsy text', () => {
      expect(parseInline('')).toEqual([]);
    });

    it('returns single text token for plain text', () => {
      expect(parseInline('Hello world')).toEqual([
        {type: 'text', text: 'Hello world'},
      ]);
    });

    it('parses inline code', () => {
      expect(parseInline('Use `npm start` now')).toEqual([
        {type: 'text', text: 'Use '},
        {type: 'code', text: 'npm start'},
        {type: 'text', text: ' now'},
      ]);
    });

    it('parses bold text', () => {
      expect(parseInline('This is **bold** text')).toEqual([
        {type: 'text', text: 'This is '},
        {type: 'bold', text: 'bold'},
        {type: 'text', text: ' text'},
      ]);
    });

    it('parses italic text', () => {
      expect(parseInline('This is *italic* text')).toEqual([
        {type: 'text', text: 'This is '},
        {type: 'italic', text: 'italic'},
        {type: 'text', text: ' text'},
      ]);
    });

    it('parses safe links', () => {
      expect(
        parseInline('See [Google](https://google.com) for details'),
      ).toEqual([
        {type: 'text', text: 'See '},
        {type: 'link', text: 'Google', url: 'https://google.com'},
        {type: 'text', text: ' for details'},
      ]);
    });

    it('rejects unsafe links and preserves as text', () => {
      const tokens = parseInline('[Click me](javascript:alert(1))');
      expect(tokens).toEqual([
        {type: 'text', text: '[Click me](javascript:alert(1))'},
      ]);
    });

    it('parses mixed inline formatting correctly', () => {
      const tokens = parseInline(
        'Check **status**: `ok` and see [doc](/docs/info)',
      );
      expect(tokens).toEqual([
        {type: 'text', text: 'Check '},
        {type: 'bold', text: 'status'},
        {type: 'text', text: ': '},
        {type: 'code', text: 'ok'},
        {type: 'text', text: ' and see '},
        {type: 'link', text: 'doc', url: '/docs/info'},
      ]);
    });
  });

  describe('parseMarkdown', () => {
    it('returns empty array for empty string', () => {
      expect(parseMarkdown('')).toEqual([]);
    });

    it('parses headings with levels 1 to 4', () => {
      const md = '# Header 1\n## Header 2\n### Header 3\n#### Header 4';
      const blocks = parseMarkdown(md);
      expect(blocks).toEqual([
        {type: 'heading', level: 1, text: 'Header 1'},
        {type: 'heading', level: 2, text: 'Header 2'},
        {type: 'heading', level: 3, text: 'Header 3'},
        {type: 'heading', level: 4, text: 'Header 4'},
      ]);
    });

    it('terminates paragraph when followed immediately by block elements without blank line', () => {
      const md = 'Paragraph line\n# Immediate Heading\n- Immediate List';
      const blocks = parseMarkdown(md);
      expect(blocks).toEqual([
        {type: 'paragraph', text: 'Paragraph line'},
        {type: 'heading', level: 1, text: 'Immediate Heading'},
        {type: 'list', ordered: false, items: ['Immediate List']},
      ]);
    });

    it('parses fenced code blocks with language', () => {
      const md = '```typescript\nconst x = 1;\nconsole.log(x);\n```';
      const blocks = parseMarkdown(md);
      expect(blocks).toEqual([
        {
          type: 'code',
          language: 'typescript',
          code: 'const x = 1;\nconsole.log(x);',
        },
      ]);
    });

    it('handles unclosed code block during streaming', () => {
      const md = '```bash\ncurl https://example.com';
      const blocks = parseMarkdown(md);
      expect(blocks).toEqual([
        {
          type: 'code',
          language: 'bash',
          code: 'curl https://example.com',
        },
      ]);
    });

    it('parses markdown tables', () => {
      const md =
        '| Device | Status |\n| --- | --- |\n| pixel-8 | Ready |\n| galaxy-s24 | Offline |';
      const blocks = parseMarkdown(md);
      expect(blocks).toEqual([
        {
          type: 'table',
          headers: ['Device', 'Status'],
          rows: [
            ['pixel-8', 'Ready'],
            ['galaxy-s24', 'Offline'],
          ],
        },
      ]);
    });

    it('parses unordered lists', () => {
      const md = '- Item A\n- Item B\n- Item C';
      const blocks = parseMarkdown(md);
      expect(blocks).toEqual([
        {
          type: 'list',
          ordered: false,
          items: ['Item A', 'Item B', 'Item C'],
        },
      ]);
    });

    it('parses ordered lists', () => {
      const md = '1. First\n2. Second\n3. Third';
      const blocks = parseMarkdown(md);
      expect(blocks).toEqual([
        {
          type: 'list',
          ordered: true,
          items: ['First', 'Second', 'Third'],
        },
      ]);
    });

    it('parses blockquotes and horizontal rules', () => {
      const md = '> Note this requirement.\n\n---\n\nNormal paragraph.';
      const blocks = parseMarkdown(md);
      expect(blocks).toEqual([
        {type: 'blockquote', text: 'Note this requirement.'},
        {type: 'hr'},
        {type: 'paragraph', text: 'Normal paragraph.'},
      ]);
    });
  });

  describe('Component rendering and interactions', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;
    let clipboardService: jasmine.SpyObj<ClipboardService>;

    beforeEach(async () => {
      clipboardService = jasmine.createSpyObj('ClipboardService', [
        'copyToClipboard',
      ]);
      clipboardService.copyToClipboard.and.returnValue(true);

      await TestBed.configureTestingModule({
        imports: [
          MarkdownViewerComponent,
          TestHostComponent,
          NoopAnimationsModule,
        ],
        providers: [{provide: ClipboardService, useValue: clipboardService}],
      }).compileComponents();

      fixture = TestBed.createComponent(TestHostComponent);
      host = fixture.componentInstance;
    });

    it('renders heading and paragraph elements', () => {
      host.content = '## Title\nHello world';
      fixture.detectChanges();

      const h2 = fixture.debugElement.query(By.css('h2.markdown-h2'));
      const p = fixture.debugElement.query(By.css('p.markdown-p'));

      expect(h2).toBeTruthy();
      expect(h2.nativeElement.textContent.trim()).toBe('Title');
      expect(p).toBeTruthy();
      expect(p.nativeElement.textContent.trim()).toBe('Hello world');
    });

    it('renders code block with language label and copies code on button click', () => {
      jasmine.clock().install();
      try {
        host.content = '```python\nprint("Hello")\n```';
        fixture.detectChanges();

        const langTag = fixture.debugElement.query(By.css('.code-lang-tag'));
        const codeBody = fixture.debugElement.query(By.css('.code-body'));
        const copyBtn = fixture.debugElement.query(By.css('.copy-code-button'));

        expect(langTag.nativeElement.textContent.trim()).toBe('python');
        expect(codeBody.nativeElement.textContent.trim()).toBe(
          'print("Hello")',
        );

        // Initial state
        const copyLabel = copyBtn.query(By.css('.copy-label'));
        expect(copyLabel.nativeElement.textContent.trim()).toBe('Copy');

        // Click copy button
        copyBtn.nativeElement.click();
        fixture.detectChanges();

        expect(clipboardService.copyToClipboard).toHaveBeenCalledWith(
          'print("Hello")',
        );
        expect(copyLabel.nativeElement.textContent.trim()).toBe('Copied');

        // Advance clock by 2000ms
        jasmine.clock().tick(2001);
        fixture.detectChanges();

        expect(copyLabel.nativeElement.textContent.trim()).toBe('Copy');
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('renders markdown table correctly', () => {
      host.content = '| Name | Age |\n| --- | --- |\n| Alice | 30 |';
      fixture.detectChanges();

      const ths = fixture.debugElement.queryAll(By.css('.markdown-table th'));
      const tds = fixture.debugElement.queryAll(By.css('.markdown-table td'));

      expect(ths.length).toBe(2);
      expect(ths[0].nativeElement.textContent.trim()).toBe('Name');
      expect(ths[1].nativeElement.textContent.trim()).toBe('Age');

      expect(tds.length).toBe(2);
      expect(tds[0].nativeElement.textContent.trim()).toBe('Alice');
      expect(tds[1].nativeElement.textContent.trim()).toBe('30');
    });

    it('renders unordered and ordered lists', () => {
      host.content = '- Apple\n- Banana\n\n1. One\n2. Two';
      fixture.detectChanges();

      const ulItems = fixture.debugElement.queryAll(By.css('.markdown-ul li'));
      const olItems = fixture.debugElement.queryAll(By.css('.markdown-ol li'));

      expect(ulItems.length).toBe(2);
      expect(ulItems[0].nativeElement.textContent.trim()).toBe('Apple');
      expect(ulItems[1].nativeElement.textContent.trim()).toBe('Banana');

      expect(olItems.length).toBe(2);
      expect(olItems[0].nativeElement.textContent.trim()).toBe('One');
      expect(olItems[1].nativeElement.textContent.trim()).toBe('Two');
    });
  });
});
