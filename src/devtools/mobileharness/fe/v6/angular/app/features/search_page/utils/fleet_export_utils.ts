import {Cell, Column, Row} from '../../../core/models/search';
import {
  createSafeObjectURL,
  revokeObjectURL,
  setSafeHref,
} from '../../../shared/utils/safe_dom';
import {FleetScope} from '../models';

/** Maximum number of selected items that can be exported at once. */
export const MAX_EXPORT_ROWS = 5000;

/** Returns the singular or plural display noun for the active fleet entity. */
export function getEntityNoun(entity: string, plural: boolean): string {
  if (entity === 'hosts') {
    return plural ? 'hosts' : 'host';
  }
  return plural ? 'devices' : 'device';
}

/**
 * Extracts a plain-text string from a table `Cell` regardless of its proto `oneof` kind.
 */
export function extractCellText(cell?: Cell): string {
  if (!cell) {
    return '';
  }

  if (cell.text) {
    return cell.text.value || '';
  }

  if (cell.link) {
    return cell.link.text || '';
  }

  if (cell.status) {
    return cell.status.text || '';
  }

  if (cell.chips) {
    return (cell.chips.values || []).filter(Boolean).join(', ');
  }

  if (cell.multiLink) {
    return (cell.multiLink.entries || [])
      .map((e) => e.text)
      .filter(Boolean)
      .join(', ');
  }

  return '';
}

/**
 * Formats a field value as a double-quoted CSV text cell, doubling internal quotes per RFC 4180.
 */
export function escapeCsvField(raw: string | null | undefined): string {
  const s = raw == null ? '' : String(raw);
  return `"${s.replace(/"/g, '""')}"`;
}

/** Sanitizes a field value for TSV output by replacing tabs and newlines with spaces. */
function cleanTsvField(raw: string | null | undefined): string {
  return (raw == null ? '' : String(raw)).replace(/[\t\r\n]+/g, ' ');
}

function buildDelimitedTable(
  columns: readonly Column[],
  rows: readonly Row[],
  formatField: (raw: string | null | undefined) => string,
  colSep: string,
  rowSep: string,
): string {
  const lines = new Array<string>(rows.length + 1);
  lines[0] = columns
    .map((c) => formatField(c.displayName || c.key))
    .join(colSep);
  for (let i = 0; i < rows.length; i++) {
    const cells = rows[i].cells;
    lines[i + 1] = columns
      .map((_, idx) => formatField(extractCellText(cells?.[idx])))
      .join(colSep);
  }
  return lines.join(rowSep);
}

/**
 * Builds a UTF-8 BOM-prefixed RFC 4180 CSV string (`\r\n` line endings) from columns and rows.
 */
export function buildCsvString(
  columns: readonly Column[],
  rows: readonly Row[],
): string {
  return (
    '\uFEFF' + buildDelimitedTable(columns, rows, escapeCsvField, ',', '\r\n')
  );
}

/**
 * Builds a tab-separated values (TSV) string (`\n` line endings) suitable for pasting
 * directly into Google Sheets or Excel.
 */
export function buildTsvString(
  columns: readonly Column[],
  rows: readonly Row[],
): string {
  return buildDelimitedTable(columns, rows, cleanTsvField, '\t', '\n');
}

/**
 * Generates the default CSV filename: `omnilab-<devices|hosts>-<internal|ats>-<YYYY-MM-DD>.csv`.
 */
export function buildExportFileName(
  entity: string,
  fleet: FleetScope,
  now: Date = new Date(),
): string {
  const noun = getEntityNoun(entity, true);
  const fleetSlug = fleet === 'ats' ? 'ats' : 'internal';
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  return `omnilab-${noun}-${fleetSlug}-${yyyy}-${mm}-${dd}.csv`;
}

/** Triggers a browser file download for a CSV string. */
export function triggerCsvDownload(
  csvContent: string,
  fileName: string,
  doc: Document = document,
): void {
  const blob = new Blob([csvContent], {type: 'application/octet-stream'});
  const url = createSafeObjectURL(blob);
  const link = doc.createElement('a');
  setSafeHref(link, url);
  link.download = fileName;
  link.style.display = 'none';
  doc.body.appendChild(link);
  link.click();
  doc.body.removeChild(link);
  setTimeout(() => {
    revokeObjectURL(url);
  }, 100);
}
