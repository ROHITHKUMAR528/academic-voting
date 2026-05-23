import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth }         from '../context/AuthContext';
import Navbar           from '../components/Navbar';
import PollResults      from '../components/PollResults';
import CredentialModal  from '../components/CredentialModal';
import TxReceipt        from '../components/TxReceipt';
import * as api         from '../api/client';

/** Format Unix timestamp to readable date string. */
function fmtDate(ts) {
  return new Date(ts * 1000).toLocaleString(undefined, {
    dateStyle: 'medium', timeStyle: 'short',
  });
}

/** Countdown string from now to a Unix timestamp. */
function timeLeft(endTime) {
  const diff = endTime - Math.floor(Date.now() / 1000);
  if (diff <= 0) return null;
  const d = Math.floor(diff / 86400);
  const h = Math.floor((diff % 86400) / 3600);
  const m = Math.floor((diff % 3600) / 60);
  if (d > 0) return `${d}d ${h}h left`;
  if (h > 0) return `${h}h ${m}m left`;
  return `${m}m left`;
}

export default function PollPage() {
  const { user }            = useAuth();
  const { pollId }          = useParams();
  const navigate            = useNavigate();
  const [searchParams]      = useSearchParams();

  // ── State ────────────────────────────────────────────────────────────────────
  const [tab, setTab]       = useState(searchParams.get('tab') === 'results' ? 'results' : 'vote');
  const [info, setInfo]     = useState(null);
  const [results, setResults] = useState(null);
  const [loadingPage, setLoadingPage] = useState(true);
  const [error, setError]   = useState('');

  // Voting flow state machine
  const [step, setStep]     = useState('idle');
  // idle → fetching-credential → showing-credential → selecting-option → voting → done

  const [credential, setCredential]       = useState(null);  // full API response
  const [ephemeralKey, setEphemeralKey]   = useState(null);  // private key string
  const [selectedOption, setSelectedOption] = useState(null);
  const [txHash, setTxHash]               = useState(null);
  const [voteError, setVoteError]         = useState('');

  // ── Loaders ──────────────────────────────────────────────────────────────────
  const loadInfo = useCallback(async () => {
    try {
      const r = await api.getPollInfo(pollId);
      setInfo(r.data);
    } catch (e) {
      setError(e.message);
    }
  }, [pollId]);

  const loadResults = useCallback(async () => {
    try {
      const r = await api.getPollResults(pollId);
      setResults(r.data);
    } catch (e) {
      setError(e.message);
    }
  }, [pollId]);

  useEffect(() => {
    async function init() {
      setLoadingPage(true);
      await Promise.all([loadInfo(), loadResults()]);
      setLoadingPage(false);
    }
    init();
  }, [loadInfo, loadResults]);

  // Refresh results every 10s while on results tab
  useEffect(() => {
    if (tab !== 'results') return;
    const id = setInterval(loadResults, 10_000);
    return () => clearInterval(id);
  }, [tab, loadResults]);

  // ── Voting Actions ────────────────────────────────────────────────────────────
  const handleGetCredential = async () => {
    setVoteError('');
    setStep('fetching-credential');
    try {
      const res = await api.getVotingCredential(Number(pollId));
      setCredential(res);
      setStep('showing-credential');
    } catch (e) {
      setVoteError(e.message);
      setStep('idle');
    }
  };

  const handleProceedToVote = (privKey) => {
    setEphemeralKey(privKey);
    setCredential(null);
    setStep('selecting-option');
  };

  const handleCastVote = async () => {
    if (selectedOption === null) return;
    setVoteError('');
    setStep('voting');
    try {
      const res = await api.castVote(Number(pollId), ephemeralKey, selectedOption);
      setTxHash(res.txHash);
      setStep('done');
      await loadResults();
    } catch (e) {
      setVoteError(e.message);
      setStep('selecting-option');
    }
  };

  // ── Render: Loading ───────────────────────────────────────────────────────────
  if (loadingPage) return (
    <>
      <Navbar />
      <div className="page-wrapper page-content">
        <div style={{ display: 'flex', justifyContent: 'center', paddingTop: '4rem' }}>
          <div className="spinner spinner-lg" />
        </div>
      </div>
    </>
  );

  // ── Render: Error ─────────────────────────────────────────────────────────────
  if (error && !info) return (
    <>
      <Navbar />
      <div className="page-wrapper page-content">
        <div className="alert alert-danger">
          <span>⚠️</span> {error}
        </div>
        <button className="btn btn-ghost mt-2" onClick={() => navigate('/')}>← Back</button>
      </div>
    </>
  );

  const isActive  = info?.active && timeLeft(info?.endTime) !== null;
  const remaining = info ? timeLeft(info.endTime) : null;

  return (
    <>
      <Navbar />

      {/* Modals */}
      {step === 'showing-credential' && credential && (
        <CredentialModal
          credential={credential}
          onProceed={handleProceedToVote}
          onClose={() => setStep('idle')}
        />
      )}
      {step === 'done' && txHash && (
        <TxReceipt
          txHash={txHash}
          pollId={pollId}
          onClose={() => { setStep('idle'); setTab('results'); }}
        />
      )}

      <div className="page-wrapper page-content">

        {/* Back */}
        <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem',
          color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '1.5rem' }}>
          ← Dashboard
        </Link>

        {/* Poll Header */}
        {info && (
          <div className="poll-header">
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
                  <span className={`badge ${isActive ? 'badge-active badge-dot' : 'badge-ended'}`}>
                    {isActive ? 'Active' : 'Ended'}
                  </span>
                  <span style={{ fontSize: '0.8125rem', fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>
                    Poll #{pollId}
                  </span>
                  {remaining && (
                    <span style={{ fontSize: '0.8125rem', color: 'var(--amber)' }}>
                      ⏱ {remaining}
                    </span>
                  )}
                </div>
                <h1 className="text-2xl font-700" style={{ lineHeight: 1.35 }}>{info.question}</h1>
              </div>
            </div>

            {/* Meta pills */}
            <div style={{ display: 'flex', gap: '1.5rem', marginTop: '1rem', fontSize: '0.8125rem', color: 'var(--text-secondary)', flexWrap: 'wrap' }}>
              <span>📅 Opens: {fmtDate(info.startTime)}</span>
              <span>🔒 Closes: {fmtDate(info.endTime)}</span>
              <span>🗳 {info.totalVotes} votes cast</span>
              <span>📋 {info.optionCount} options</span>
            </div>
          </div>
        )}

        {/* Tabs */}
        <div className="tabs mb-3" style={{ maxWidth: 360 }}>
          <button className={`tab ${tab === 'vote' ? 'active' : ''}`} onClick={() => setTab('vote')}>
            🗳 Vote
          </button>
          <button className={`tab ${tab === 'results' ? 'active' : ''}`} onClick={() => setTab('results')}>
            📊 Results
          </button>
        </div>

        {/* ── VOTE TAB ────────────────────────────────────────────────────── */}
        {tab === 'vote' && (
          <div style={{ maxWidth: 640 }}>

            {/* Poll ended */}
            {!isActive && (
              <div className="alert alert-info mb-3">
                <span>🔒</span>
                <span>This poll has ended. You can still view the final results.</span>
              </div>
            )}

            {/* Step: idle / get credential */}
            {isActive && step === 'idle' && (
              <div className="card">
                <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
                  {user?.role === 'ADMIN' ? (
                    <div>
                      <h2 className="text-lg font-600" style={{ marginBottom: '0.5rem' }}>
                        Voting Restricted
                      </h2>
                      <p style={{ color: 'var(--text-secondary)', fontSize: '0.9375rem' }}>
                        You are logged in as an administrator. Administrators are not permitted to vote in elections.
                      </p>
                      <div className="alert alert-warning" style={{ fontSize: '0.875rem', marginTop: '1rem' }}>
                        <span>⚠️</span>
                        <span>Only users with the STUDENT role are permitted to obtain voting credentials and cast votes.</span>
                      </div>
                    </div>
                  ) : (
                    <>
                      <div>
                        <h2 className="text-lg font-600" style={{ marginBottom: '0.5rem' }}>
                          Ready to vote?
                        </h2>
                        <p style={{ color: 'var(--text-secondary)', fontSize: '0.9375rem' }}>
                          Your vote is completely anonymous. We'll generate a one-time ephemeral wallet
                          for you — your university identity will never appear on-chain.
                        </p>
                      </div>
                      <div className="alert alert-info" style={{ fontSize: '0.875rem' }}>
                        <span>🔐</span>
                        <span>Step 1 of 2: Get your anonymous voting credential → Step 2: Select your option and cast your vote.</span>
                      </div>
                      {voteError && (
                        <div className="alert alert-danger" style={{ fontSize: '0.875rem' }}>
                          <span>⚠️</span> {voteError}
                        </div>
                      )}
                      <button className="btn btn-primary btn-lg" onClick={handleGetCredential}>
                        🔑 Get Voting Credential
                      </button>
                    </>
                  )}
                </div>
              </div>
            )}

            {/* Step: fetching credential */}
            {step === 'fetching-credential' && (
              <div className="card">
                <div className="card-body" style={{ textAlign: 'center', padding: '3rem 2rem' }}>
                  <div className="spinner spinner-lg" style={{ margin: '0 auto 1.5rem' }} />
                  <p className="text-lg font-500">Generating ephemeral wallet…</p>
                  <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginTop: '0.5rem' }}>
                    Creating keypair, committing to blockchain…
                  </p>
                </div>
              </div>
            )}

            {/* Step: select option */}
            {isActive && (step === 'selecting-option' || step === 'voting') && (
              <div className="card">
                <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
                  <div>
                    <div className="alert alert-success" style={{ fontSize: '0.875rem', marginBottom: '1.25rem' }}>
                      <span>✅</span>
                      <span>Credential ready! Your anonymous wallet is whitelisted. Now select your choice.</span>
                    </div>
                    <h2 className="text-lg font-600">{info?.question}</h2>
                  </div>

                  {/* Option cards */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    {results?.options?.map((opt) => (
                      <button
                        key={opt.index}
                        className={`option-card ${selectedOption === opt.index ? 'selected' : ''}`}
                        onClick={() => setSelectedOption(opt.index)}
                        disabled={step === 'voting'}
                      >
                        <div className="option-radio" />
                        <span style={{ fontWeight: selectedOption === opt.index ? 600 : 400, fontSize: '1rem' }}>
                          {opt.label}
                        </span>
                      </button>
                    ))}
                  </div>

                  {voteError && (
                    <div className="alert alert-danger" style={{ fontSize: '0.875rem' }}>
                      <span>⚠️</span> {voteError}
                    </div>
                  )}

                  <button
                    className="btn btn-cyan btn-lg"
                    onClick={handleCastVote}
                    disabled={selectedOption === null || step === 'voting'}
                  >
                    {step === 'voting'
                      ? <><span className="spinner" /> Submitting to blockchain…</>
                      : '🗳 Cast My Vote'}
                  </button>

                  <p style={{ fontSize: '0.775rem', color: 'var(--text-muted)', textAlign: 'center' }}>
                    Once submitted, your vote is permanent and cannot be changed.
                  </p>
                </div>
              </div>
            )}

            {/* Step: done (fallback if modal dismissed) */}
            {step === 'done' && !txHash && (
              <div className="alert alert-success">
                <span>✅</span>
                <span>Your vote was successfully cast! View the Results tab.</span>
              </div>
            )}

          </div>
        )}

        {/* ── RESULTS TAB ─────────────────────────────────────────────────── */}
        {tab === 'results' && (
          <div style={{ maxWidth: 640 }}>
            <div className="card">
              <div className="card-body">
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
                  <h2 className="text-lg font-600">Live Results</h2>
                  {isActive && (
                    <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                      ↻ auto-refreshes every 10s
                    </span>
                  )}
                </div>
                {results
                  ? <PollResults results={results} />
                  : <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                      <div className="spinner spinner-lg" style={{ margin: '0 auto' }} />
                    </div>
                }
              </div>
            </div>
          </div>
        )}

      </div>
    </>
  );
}
