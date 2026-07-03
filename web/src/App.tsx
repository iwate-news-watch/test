import type { ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { useReminderWatcher } from "./hooks/useReminderWatcher";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { FocusPage } from "./pages/FocusPage";
import { ListPage } from "./pages/ListPage";
import { TaskEditPage } from "./pages/TaskEditPage";

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function App() {
  const { user } = useAuth();
  useReminderWatcher(user != null);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <FocusPage />
          </RequireAuth>
        }
      />
      <Route
        path="/tasks"
        element={
          <RequireAuth>
            <ListPage />
          </RequireAuth>
        }
      />
      <Route
        path="/tasks/new"
        element={
          <RequireAuth>
            <TaskEditPage />
          </RequireAuth>
        }
      />
      <Route
        path="/tasks/:id"
        element={
          <RequireAuth>
            <TaskEditPage />
          </RequireAuth>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
