import { useState } from 'react';

function CopyButton({ text, label = 'Copy' }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    await navigator.clipboard.writeText(text).catch(() => {});
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button className={`copy-btn ${copied ? 'copied' : ''}`} onClick={handleCopy}>
      {copied ? '✓ Copied!' : label}
    </button>
  );
}

/**
 * Modal shown after requesting a voting credential.
 * Displays the ephemeral private key with a security warning and copy button.
 * User must acknowledge before proceeding to vote.
 */
export default function CredentialModal({ credential, onProceed, onClose }) {
  const [confirmed, setConfirmed] = useState(false);
  const [revealed,  setRevealed]  = useState(false);

  return (
    <div className="modal-overlay">
      <div className="modal-box">
        <div className="modal-header">
          <div>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 700 }}>🔑 Your Voting Credential</h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginTop: '0.25rem' }}>
              Keep this key private — it proves your anonymous vote
            </p>
          </div>
        </div>

        <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>

          {/* Warning */}
          <div className="alert alert-warning">
            <span style={{ fontSize: '1.2rem', flexShrink: 0 }}>⚠️</span>
            <div>
              <strong>Save this private key immediately.</strong> This is the only time it will be shown.
              The server does <em>not</em> store it. Without this key, you cannot cast your vote.
            </div>
          </div>

          {/* Ephemeral Address */}
          <div>
            <p className="form-label mb-1">Ephemeral Wallet Address (on-chain)</p>
            <div className="code-block" style={{ color: 'var(--indigo-light)' }}>
              {credential.data.ephemeralAddress}
              <CopyButton text={credential.data.ephemeralAddress} label="Copy" />
            </div>
            <p style={{ fontSize: '0.775rem', color: 'var(--text-muted)', marginTop: '0.4rem' }}>
              This address is whitelisted on-chain. It is NOT linked to your identity.
            </p>
          </div>

          {/* Private Key */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
              className="mb-1">
              <p className="form-label">Ephemeral Private Key</p>
              <button
                onClick={() => setRevealed(r => !r)}
                style={{
                  background: 'none', border: 'none',
                  color: 'var(--indigo-light)', fontSize: '0.8rem',
                  cursor: 'pointer', fontFamily: 'var(--font-sans)',
                }}>
                {revealed ? '🙈 Hide' : '👁 Reveal'}
              </button>
            </div>
            <div className="code-block" style={{ position: 'relative' }}>
              <span className={revealed ? '' : 'key-blur'}>
                {credential.data.ephemeralPrivateKey}
              </span>
              {revealed && (
                <CopyButton text={credential.data.ephemeralPrivateKey} label="Copy Key" />
              )}
            </div>
            {!revealed && (
              <p style={{ fontSize: '0.775rem', color: 'var(--text-muted)', marginTop: '0.4rem', textAlign: 'center' }}>
                Click "Reveal" to show your private key
              </p>
            )}
          </div>

          {/* Whitelist TX */}
          <div>
            <p className="form-label mb-1">Whitelist Transaction</p>
            <div className="code-block" style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              {credential.data.whitelistTxHash}
              <CopyButton text={credential.data.whitelistTxHash} label="Copy TX" />
            </div>
          </div>

          {/* Confirmation */}
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={e => setConfirmed(e.target.checked)}
            />
            <span>
              I have <strong>securely saved</strong> my ephemeral private key and understand
              it cannot be recovered if lost.
            </span>
          </label>
        </div>

        <div className="modal-footer">
          <button
            className="btn btn-primary btn-lg"
            disabled={!confirmed || !revealed}
            onClick={() => onProceed(credential.data.ephemeralPrivateKey)}
          >
            Proceed to Vote →
          </button>
          {(!confirmed || !revealed) && (
            <p style={{ textAlign: 'center', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              {!revealed ? 'Reveal your key first, then' : 'Check the box above to'} proceed
            </p>
          )}
          <button className="btn btn-ghost" onClick={onClose}>Cancel</button>
        </div>
      </div>
    </div>
  );
}
