import { useState } from 'react';
import { useNavigate, Navigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import * as api from '../api/client';

export default function SignupPage() {
  const { login, user } = useAuth();
  const navigate = useNavigate();

  const [userId, setUserId] = useState('');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('STUDENT');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Already logged in — skip to dashboard
  if (user) return <Navigate to="/" replace />;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      // 1. Sign up the user
      await api.signup(
        userId.trim(),
        name.trim(),
        email.trim(),
        password,
        role
      );

      // 2. Auto-login for a premium, seamless user experience
      const loginRes = await api.login(userId.trim(), password);
      const { token, userId: uid, name: uName, email: uEmail, role: uRole } = loginRes.data;
      login(token, { userId: uid, name: uName, email: uEmail, role: uRole });
      navigate('/');
    } catch (err) {
      setError(err.message || 'Signup failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      {/* Background orbs */}
      <div className="login-bg-orb login-bg-orb-1" />
      <div className="login-bg-orb login-bg-orb-2" />

      <div className="login-card" style={{ maxWidth: 460 }}>
        <div className="card">
          <div className="card-body" style={{ padding: '2.5rem 2rem' }}>

            {/* Header */}
            <div className="login-header">
              <div className="login-logo">🗳️</div>
              <h1 className="text-2xl font-700" style={{ marginBottom: '0.5rem' }}>
                <span className="text-gradient">Create Account</span>
              </h1>
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.9375rem' }}>
                Register for Academic Voting
              </p>
            </div>

            {/* Form */}
            <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.15rem' }}>
              <div className="form-group">
                <label className="form-label" htmlFor="userId">University ID</label>
                <input
                  id="userId"
                  className={`form-input ${error ? 'has-error' : ''}`}
                  type="text"
                  placeholder="e.g. student005"
                  value={userId}
                  onChange={e => setUserId(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="name">Full Name</label>
                <input
                  id="name"
                  className={`form-input ${error ? 'has-error' : ''}`}
                  type="text"
                  placeholder="e.g. John Doe"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="email">Email Address</label>
                <input
                  id="email"
                  className={`form-input ${error ? 'has-error' : ''}`}
                  type="email"
                  placeholder="e.g. john@university.edu"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="password">Password</label>
                <input
                  id="password"
                  className={`form-input ${error ? 'has-error' : ''}`}
                  type="password"
                  placeholder="Create a strong password"
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="role">Account Role</label>
                <select
                  id="role"
                  className="form-input"
                  value={role}
                  onChange={e => setRole(e.target.value)}
                  style={{
                    backgroundColor: 'var(--bg-card)',
                    color: 'var(--text-primary)',
                    border: '1px solid var(--border-color)',
                    cursor: 'pointer'
                  }}
                >
                  <option value="STUDENT">Student (Voter)</option>
                  <option value="ADMIN">Administrator (Poll Creator)</option>
                </select>
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
                {loading ? <><span className="spinner" /> Creating Account…</> : 'Sign Up →'}
              </button>
            </form>

            <p style={{ textAlign: 'center', marginTop: '1.25rem', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
              Already have an account?{' '}
              <Link to="/login" style={{ color: 'var(--primary)', fontWeight: 600, textDecoration: 'none' }} className="text-gradient">
                Sign In
              </Link>
            </p>

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
