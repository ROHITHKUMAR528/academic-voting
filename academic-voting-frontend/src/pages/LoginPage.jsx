import { useState } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import * as api from '../api/client';

export default function LoginPage() {
  const { login, user } = useAuth();
  const navigate = useNavigate();

  const [userId,   setUserId]   = useState('');
  const [password, setPassword] = useState('');
  const [loading,  setLoading]  = useState(false);
  const [error,    setError]    = useState('');

  // Already logged in — skip to dashboard
  if (user) return <Navigate to="/" replace />;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await api.login(userId.trim(), password);
      const { token, userId: uid, name, email, role } = res.data;
      login(token, { userId: uid, name, email, role });
      navigate('/');
    } catch (err) {
      setError(err.message || 'Login failed. Check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  // Quickly fill demo credentials
  const fillDemo = (id, pass) => { setUserId(id); setPassword(pass); setError(''); };

  return (
    <div className="login-page">
      {/* Background orbs */}
      <div className="login-bg-orb login-bg-orb-1" />
      <div className="login-bg-orb login-bg-orb-2" />

      <div className="login-card">
        <div className="card">
          <div className="card-body" style={{ padding: '2.5rem 2rem' }}>

            {/* Header */}
            <div className="login-header">
              <div className="login-logo">🗳️</div>
              <h1 className="text-2xl font-700" style={{ marginBottom: '0.5rem' }}>
                <span className="text-gradient">AcademicVote</span>
              </h1>
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.9375rem' }}>
                Secure · Anonymous · Transparent
              </p>
            </div>

            {/* Form */}
            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <div className="form-group">
                <label className="form-label" htmlFor="userId">University ID</label>
                <input
                  id="userId"
                  className={`form-input ${error ? 'has-error' : ''}`}
                  type="text"
                  placeholder="e.g. student001"
                  value={userId}
                  onChange={e => setUserId(e.target.value)}
                  required
                  autoComplete="username"
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="password">Password</label>
                <input
                  id="password"
                  className={`form-input ${error ? 'has-error' : ''}`}
                  type="password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  required
                  autoComplete="current-password"
                />
              </div>

              {error && (
                <div className="alert alert-danger" style={{ fontSize: '0.875rem' }}>
                  <span>⚠️</span> {error}
                </div>
              )}

              <button
                type="submit"
                className="btn btn-primary btn-lg btn-full"
                disabled={loading}
                style={{ marginTop: '0.25rem' }}
              >
                {loading ? <><span className="spinner" /> Signing in…</> : 'Sign In →'}
              </button>
            </form>

            <div className="divider" />

            {/* Demo credentials */}
            <div>
              <p style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', marginBottom: '0.75rem', textAlign: 'center' }}>
                Quick-fill demo accounts
              </p>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
                {[
                  { id: 'student001', pass: 'password123', label: '🎓 Alice' },
                  { id: 'student002', pass: 'password456', label: '🎓 Bob' },
                  { id: 'student003', pass: 'password789', label: '🎓 Carol' },
                  { id: 'admin001',   pass: 'admin123',    label: '👑 Admin' },
                ].map(({ id, pass, label }) => (
                  <button
                    key={id}
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => fillDemo(id, pass)}
                    style={{ fontSize: '0.8125rem', justifyContent: 'flex-start' }}
                  >
                    {label}
                    <span style={{ color: 'var(--text-muted)', marginLeft: 'auto', fontFamily: 'var(--font-mono)', fontSize: '0.7rem' }}>
                      {id}
                    </span>
                  </button>
                ))}
              </div>
            </div>

          </div>
        </div>

        {/* Footer note */}
        <p style={{ textAlign: 'center', marginTop: '1.25rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
          Powered by Ethereum · Identity blinded by ephemeral wallets
        </p>
      </div>
    </div>
  );
}
