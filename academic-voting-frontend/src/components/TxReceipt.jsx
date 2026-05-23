import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false);
  const handle = async () => {
    await navigator.clipboard.writeText(text).catch(() => {});
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button className={`copy-btn ${copied ? 'copied' : ''}`} onClick={handle}>
      {copied ? '✓ Copied!' : 'Copy'}
    </button>
  );
}

/** Success modal displayed after a vote is cast on-chain. */
export default function TxReceipt({ txHash, pollId, onClose }) {
  const navigate = useNavigate();

  const handleViewResults = () => {
    onClose();
    navigate(`/polls/${pollId}?tab=results`);
  };

  return (
    <div className="modal-overlay">
      <div className="modal-box" style={{ maxWidth: 500 }}>
        <div className="modal-body" style={{
          display: 'flex', flexDirection: 'column',
          alignItems: 'center', gap: '1.5rem',
          padding: '2.5rem 2rem',
          textAlign: 'center',
        }}>

          {/* Success checkmark animation */}
          <div className="success-circle">
            <svg width="36" height="36" viewBox="0 0 36 36" fill="none"
              xmlns="http://www.w3.org/2000/svg">
              <polyline
                className="success-check"
                points="7,18 15,26 29,10"
                stroke="#10b981"
                strokeWidth="3.5"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeDasharray="100"
              />
            </svg>
          </div>

          {/* Heading */}
          <div>
            <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '0.5rem' }}>
              Vote Cast! 🎉
            </h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.9375rem' }}>
              Your vote has been recorded anonymously on the blockchain.
              Your real identity is not linked to this transaction.
            </p>
          </div>

          {/* Transaction hash */}
          <div style={{ width: '100%' }}>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', marginBottom: '0.5rem', textAlign: 'left' }}>
              Transaction Hash
            </p>
            <div className="code-block" style={{ fontSize: '0.75rem', textAlign: 'left' }}>
              {txHash}
              <CopyButton text={txHash} />
            </div>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.5rem' }}>
              Use this hash to verify your vote on any EVM block explorer.
            </p>
          </div>

          {/* Privacy note */}
          <div className="alert alert-success" style={{ textAlign: 'left', width: '100%' }}>
            <span>🔒</span>
            <span>Your ephemeral wallet address was used for this transaction.
            Your university identity is protected by cryptographic commitment.</span>
          </div>
        </div>

        {/* Footer */}
        <div className="modal-footer">
          <button className="btn btn-success btn-lg" onClick={handleViewResults}>
            View Live Results 📊
          </button>
          <button className="btn btn-ghost" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}
