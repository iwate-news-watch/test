import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function NavBar() {
  const { user, logout } = useAuth();

  return (
    <nav className="navbar">
      <div className="navbar__links">
        <NavLink to="/" end className={({ isActive }) => (isActive ? "navbar__link navbar__link--active" : "navbar__link")}>
          フォーカス
        </NavLink>
        <NavLink to="/tasks" className={({ isActive }) => (isActive ? "navbar__link navbar__link--active" : "navbar__link")}>
          すべて
        </NavLink>
      </div>
      <div className="navbar__account">
        <span className="navbar__email">{user?.email}</span>
        <button className="navbar__logout" onClick={logout}>
          ログアウト
        </button>
      </div>
    </nav>
  );
}
