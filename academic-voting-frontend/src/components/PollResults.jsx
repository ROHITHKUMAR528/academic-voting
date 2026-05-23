/** Animated vote result bars — highlights the leading option. */
export default function PollResults({ results }) {
  if (!results) return null;
  const { options, totalVotes } = results;

  // Find the max votes to determine the winner
  const maxVotes = Math.max(...options.map(o => o.voteCount), 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      {/* Summary */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0.875rem 1.125rem',
        background: 'rgba(255,255,255,0.03)',
        borderRadius: 'var(--r-md)',
        border: '1px solid var(--border)',
        fontSize: '0.9375rem',
      }}>
        <span style={{ color: 'var(--text-secondary)' }}>Total votes cast</span>
        <span style={{ fontWeight: 700, fontSize: '1.25rem' }}>{totalVotes}</span>
      </div>

      {/* Option bars */}
      {options.map((opt) => {
        const isWinner = totalVotes > 0 && opt.voteCount === maxVotes;
        return (
          <div key={opt.index} className="result-row">
            <div className="result-row-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                {isWinner && totalVotes > 0 && (
                  <span style={{ fontSize: '1rem' }}>🏆</span>
                )}
                <span style={{
                  fontWeight: isWinner ? 600 : 400,
                  color: isWinner ? 'var(--text-primary)' : 'var(--text-secondary)',
                }}>
                  {opt.label}
                </span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <span style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: '0.875rem',
                  color: isWinner ? 'var(--emerald)' : 'var(--text-secondary)',
                  fontWeight: isWinner ? 600 : 400,
                }}>
                  {opt.percentage.toFixed(1)}%
                </span>
                <span style={{ color: 'var(--text-muted)', fontSize: '0.8125rem' }}>
                  {opt.voteCount} votes
                </span>
              </div>
            </div>

            <div className="progress-track">
              <div
                className={`progress-fill ${isWinner && totalVotes > 0 ? 'winner' : ''}`}
                style={{ width: `${opt.percentage}%` }}
              />
            </div>
          </div>
        );
      })}

      {totalVotes === 0 && (
        <div style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '1rem', fontSize: '0.9375rem' }}>
          No votes have been cast yet. Be the first! 🗳️
        </div>
      )}
    </div>
  );
}
