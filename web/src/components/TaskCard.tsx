import { Link } from "react-router-dom";
import type { Task } from "../types";
import { formatDueDate, isOverdue } from "../util/task";

interface Props {
  task: Task;
  onToggleCompleted: (completed: boolean) => void;
}

const priorityColors: Record<Task["priority"], string> = {
  LOW: "#43a047",
  MEDIUM: "#fb8c00",
  HIGH: "#e53935"
};

export function TaskCard({ task, onToggleCompleted }: Props) {
  const overdue = isOverdue(task);

  return (
    <div className={`task-card ${overdue ? "task-card--overdue" : ""}`}>
      <input
        type="checkbox"
        checked={task.isCompleted}
        onChange={(e) => onToggleCompleted(e.target.checked)}
        className="task-card__checkbox"
        aria-label="完了にする"
      />
      <span
        className="task-card__priority-dot"
        style={{ backgroundColor: priorityColors[task.priority] }}
      />
      <Link to={`/tasks/${task.id}`} className="task-card__body">
        <div className={`task-card__title ${task.isCompleted ? "task-card__title--done" : ""}`}>
          {task.title}
        </div>
        {task.description && <div className="task-card__desc">{task.description}</div>}
        {task.tag && <span className="task-card__tag">{task.tag}</span>}
        {task.isRecurring && <span className="task-card__tag">🔁 毎日</span>}
        {task.subtaskTotal > 0 && (
          <div className="task-card__meta">
            サブタスク {task.subtaskCompleted}/{task.subtaskTotal}
          </div>
        )}
        {task.dueDate != null && (
          <div className={overdue ? "task-card__due task-card__due--overdue" : "task-card__due"}>
            {overdue ? "⚠ 期限切れ: " : "期限: "}
            {formatDueDate(task.dueDate)}
          </div>
        )}
      </Link>
    </div>
  );
}
