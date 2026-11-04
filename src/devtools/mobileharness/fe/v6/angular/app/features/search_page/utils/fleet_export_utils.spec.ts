import {Column, Row, StatusCell, TextCell} from '../../../core/models/search';
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
      expect(extractCellText({link: {}})).toBe('');
      expect(extractCellText({text: {value: 'Pixel 9'}})).toBe('Pixel 9');
      expect(extractCellText({text: {} as TextCell})).toBe('');
      expect(extractCellText({status: {text: 'IDLE'}})).toBe('IDLE');
      expect(extractCellText({status: {} as StatusCell})).toBe('');
      expect(
        extractCellText({chips: {values: ['owner-a', '', 'owner-b']}}),
      ).toBe('owner-a, owner-b');
      expect(extractCellText({chips: {}})).toBe('');
      expect(
        extractCellText({
          multiLink: {entries: [{text: 'job-1'}, {text: ''}, {text: 'job-2'}]},
        }),
      ).toBe('job-1, job-2');
      expect(extractCellText({multiLink: {}})).toBe('');
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

    it('preserves leading zeros on numeric strings (e.g. 000000000008) for spreadsheets', () => {
      expect(escapeCsvField('000000000008')).toBe('="000000000008"');
      expect(escapeCsvField('000000000000')).toBe('="000000000000"');
      expect(escapeCsvField('0')).toBe('"0"');
      expect(escapeCsvField('8')).toBe('"8"');
      expect(escapeCsvField('100000000008')).toBe('"100000000008"');
      expect(escapeCsvField('00000054ac9c71e3')).toBe('"00000054ac9c71e3"');
    });
  });

  describe('buildCsvString and buildTsvString', () => {
    const columns: Column[] = [
      {key: 'field::uuid', displayName: 'Device ID'},
      {key: 'owners', displayName: 'Owners'},
      {key: 'notes', displayName: ''},
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
      {
        id: '000000000008',
        cells: [
          {link: {text: '000000000008'}},
          {chips: {values: ['charlie']}},
          {text: {value: '0'}},
        ],
      },
    ];

    it('builds UTF-8 BOM RFC 4180 CSV with quoted text cells, preserved leading zeros, and CRLF line endings', () => {
      const csv = buildCsvString(columns, rows);
      expect(csv.startsWith('\uFEFF')).toBeTrue();
      expect(csv).toBe(
        '\uFEFF"Device ID","Owners","notes"\r\n' +
          '"dev-1","alice, bob","Fast ""Pro""\tTest\nUnit"\r\n' +
          '="000000000008","charlie","0"',
      );
    });

    it('builds TSV with tabs/newlines replaced by spaces and preserved leading zeros', () => {
      const tsv = buildTsvString(columns, rows);
      expect(tsv).toBe(
        'Device ID\tOwners\tnotes\n' +
          'dev-1\talice, bob\tFast "Pro" Test Unit\n' +
          '="000000000008"\tcharlie\t0',
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
    beforeEach(() => {
      jasmine.clock().install();
    });

    afterEach(() => {
      jasmine.clock().uninstall();
    });

    it('creates a hidden anchor element with href and download filename, clicks it while attached, removes it, and revokes the object URL after 100ms', () => {
      const createUrlSpy = spyOn(URL, 'createObjectURL').and.returnValue(
        'blob:test-url',
      );
      const revokeUrlSpy = spyOn(URL, 'revokeObjectURL');
      let clickedAnchor: HTMLAnchorElement | undefined;
      let attachedDuringClick = false;
      const clickSpy = spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(
        function (this: HTMLAnchorElement) {
          clickedAnchor = this;
          attachedDuringClick = document.body.contains(this);
        },
      );

      triggerCsvDownload('\uFEFFid\r\ndev-1', 'test.csv');

      expect(createUrlSpy).toHaveBeenCalledTimes(1);
      expect(clickSpy).toHaveBeenCalledTimes(1);
      expect(clickedAnchor).toBeDefined();
      expect(clickedAnchor!.href).toBe('blob:test-url');
      expect(clickedAnchor!.download).toBe('test.csv');
      expect(clickedAnchor!.style.display).toBe('none');
      expect(attachedDuringClick).toBeTrue();
      expect(document.body.contains(clickedAnchor!)).toBeFalse();
      expect(revokeUrlSpy).not.toHaveBeenCalled();

      jasmine.clock().tick(100);

      expect(revokeUrlSpy).toHaveBeenCalledWith('blob:test-url');
    });
  });
});
