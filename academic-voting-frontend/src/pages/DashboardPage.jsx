import { useEffect, useState, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import Navbar   from '../components/Navbar';
import PollCard from '../components/PollCard';
import * as api from '../api/client';

export default function DashboardPage() {
  const { user } = useAuth();
  const [polls,   setPolls]   = useState([]);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState('');

  // Admin Poll Creation State
  const [showCreateModal, setShowCreateModal]   = useState(false);
  const [newQuestion, setNewQuestion]           = useState('');
  const [newOptions, setNewOptions]             = useState(['', '']);
  const [newDurationValue, setNewDurationValue] = useState(24);
  const [newDurationUnit, setNewDurationUnit]   = useState('hours');
  const [submittingPoll, setSubmittingPoll]     = useState(false);
  const [createError, setCreateError]           = useState('');

  const fetchPolls = useCallback(async () => {
    try {
      setLoading(true);
      setError('');
      const countRes = await api.getPollCount();
      const total = countRes.data?.totalPolls ?? 0;

      if (total === 0) {
        setPolls([]);
        return;
      }

      // Fetch each poll info in parallel
      const infos = await Promise.all(
        Array.from({ length: total }, (_, i) =>
          api.getPollInfo(i).then(r => ({ ...r.data, pollId: i })).catch(() => null)
        )
      );
      setPolls(infos.filter(Boolean));
    } catch (err) {
      setError(err.message || 'Failed to load polls');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPolls();
  }, [fetchPolls]);

  // -- Admin Option Handlers --
  const handleAddOption = () => {
    setNewOptions([...newOptions, '']);
  };

  const handleRemoveOption = (index) => {
    if (newOptions.length <= 2) return; // Keep at least 2 options
    setNewOptions(newOptions.filter((_, i) => i !== index));
  };

  const handleOptionChange = (index, value) => {
    const updated = [...newOptions];
    updated[index] = value;
    setNewOptions(updated);
  };

  const handleCreatePoll = async (e) => {
    e.preventDefault();
    setCreateError('');
    setSubmittingPoll(true);

    try {
      const filteredOptions = newOptions.map(opt => opt.trim()).filter(Boolean);
      if (filteredOptions.length < 2) {
        throw new Error('You must provide at least 2 valid non-empty options.');
      }

      const val = Number(newDurationValue);
      if (isNaN(val) || val <= 0) {
        throw new Error('Please enter a valid duration.');
      }

      // Convert unit to seconds
      const factor = newDurationUnit === 'days' ? 86400 : 3600;
      const durationSeconds = val * factor;

      // Deploy the poll
      await api.createPoll(newQuestion.trim(), filteredOptions, durationSeconds);

      // Success cleanup
      setNewQuestion('');
      setNewOptions(['', '']);
      setNewDurationValue(24);
      setNewDurationUnit('hours');
      setShowCreateModal(false);

      // Refresh polls
      await fetchPolls();
    } catch (err) {
      setCreateError(err.message || 'Failed to create poll. Check backend logs.');
    } finally {
      setSubmittingPoll(false);
    }
  };

  const activePolls = polls.filter(p => p.active);
  const endedPolls  = polls.filter(p => !p.active);
  const totalVotes  = polls.reduce((acc, p) => acc + (p.totalVotes || 0), 0);

  const greetingHour = new Date().getHours();
  const greeting = greetingHour < 12 ? 'Good morning' : greetingHour < 18 ? 'Good afternoon' : 'Good evening';

  return (
    <>
      <Navbar />
      <div className="page-wrapper page-content">

        {/* Hero */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2.5rem', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h1 className="text-3xl font-700" style={{ lineHeight: 1.2 }}>
              {greeting}, <span className="text-gradient">{user?.name?.split(' ')[0]}</span> 👋
            </h1>
            <p style={{ color: 'var(--text-secondary)', marginTop: '0.5rem', fontSize: '1.0625rem' }}>
              {user?.role === 'ADMIN'
                ? 'Create and deploy secure, anonymous blockchain polls.'
                : 'Cast your vote anonymously and transparently on the blockchain.'}
            </p>
          </div>
          {user?.role === 'ADMIN' && (
            <button className="btn btn-primary btn-lg" onClick={() => setShowCreateModal(true)}>
              ➕ Create New Poll
            </button>
          )}
        </div>

        {/* Stats */}
        <div className="stats-row">
          <div className="stat-card">
            <div className="stat-icon indigo">📋</div>
            <div>
              <div className="stat-value">{polls.length}</div>
              <div className="stat-label">Total Polls</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon cyan">🟢</div>
            <div>
              <div className="stat-value">{activePolls.length}</div>
              <div className="stat-label">Active Now</div>
            </div>
          </div>
          <div className="stat-card">
            <div className="stat-icon emerald">🗳</div>
            <div>
              <div className="stat-value">{totalVotes}</div>
              <div className="stat-label">Total Votes Cast</div>
            </div>
          </div>
        </div>

        {/* Error */}
        {error && (
          <div className="alert alert-danger mb-3">
            <span>⚠️</span>
            <div>
              <strong>Could not connect to backend.</strong> {error}
              <br />
              <span style={{ fontSize: '0.8rem', opacity: 0.8 }}>
                Make sure the Spring Boot server is running on port 8080.
              </span>
            </div>
          </div>
        )}

        {/* Loading skeleton */}
        {loading && (
          <div className="grid-auto">
            {[1, 2, 3].map(i => (
              <div key={i} className="card">
                <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                  <div className="skeleton" style={{ height: 22, width: '40%' }} />
                  <div className="skeleton" style={{ height: 18, width: '90%' }} />
                  <div className="skeleton" style={{ height: 18, width: '70%' }} />
                  <div className="skeleton" style={{ height: 40, marginTop: '0.5rem' }} />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Active Polls */}
        {!loading && activePolls.length > 0 && (
          <section style={{ marginBottom: '2.5rem' }}>
            <h2 className="text-xl font-600" style={{ marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span className="badge badge-active badge-dot">Active</span> Polls
            </h2>
            <div className="grid-auto">
              {activePolls.map(p => <PollCard key={p.pollId} poll={p} />)}
            </div>
          </section>
        )}

        {/* Ended Polls */}
        {!loading && endedPolls.length > 0 && (
          <section>
            <h2 className="text-xl font-600" style={{ marginBottom: '1.25rem', color: 'var(--text-secondary)' }}>
              Past Polls
            </h2>
            <div className="grid-auto">
              {endedPolls.map(p => <PollCard key={p.pollId} poll={p} />)}
            </div>
          </section>
        )}

        {/* Empty */}
        {!loading && !error && polls.length === 0 && (
          <div className="empty-state">
            <div className="empty-icon">📭</div>
            <h3 style={{ fontSize: '1.125rem', fontWeight: 600, marginBottom: '0.5rem' }}>No polls yet</h3>
            <p style={{ fontSize: '0.9375rem' }}>
              {user?.role === 'ADMIN'
                ? 'Click "Create New Poll" above to create the first poll.'
                : 'An admin needs to create a poll first. Deploy the contract and run the setup script.'}
            </p>
          </div>
        )}

      </div>

      {/* Create Poll Modal */}
      {showCreateModal && (
        <div className="modal-overlay">
          <div className="modal-box" style={{ maxWidth: 500 }}>
            <div className="modal-header">
              <h2 style={{ fontSize: '1.25rem', fontWeight: 700 }}>➕ Create New Election Poll</h2>
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginTop: '0.25rem' }}>
                Deploy a new voting contract instance on the blockchain
              </p>
            </div>

            <form onSubmit={handleCreatePoll}>
              <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
                {createError && (
                  <div className="alert alert-danger" style={{ fontSize: '0.875rem' }}>
                    <span>⚠️</span> {createError}
                  </div>
                )}

                <div className="form-group">
                  <label className="form-label">Poll Question / Title</label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="e.g. Who should be elected as Dean of the University?"
                    value={newQuestion}
                    onChange={e => setNewQuestion(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>Options / Candidates</span>
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      style={{ padding: '0.2rem 0.5rem', fontSize: '0.8rem', minWidth: 'auto', background: 'var(--bg-card)' }}
                      onClick={handleAddOption}
                    >
                      ➕ Add Option
                    </button>
                  </label>
                  
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {newOptions.map((option, idx) => (
                      <div key={idx} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                        <input
                          type="text"
                          className="form-input"
                          placeholder={`Option #${idx + 1}`}
                          value={option}
                          onChange={e => handleOptionChange(idx, e.target.value)}
                          required
                        />
                        {newOptions.length > 2 && (
                          <button
                            type="button"
                            className="btn btn-ghost"
                            style={{ padding: '0.5rem', minWidth: 'auto', color: 'var(--danger)' }}
                            onClick={() => handleRemoveOption(idx)}
                          >
                            🗑️
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Poll Duration</label>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <input
                      type="number"
                      className="form-input"
                      style={{ flex: 2 }}
                      min="1"
                      value={newDurationValue}
                      onChange={e => setNewDurationValue(e.target.value)}
                      required
                    />
                    <select
                      className="form-input"
                      style={{ flex: 1, backgroundColor: 'var(--bg-card)', color: 'var(--text-primary)', border: '1px solid var(--border-color)' }}
                      value={newDurationUnit}
                      onChange={e => setNewDurationUnit(e.target.value)}
                    >
                      <option value="hours">Hours</option>
                      <option value="days">Days</option>
                    </select>
                  </div>
                </div>
              </div>

              <div className="modal-footer" style={{ marginTop: '1.25rem', display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={submittingPoll}
                >
                  {submittingPoll ? <><span className="spinner" /> Creating...</> : 'Deploy Poll'}
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => {
                    setShowCreateModal(false);
                    setCreateError('');
                  }}
                  disabled={submittingPoll}
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
