import {CommonModule} from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatTooltipModule} from '@angular/material/tooltip';

import {ClipboardService} from '../../services/clipboard_service';

/** Supported block types in the parsed markdown AST. */
export type BlockType =
  | 'heading'
  | 'code'
  | 'table'
  | 'list'
  | 'blockquote'
  | 'hr'
  | 'paragraph';

/** Represents a markdown heading (# to ######). */
export interface HeadingBlock {
  type: 'heading';
  level: number;
  text: string;
}

/** Represents a fenced code block with optional language tag. */
export interface CodeBlock {
  type: 'code';
  language: string;
  code: string;
}

/** Represents a tabular dataset rendered from markdown table syntax. */
export interface TableBlock {
  type: 'table';
  headers: string[];
  rows: string[][];
}

/** Represents an ordered or unordered list. */
export interface ListBlock {
  type: 'list';
  ordered: boolean;
  items: string[];
}

/** Represents a blockquote block. */
export interface BlockquoteBlock {
  type: 'blockquote';
  text: string;
}

/** Represents a horizontal divider line. */
export interface HrBlock {
  type: 'hr';
}

/** Represents a standard text paragraph. */
export interface ParagraphBlock {
  type: 'paragraph';
  text: string;
}

/** Union of all parsed block types. */
export type MarkdownBlock =
  | HeadingBlock
  | CodeBlock
  | TableBlock
  | ListBlock
  | BlockquoteBlock
  | HrBlock
  | ParagraphBlock;

/** Represents an inline token within text content. */
export interface InlineToken {
  type: 'text' | 'bold' | 'italic' | 'code' | 'link';
  text: string;
  url?: string;
}

/** Checks whether a URL is safe to open as a link. */
export function isSafeUrl(url: string): boolean {
  return /^(https?:\/\/|\/|mailto:)/i.test(url);
}

/**
 * Parses inline formatting (bold, italic, code, links) from a line of text.
 * Returns an array of safe typed tokens.
 */
