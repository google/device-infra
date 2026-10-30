import {Column, Row} from '../../../core/models/search';
import {
  buildCsvString,
  buildExportFileName,
  buildTsvString,
  escapeCsvField,
  extractCellText,
  getEntityNoun,
  triggerCsvDownload,
} from './fleet_export_utils';

describe('fleet_export_utils', () => {
  describe('getEntityNoun', () => {
    it('returns singular and plural nouns for devices and hosts', () => {
      expect(getEntityNoun('devices', false)).toBe('device');
      expect(getEntityNoun('devices', true)).toBe('devices');
      expect(getEntityNoun('hosts', false)).toBe('host');
      expect(getEntityNoun('hosts', true)).toBe('hosts');
    });
  });

  describe('extractCellText', () => {
    it('extracts plain text from link, text, status, chips, and multiLink cells', () => {
      expect(
        extractCellText({
          link: {text: 'dev-1', target: {device: {id: 'dev-1'}}},
        }),
      ).toBe('dev-1');
      expect(extractCellText({text: {value: 'Pixel 9'}})).toBe('Pixel 9');
      expect(extractCellText({status: {text: 'IDLE'}})).toBe('IDLE');
      expect(extractCellText({chips: {values: ['owner-a', 'owner-b']}})).toBe(
        'owner-a, owner-b',
      );
      expect(
        extractCellText({
          multiLink: {entries: [{text: 'job-1'}, {text: 'job-2'}]},
        }),
      ).toBe('job-1, job-2');
      expect(extractCellText({})).toBe('');
      expect(extractCellText(undefined)).toBe('');
    });
  });

  describe('escapeCsvField', () => {
    it('always wraps values in double quotes as text and escapes internal quotes per RFC 4180', () => {
      expect(escapeCsvField('simple')).toBe('"simple"');
      expect(escapeCsvField(null)).toBe('""');
      expect(escapeCsvField(undefined)).toBe('""');
      expect(escapeCsvField('a, b')).toBe('"a, b"');
      expect(escapeCsvField('say "hi"')).toBe('"say ""hi"""');
      expect(escapeCsvField('line1\nline2')).toBe('"line1\nline2"');
      expect(escapeCsvField('line1\r\nline2')).toBe('"line1\r\nline2"');
    });
  });

  describe('buildCsvString and buildTsvString', () => {
    const columns: Column[] = [
      {key: 'field::uuid', displayName: 'Device ID'},
      {key: 'owners', displayName: 'Owners'},
      {key: 'notes', displayName: 'Notes'},
    ];
    const rows: Row[] = [
      {
        id: 'dev-1',
        cells: [
          {link: {text: 'dev-1'}},
          {chips: {values: ['alice', 'bob']}},
          {text: {value: 'Fast "Pro"\tTest\nUnit'}},
        ],
      },
    ];

    it('builds UTF-8 BOM RFC 4180 CSV with quoted text cells and CRLF line endings', () => {
      const csv = buildCsvString(columns, rows);
      expect(csv.startsWith('\uFEFF')).toBeTrue();
      expect(csv).toBe(
        '\uFEFF"Device ID","Owners","Notes"\r\n"dev-1","alice, bob","Fast ""Pro""\tTest\nUnit"',
      );
    });

    it('builds TSV with tabs and newlines inside cells replaced by spaces', () => {
      const tsv = buildTsvString(columns, rows);
      expect(tsv).toBe(
        'Device ID\tOwners\tNotes\ndev-1\talice, bob\tFast "Pro" Test Unit',
      );
    });
  });

  describe('buildExportFileName', () => {
    it('formats filename with entity, fleet, and YYYY-MM-DD date', () => {
      const date = new Date(2026, 2, 5); // 2026-03-05
      expect(buildExportFileName('devices', 'internal', date)).toBe(
        'omnilab-devices-internal-2026-03-05.csv',
      );
      expect(buildExportFileName('hosts', 'ats', date)).toBe(
        'omnilab-hosts-ats-2026-03-05.csv',
      );
    });
  });

  describe('triggerCsvDownload', () => {
    it('creates an anchor element on the provided document, clicks it, and revokes the object URL', () => {
      const createUrlSpy = spyOn(URL, 'createObjectURL').and.returnValue(
        'blob:test-url',
      );
      const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click');

      triggerCsvDownload('\uFEFFid\r\ndev-1', 'test.csv', document);

      expect(createUrlSpy).toHaveBeenCalled();
      expect(clickSpy).toHaveBeenCalled();
    });
  });
});
