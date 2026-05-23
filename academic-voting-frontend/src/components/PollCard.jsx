import { Link } from 'react-router-dom';

/** Formats seconds-remaining into a human-readable string. */
function timeRemaining(endTime) {
  const now  = Math.floor(Date.now() / 1000);
  const diff = endTime - now;
  if (diff <= 0) return 'Ended';
  const days  = Math.floor(diff / 86400);
  const hours = Math.floor((diff % 86400) / 3600);
  if (days > 0)  return `${days}d ${hours}h remaining`;
  const mins = Math.floor((diff % 3600) / 60);
  if (hours > 0) return `${hours}h ${mins}m remaining`;
  return `${mins}m remaining`;
}

export default function PollCard({ poll }) {
  const { pollId, question, active, totalVotes, optionCount, endTime } = poll;
  const timeStr = timeRemaining(endTime);
  const isEnded = !active || timeStr === 'Ended';

  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column' }}>
      <div className="card-body poll-card">

        {/* Header row */}
        <div className="flex items-center justify-between">
          <span className={`badge ${isEnded ? 'badge-ended' : 'badge-active badge-dot'}`}>
            {isEnded ? 'Ended' : 'Active'}
          </span>
          <span style={{
            fontSize: '0.75rem',
            color: 'var(--text-muted)',
            fontFamily: 'var(--font-mono)',
          }}>
            Poll #{pollId}
          </span>
        </div>

        {/* Question */}
        <p className="poll-question">{question}</p>

        {/* Stats row */}
        <div style={{ display: 'flex', gap: '1.5rem', fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
          <span>🗳 <strong style={{ color: 'var(--text-primary)' }}>{totalVotes}</strong> votes</span>
          <span>📋 <strong style={{ color: 'var(--text-primary)' }}>{optionCount}</strong> options</span>
        </div>

        {/* Time */}
        <div className="poll-time">
          <span>{isEnded ? '🔒' : '⏱'}</span>
          <span style={{ color: isEnded ? 'var(--text-muted)' : 'var(--amber)' }}>
            {timeStr}
          </span>
        </div>

        {/* CTA */}
        <Link to={`/polls/${pollId}`} style={{ marginTop: 'auto' }}>
          <button className={`btn w-full ${isEnded ? 'btn-ghost' : 'btn-primary'}`}>
            {isEnded ? 'View Results' : 'View Poll →'}
          </button>
        </Link>
      </div>
    </div>
  );
}