export function parseInline(text: string): InlineToken[] {
  if (!text) {
    return [];
  }
  const tokens: InlineToken[] = [];
  const regex =
    /(?:`([^`]+)`)|(?:\*\*([^*]+)\*\*)|(?:\*([^*]+)\*)|(?:\[([^\]]+)\]\(([^()\s]+(?:\([^()\s]*\)[^()\s]*)*)\))/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      tokens.push({
        type: 'text',
        text: text.slice(lastIndex, match.index),
      });
    }

    if (match[1]) {
      tokens.push({type: 'code', text: match[1]});
    } else if (match[2]) {
      tokens.push({type: 'bold', text: match[2]});
    } else if (match[3]) {
      tokens.push({type: 'italic', text: match[3]});
    } else if (match[5]) {
      const url = match[5].trim();
      if (isSafeUrl(url)) {
        tokens.push({type: 'link', text: match[4], url});
      } else {
        tokens.push({type: 'text', text: match[0]});
      }
    }

    lastIndex = regex.lastIndex;
  }

  if (lastIndex < text.length) {
    tokens.push({
      type: 'text',
      text: text.slice(lastIndex),
    });
  }

  return tokens;
}

/** Checks whether a line is a markdown table separator row. */
function isTableSeparator(line: string): boolean {
  const trimmed = line.trim();
  return /^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$/.test(trimmed);
}

/** Parses a table row line into an array of trimmed cell strings. */
function parseTableRow(line: string): string[] {
  const trimmed = line.trim().replace(/^\|/, '').replace(/\|$/, '');
  return trimmed.split('|').map((cell) => cell.trim());
}

/** Checks whether a line starts a list item. */
function isListLine(line: string): boolean {
  return /^\s*([*+-]|\d+\.)\s+/.test(line);
}

/** Checks whether a line is a horizontal divider. */
function isHrLine(line: string): boolean {
  return /^(\-{3,}|\*{3,}|_{3,})$/.test(line.trim());
}

/** Checks whether a table starts at the given index. */
function isTableStart(lines: string[], index: number): boolean {
  return (
    index + 1 < lines.length &&
    lines[index].includes('|') &&
    isTableSeparator(lines[index + 1])
  );
}

/**
 * Parses raw markdown text into structured blocks.
 * Supports streaming responses where code blocks may be unclosed at EOF.
 */
export function parseMarkdown(content: string): MarkdownBlock[] {
  if (!content) {
    return [];
  }

  const lines = content.split(/\r?\n/);
  const blocks: MarkdownBlock[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // Blank line
    if (line.trim().length === 0) {
      i++;
      continue;
    }

    // Code Fence (``` or ~~~)
    const fenceMatch =
      line.match(/^```(\w*)\s*$/) || line.match(/^~~~(\w*)\s*$/);
    if (fenceMatch) {
      const lang = fenceMatch[1].toLowerCase() || 'text';
      const codeLines: string[] = [];
      i++;
      while (
        i < lines.length &&
        !lines[i].startsWith('```') &&
        !lines[i].startsWith('~~~')
      ) {
        codeLines.push(lines[i]);
        i++;
      }
      blocks.push({
        type: 'code',
        language: lang,
        code: codeLines.join('\n'),
      });
      i++;
      continue;
    }

    // Heading (# to ######)
    const headingMatch = line.match(/^(#{1,6})\s+(.*)$/);
    if (headingMatch) {
      blocks.push({
        type: 'heading',
        level: headingMatch[1].length,
        text: headingMatch[2].trim(),
      });
      i++;
      continue;
    }

    // Horizontal Rule
    if (isHrLine(line)) {
      blocks.push({type: 'hr'});
      i++;
      continue;
    }

    // Blockquote
    if (line.startsWith('>')) {
      const quoteLines: string[] = [];
      while (i < lines.length && lines[i].startsWith('>')) {
        quoteLines.push(lines[i].replace(/^>\s?/, ''));
        i++;
      }
      blocks.push({
        type: 'blockquote',
        text: quoteLines.join('\n'),
      });
      continue;
    }

    // Table
    if (isTableStart(lines, i)) {
      const headers = parseTableRow(line);
      i += 2; // skip header and delimiter
      const rows: string[][] = [];
      while (
        i < lines.length &&
        lines[i].includes('|') &&
        lines[i].trim().length > 0
      ) {
        rows.push(parseTableRow(lines[i]));
        i++;
      }
      blocks.push({
        type: 'table',
        headers,
        rows,
      });
      continue;
    }

    // Unordered List
    if (/^\s*[-*+]\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length) {
        const match = lines[i].match(/^\s*[-*+]\s+(.*)$/);
        if (!match) break;
        items.push(match[1].trim());
        i++;
      }
      blocks.push({
        type: 'list',
        ordered: false,
        items,
      });
      continue;
    }

    // Ordered List
    if (/^\s*\d+\.\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length) {
        const match = lines[i].match(/^\s*\d+\.\s+(.*)$/);
        if (!match) break;
        items.push(match[1].trim());
        i++;
      }
      blocks.push({
        type: 'list',
        ordered: true,
        items,
      });
      continue;
    }

    // Paragraph
    const paraLines: string[] = [];
    while (i < lines.length) {
      const current = lines[i];
      if (current.trim().length === 0) break;
      if (
        current.startsWith('```') ||
        current.startsWith('~~~') ||
        current.startsWith('#') ||
        isTableStart(lines, i) ||
        current.startsWith('>') ||
        isListLine(current) ||
        isHrLine(current)
      ) {
        break;
      }
      paraLines.push(current);
      i++;
    }
    blocks.push({
      type: 'paragraph',
      text: paraLines.join('\n'),
    });
  }

  return blocks;
}

/**
 * Lightweight, safe, and reactive Markdown viewer component.
 * Renders structured markdown tokens with Material styling, dark-theme code blocks,
 * and one-click clipboard copying.
 */
@Component({
  selector: 'app-markdown-viewer',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './markdown_viewer.ng.html',
  styleUrl: './markdown_viewer.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarkdownViewerComponent {
  private readonly clipboardService = inject(ClipboardService);

  /** Raw markdown content string to display. */
  readonly content = input<string>('');

  /** Derived structured blocks. */
  readonly blocks = computed(() => parseMarkdown(this.content()));

  /** Tracks active copied code blocks by index to show temporary feedback. */
  readonly copiedIndices = signal<ReadonlySet<number>>(new Set());

  parseInline(text: string): InlineToken[] {
    return parseInline(text);
  }

  copyCode(code: string, index: number): void {
    const success = this.clipboardService.copyToClipboard(code);
    if (success) {
      this.copiedIndices.update((indices) => {
        const next = new Set(indices);
        next.add(index);
        return next;
      });
      setTimeout(() => {
        this.copiedIndices.update((indices) => {
          const next = new Set(indices);
          next.delete(index);
          return next;
        });
      }, 2000);
    }
  }

  isCopied(index: number): boolean {
    return this.copiedIndices().has(index);
  }
}
