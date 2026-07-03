import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { authApi, setToken } from "../api";
import type { AuthUser } from "../types";

interface AuthContextValue {
  user: AuthUser | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function loadStoredUser(): AuthUser | null {
  const raw = localStorage.getItem("user");
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadStoredUser());

  const persist = (token: string, user: AuthUser) => {
    setToken(token);
    localStorage.setItem("user", JSON.stringify(user));
    setUser(user);
  };

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      login: async (email, password) => {
        const result = await authApi.login(email, password);
        persist(result.token, result.user);
      },
      register: async (email, password) => {
        const result = await authApi.register(email, password);
        persist(result.token, result.user);
      },
      logout: () => {
        setToken(null);
        localStorage.removeItem("user");
        setUser(null);
      }
    }),
    [user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
