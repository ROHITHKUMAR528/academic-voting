import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const initials = user?.name
    ? user.name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()
    : '??';

  return (
    <nav className="navbar">
      <div className="navbar-inner">
        {/* Logo */}
        <Link to="/" className="nav-logo">
          <div className="nav-logo-icon">🗳️</div>
          <span>
            <span className="text-gradient">Academic</span>
            <span style={{ color: 'var(--text-secondary)', fontWeight: 400 }}>Vote</span>
          </span>
        </Link>

        {/* Right side */}
        <div className="nav-right">
          {user && (
            <>
              {/* User pill */}
              <div className="nav-user">
                <div className="nav-avatar">{initials}</div>
                <div>
                  <div style={{ fontWeight: 600, fontSize: '0.875rem', lineHeight: 1.2 }}>
                    {user.name}
                  </div>
                  <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', lineHeight: 1 }}>
                    {user.userId}
                  </div>
                </div>
                <span className={`badge ${user.role === 'ADMIN' ? 'badge-admin' : 'badge-student'}`}
                  style={{ marginLeft: '0.25rem' }}>
                  {user.role}
                </span>
              </div>

              <button className="btn btn-ghost btn-sm" onClick={handleLogout}>
                Sign out
              </button>
            </>
          )}
        </div>
      </div>
    </nav>
  );
}
