import { useMemo, useState } from "react";
import type { Task } from "../types";
import { addMonths, buildMonthGrid, dateKey, groupTasksByDate, monthLabel } from "../util/calendar";
import { TaskCard } from "./TaskCard";

interface Props {
  tasks: Task[];
  onToggleCompleted: (id: string, completed: boolean) => void;
  onDelete: (id: string) => void;
}

const WEEKDAY_LABELS = ["日", "月", "火", "水", "木", "金", "土"];

export function CalendarView({ tasks, onToggleCompleted, onDelete }: Props) {
  const [month, setMonth] = useState(() => new Date());
  const [selectedKey, setSelectedKey] = useState<string | null>(null);

  const grid = useMemo(() => buildMonthGrid(month), [month]);
  const tasksByDate = useMemo(() => groupTasksByDate(tasks), [tasks]);
  const todayKey = dateKey(new Date());

  const selectedTasks = selectedKey ? tasksByDate.get(selectedKey) ?? [] : [];

  return (
    <div className="calendar">
      <div className="calendar__header">
        <button type="button" onClick={() => setMonth((m) => addMonths(m, -1))} aria-label="前の月">
          ‹
        </button>
        <span className="calendar__month-label">{monthLabel(month)}</span>
        <button type="button" onClick={() => setMonth((m) => addMonths(m, 1))} aria-label="次の月">
          ›
        </button>
      </div>

      <div className="calendar__weekdays">
        {WEEKDAY_LABELS.map((label) => (
          <div key={label} className="calendar__weekday">
            {label}
          </div>
        ))}
      </div>

      <div className="calendar__grid">
        {grid.map((cell, i) => {
          if (!cell.date) return <div key={i} className="calendar__cell calendar__cell--empty" />;
          const key = dateKey(cell.date);
          const dayTasks = tasksByDate.get(key) ?? [];
          const isToday = key === todayKey;
          const isSelected = key === selectedKey;
          return (
            <button
              type="button"
              key={key}
              className={`calendar__cell ${isToday ? "calendar__cell--today" : ""} ${
                isSelected ? "calendar__cell--selected" : ""
              }`}
              onClick={() => setSelectedKey(isSelected ? null : key)}
            >
              <span className="calendar__day-number">{cell.date.getDate()}</span>
              {dayTasks.length > 0 && (
                <span className="calendar__day-count">{dayTasks.length}</span>
              )}
            </button>
          );
        })}
      </div>

      <div className="calendar__selected-day">
        {selectedKey == null ? (
          <p className="empty-state">日付をタップすると、その日のタスクが見られます。</p>
        ) : selectedTasks.length === 0 ? (
          <p className="empty-state">{selectedKey} にはタスクがありません。</p>
        ) : (
          <div className="task-list">
            {selectedTasks.map((task) => (
              <div key={task.id} className="task-list__row">
                <TaskCard task={task} onToggleCompleted={(c) => onToggleCompleted(task.id, c)} />
                <button
                  className="task-list__delete"
                  onClick={() => onDelete(task.id)}
                  aria-label="削除"
                >
                  削除
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
