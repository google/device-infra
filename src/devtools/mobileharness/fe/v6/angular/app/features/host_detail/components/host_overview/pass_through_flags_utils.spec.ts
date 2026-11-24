import {
  parsePassThroughFlag,
  parsePassThroughFlags,
  splitPassThroughFlags,
} from './pass_through_flags_utils';

describe('splitPassThroughFlags', () => {
  it('returns no flags for empty, blank, null, and undefined input', () => {
    expect(splitPassThroughFlags('')).toEqual([]);
    expect(splitPassThroughFlags('  \t ')).toEqual([]);
    expect(splitPassThroughFlags(null)).toEqual([]);
    expect(splitPassThroughFlags(undefined)).toEqual([]);
  });

  it('starts a new flag at whitespace followed by --', () => {
    expect(
      splitPassThroughFlags('  --alpha=1   --enable_beta\t--gamma=x '),
    ).toEqual(['--alpha=1', '--enable_beta', '--gamma=x']);
  });

  it('keeps a space separated value with its flag', () => {
    expect(splitPassThroughFlags('--foo bar  baz --qux')).toEqual([
      '--foo bar  baz',
      '--qux',
    ]);
  });

  it('keeps quoted values together even when they contain --', () => {
    expect(
      splitPassThroughFlags(`--msg="hello --world" --opts='-a --b' --c`),
    ).toEqual([`--msg="hello --world"`, `--opts='-a --b'`, '--c']);
  });

  it('treats an unmatched quote as a literal character', () => {
    expect(splitPassThroughFlags(`--note=it's --next`)).toEqual([
      `--note=it's`,
      '--next',
    ]);
  });
});

describe('parsePassThroughFlag', () => {
  it('splits a flag at its first = or whitespace', () => {
    expect(parsePassThroughFlag('--alpha=1=2')).toEqual({
      name: '--alpha',
      value: '=1=2',
    });
    expect(parsePassThroughFlag('--foo bar')).toEqual({
      name: '--foo',
      value: ' bar',
    });
  });

  it('returns an empty value for a flag without = or whitespace', () => {
    expect(parsePassThroughFlag('--enable_gamma')).toEqual({
      name: '--enable_gamma',
      value: '',
    });
  });

  it('keeps a token that starts with = as the name', () => {
    expect(parsePassThroughFlag('=value')).toEqual({
      name: '=value',
      value: '',
    });
  });
});

describe('parsePassThroughFlags', () => {
  it('parses every flag into its name and value', () => {
    expect(
      parsePassThroughFlags('--alpha=1 --beta="hello world" --foo bar'),
    ).toEqual([
      {name: '--alpha', value: '=1'},
      {name: '--beta', value: '="hello world"'},
      {name: '--foo', value: ' bar'},
    ]);
  });
});
