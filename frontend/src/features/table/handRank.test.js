import { evaluateHandLabel } from './handRank';

describe('evaluateHandLabel', () => {
  it('detects trail', () => {
    const r = evaluateHandLabel([
      { suit: 'HEARTS', rank: 'ACE' },
      { suit: 'SPADES', rank: 'ACE' },
      { suit: 'CLUBS', rank: 'ACE' },
    ]);
    expect(r?.label).toBe('Trail');
  });

  it('detects pure sequence', () => {
    const r = evaluateHandLabel([
      { suit: 'HEARTS', rank: 'ACE' },
      { suit: 'HEARTS', rank: 'KING' },
      { suit: 'HEARTS', rank: 'QUEEN' },
    ]);
    expect(r?.label).toBe('Pure Sequence');
  });

  it('detects color', () => {
    const r = evaluateHandLabel([
      { suit: 'SPADES', rank: 'ACE' },
      { suit: 'SPADES', rank: 'NINE' },
      { suit: 'SPADES', rank: 'THREE' },
    ]);
    expect(r?.label).toBe('Color');
  });

  it('returns null for blind / incomplete', () => {
    expect(evaluateHandLabel([])).toBeNull();
    expect(evaluateHandLabel([{ suit: 'HEARTS', rank: 'ACE' }])).toBeNull();
  });
});
