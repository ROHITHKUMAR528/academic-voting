import { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser]   = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);

  // Rehydrate from localStorage on mount
  useEffect(() => {
    try {
      const savedToken = localStorage.getItem('av_token');
      const savedUser  = localStorage.getItem('av_user');
      if (savedToken && savedUser) {
        setToken(savedToken);
        setUser(JSON.parse(savedUser));
      }
    } catch {
      localStorage.removeItem('av_token');
      localStorage.removeItem('av_user');
    } finally {
      setLoading(false);
    }
  }, []);

  const login = (tokenValue, userInfo) => {
    setToken(tokenValue);
    setUser(userInfo);
    localStorage.setItem('av_token', tokenValue);
    localStorage.setItem('av_user', JSON.stringify(userInfo));
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('av_token');
    localStorage.removeItem('av_user');
  };

  return (
    <AuthContext.Provider value={{ user, token, login, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
