import { useState } from "react";
import { Link } from "react-router-dom";
import { NavBar } from "../components/NavBar";
import { TaskCard } from "../components/TaskCard";
import { useTasks } from "../hooks/useTasks";
import { useToast } from "../components/Toast";
import { taskApi } from "../api";
import { randomEncouragement, sortForFocus } from "../util/task";

export function FocusPage() {
  const { tasks, loading, refresh } = useTasks();
  const { show } = useToast();
  const [limit, setLimit] = useState(3);

  const focusList = sortForFocus(tasks, limit);

  const onToggle = async (id: string, completed: boolean) => {
    await taskApi.update(id, { isCompleted: completed });
    if (completed) show(randomEncouragement());
    refresh();
  };

  return (
    <div className="page">
      <NavBar />
      <header className="page__header">
        <h1>フォーカス</h1>
        <Link className="fab" to="/tasks/new">
          + 追加
        </Link>
      </header>

      <div className="chip-row">
        <button className={`chip ${limit === 1 ? "chip--selected" : ""}`} onClick={() => setLimit(1)}>
          今やる1件だけ
        </button>
        <button className={`chip ${limit === 3 ? "chip--selected" : ""}`} onClick={() => setLimit(3)}>
          今やる3件
        </button>
      </div>

      {loading ? (
        <p className="empty-state">読み込み中…</p>
      ) : focusList.length === 0 ? (
        <p className="empty-state">今のところやることはありません。ひと息つこう。</p>
      ) : (
        <div className="task-list">
          {focusList.map((task) => (
            <TaskCard key={task.id} task={task} onToggleCompleted={(c) => onToggle(task.id, c)} />
          ))}
        </div>
      )}
    </div>
  );
}
