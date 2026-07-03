import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { NavBar } from "../components/NavBar";
import { TaskCard } from "../components/TaskCard";
import { useTasks } from "../hooks/useTasks";
import { useToast } from "../components/Toast";
import { taskApi } from "../api";
import type { TaskFilter } from "../types";
import { groupByPriority, priorityLabel, randomEncouragement, sortAllTasks, uniqueTags } from "../util/task";

export function ListPage() {
  const { tasks, loading, refresh } = useTasks();
  const { show } = useToast();
  const [filter, setFilter] = useState<TaskFilter>("ALL");
  const [tagFilter, setTagFilter] = useState<string | null>(null);
  const [groupedByPriority, setGroupedByPriority] = useState(false);
  const [quickAddText, setQuickAddText] = useState("");

  const filtered = tasks.filter((t) => {
    if (filter === "ACTIVE" && t.isCompleted) return false;
    if (filter === "COMPLETED" && !t.isCompleted) return false;
    if (tagFilter && t.tag !== tagFilter) return false;
    return true;
  });
  const sorted = sortAllTasks(filtered);
  const tags = uniqueTags(tasks);

  const onToggle = async (id: string, completed: boolean) => {
    await taskApi.update(id, { isCompleted: completed });
    if (completed) show(randomEncouragement());
    refresh();
  };

  const onDelete = async (id: string) => {
    await taskApi.remove(id);
    refresh();
  };

  const onQuickAdd = async (e: FormEvent) => {
    e.preventDefault();
    if (!quickAddText.trim()) return;
    await taskApi.create({ title: quickAddText.trim() });
    setQuickAddText("");
    refresh();
  };

  const renderTask = (task: (typeof sorted)[number]) => (
    <div key={task.id} className="task-list__row">
      <TaskCard task={task} onToggleCompleted={(c) => onToggle(task.id, c)} />
      <button className="task-list__delete" onClick={() => onDelete(task.id)} aria-label="削除">
        削除
      </button>
    </div>
  );

  return (
    <div className="page">
      <NavBar />
      <header className="page__header">
        <h1>すべてのタスク</h1>
        <Link className="fab" to="/tasks/new">
          + 詳細入力
        </Link>
      </header>

      <form className="quick-add" onSubmit={onQuickAdd}>
        <input
          type="text"
          value={quickAddText}
          onChange={(e) => setQuickAddText(e.target.value)}
          placeholder="思いついたらすぐ入力…"
        />
        <button type="submit">追加</button>
      </form>

      <div className="chip-row">
        {(["ALL", "ACTIVE", "COMPLETED"] as TaskFilter[]).map((f) => (
          <button key={f} className={`chip ${filter === f ? "chip--selected" : ""}`} onClick={() => setFilter(f)}>
            {f === "ALL" ? "すべて" : f === "ACTIVE" ? "未完了" : "完了済み"}
          </button>
        ))}
      </div>

      {tags.length > 0 && (
        <div className="chip-row">
          <button
            className={`chip ${tagFilter === null ? "chip--selected" : ""}`}
            onClick={() => setTagFilter(null)}
          >
            すべてのタグ
          </button>
          {tags.map((t) => (
            <button
              key={t}
              className={`chip ${tagFilter === t ? "chip--selected" : ""}`}
              onClick={() => setTagFilter(t)}
            >
              {t}
            </button>
          ))}
        </div>
      )}

      <div className="chip-row">
        <button
          className={`chip ${!groupedByPriority ? "chip--selected" : ""}`}
          onClick={() => setGroupedByPriority(false)}
        >
          通常表示
        </button>
        <button
          className={`chip ${groupedByPriority ? "chip--selected" : ""}`}
          onClick={() => setGroupedByPriority(true)}
        >
          優先度別に表示
        </button>
      </div>

      {loading ? (
        <p className="empty-state">読み込み中…</p>
      ) : sorted.length === 0 ? (
        <p className="empty-state">タスクはまだありません。上の欄から追加できます。</p>
      ) : groupedByPriority ? (
        <div className="priority-groups">
          {groupByPriority(sorted).map((group) => (
            <div key={group.priority}>
              <h2 className="priority-group-heading">
                優先度: {priorityLabel(group.priority)} ({group.tasks.length})
              </h2>
              <div className="task-list">{group.tasks.map(renderTask)}</div>
            </div>
          ))}
        </div>
      ) : (
        <div className="task-list">{sorted.map(renderTask)}</div>
      )}
    </div>
  );
}
