import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { NavBar } from "../components/NavBar";
import { taskApi } from "../api";
import type { Priority, Task } from "../types";
import { googleCalendarUrl } from "../util/googleCalendar";

const REMINDER_OPTIONS: { label: string; minutes: number | null }[] = [
  { label: "通知なし", minutes: null },
  { label: "期限ちょうど", minutes: 0 },
  { label: "10分前", minutes: 10 },
  { label: "30分前", minutes: 30 },
  { label: "1時間前", minutes: 60 },
  { label: "前日", minutes: 1440 }
];

type TimeOfDay = "MORNING" | "NOON" | "EVENING" | "NIGHT";

const TIME_OF_DAY_HOURS: Record<TimeOfDay, number> = {
  MORNING: 9,
  NOON: 12,
  EVENING: 18,
  NIGHT: 21
};

const TIME_OF_DAY_OPTIONS: { value: TimeOfDay; label: string }[] = [
  { value: "MORNING", label: "朝" },
  { value: "NOON", label: "昼" },
  { value: "EVENING", label: "夕方" },
  { value: "NIGHT", label: "夜" }
];

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

function toDateOnlyStr(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function dateOnlyStr(millis: number): string {
  return toDateOnlyStr(new Date(millis));
}

function combineDateAndTimeOfDay(dateStr: string, timeOfDay: TimeOfDay): number {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(y, m - 1, d, TIME_OF_DAY_HOURS[timeOfDay], 0, 0, 0).getTime();
}

function closestTimeOfDay(millis: number): TimeOfDay {
  const hour = new Date(millis).getHours();
  return TIME_OF_DAY_OPTIONS.reduce((closest, opt) =>
    Math.abs(TIME_OF_DAY_HOURS[opt.value] - hour) < Math.abs(TIME_OF_DAY_HOURS[closest] - hour)
      ? opt.value
      : closest,
    "MORNING" as TimeOfDay
  );
}

function addDays(base: Date, days: number): Date {
  const d = new Date(base);
  d.setDate(d.getDate() + days);
  return d;
}

function nextSaturday(base: Date): Date {
  const d = new Date(base);
  d.setDate(d.getDate() + ((6 - d.getDay() + 7) % 7));
  return d;
}

const DATE_PRESETS: { label: string; resolve: () => Date }[] = [
  { label: "今日", resolve: () => new Date() },
  { label: "明日", resolve: () => addDays(new Date(), 1) },
  { label: "今週末", resolve: () => nextSaturday(new Date()) },
  { label: "来週", resolve: () => addDays(new Date(), 7) }
];

export function TaskEditPage() {
  const { id } = useParams();
  const isNew = id === undefined;
  const navigate = useNavigate();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<Priority>("MEDIUM");
  const [tag, setTag] = useState("");
  const [dueDate, setDueDate] = useState<number | null>(null);
  const [timeOfDay, setTimeOfDay] = useState<TimeOfDay>("MORNING");
  const [reminderOffsetMinutes, setReminderOffsetMinutes] = useState<number | null>(null);
  const [subtasks, setSubtasks] = useState<Task[]>([]);
  const [subtaskText, setSubtaskText] = useState("");
  const [loading, setLoading] = useState(!isNew);
  const [error, setError] = useState<string | null>(null);

  const loadSubtasks = async (taskId: string) => {
    setSubtasks(await taskApi.subtasks(taskId));
  };

  useEffect(() => {
    if (isNew || !id) return;
    (async () => {
      try {
        const task = await taskApi.get(id);
        setTitle(task.title);
        setDescription(task.description);
        setPriority(task.priority);
        setTag(task.tag ?? "");
        setDueDate(task.dueDate);
        if (task.dueDate != null) setTimeOfDay(closestTimeOfDay(task.dueDate));
        setReminderOffsetMinutes(task.reminderOffsetMinutes);
        await loadSubtasks(id);
      } catch {
        setError("タスクを読み込めませんでした");
      } finally {
        setLoading(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, isNew]);

  const onSave = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    const payload = {
      title: title.trim(),
      description: description.trim(),
      priority,
      tag: tag.trim() || null,
      dueDate,
      reminderOffsetMinutes: dueDate == null ? null : reminderOffsetMinutes
    };
    if (isNew) {
      await taskApi.create(payload);
    } else if (id) {
      await taskApi.update(id, payload);
    }
    navigate(-1);
  };

  const onDelete = async () => {
    if (!id) return;
    await taskApi.remove(id);
    navigate(-1);
  };

  const onAddSubtask = async (e: FormEvent) => {
    e.preventDefault();
    if (!id || !subtaskText.trim()) return;
    await taskApi.create({ title: subtaskText.trim(), parentTaskId: id });
    setSubtaskText("");
    await loadSubtasks(id);
  };

  const onToggleSubtask = async (subtaskId: string, completed: boolean) => {
    if (!id) return;
    await taskApi.update(subtaskId, { isCompleted: completed });
    await loadSubtasks(id);
  };

  const onDeleteSubtask = async (subtaskId: string) => {
    if (!id) return;
    await taskApi.remove(subtaskId);
    await loadSubtasks(id);
  };

  const applyDatePreset = (date: Date) => {
    setDueDate(combineDateAndTimeOfDay(toDateOnlyStr(date), timeOfDay));
  };

  const onCustomDateChange = (value: string) => {
    setDueDate(value ? combineDateAndTimeOfDay(value, timeOfDay) : null);
  };

  const onTimeOfDayChange = (t: TimeOfDay) => {
    setTimeOfDay(t);
    if (dueDate != null) {
      setDueDate(combineDateAndTimeOfDay(dateOnlyStr(dueDate), t));
    }
  };

  if (loading) {
    return (
      <div className="page">
        <NavBar />
        <p className="empty-state">読み込み中…</p>
      </div>
    );
  }

  return (
    <div className="page">
      <NavBar />
      <header className="page__header">
        <h1>{isNew ? "新しいタスク" : "タスクを編集"}</h1>
        {!isNew && (
          <button className="danger-link" onClick={onDelete}>
            削除
          </button>
        )}
      </header>

      {error && <p className="auth-error">{error}</p>}

      <form id="task-form" className="task-form" onSubmit={onSave}>
        <label>
          タイトル
          <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} required />
        </label>

        <label>
          メモ
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} />
        </label>

        <div className="field-group">
          <span className="field-group__label">優先度</span>
          <div className="chip-row">
            {(["LOW", "MEDIUM", "HIGH"] as Priority[]).map((p) => (
              <button
                type="button"
                key={p}
                className={`chip ${priority === p ? "chip--selected" : ""}`}
                onClick={() => setPriority(p)}
              >
                {p === "LOW" ? "低" : p === "MEDIUM" ? "中" : "高"}
              </button>
            ))}
          </div>
        </div>

        <div className="field-group">
          <span className="field-group__label">タグ</span>
          <input
            type="text"
            list="tag-suggestions"
            value={tag}
            onChange={(e) => setTag(e.target.value)}
            placeholder="仕事、プライベートなど(空欄でもOK)"
          />
          <datalist id="tag-suggestions">
            <option value="仕事" />
            <option value="プライベート" />
          </datalist>
        </div>

        <div className="field-group">
          <span className="field-group__label">期限日</span>
          <div className="chip-row">
            {DATE_PRESETS.map((preset) => (
              <button
                type="button"
                key={preset.label}
                className={`chip ${
                  dueDate != null && dateOnlyStr(dueDate) === toDateOnlyStr(preset.resolve()) ? "chip--selected" : ""
                }`}
                onClick={() => applyDatePreset(preset.resolve())}
              >
                {preset.label}
              </button>
            ))}
          </div>
          <input
            type="date"
            value={dueDate != null ? dateOnlyStr(dueDate) : ""}
            onChange={(e) => onCustomDateChange(e.target.value)}
          />
          {dueDate != null && (
            <button type="button" className="text-button" onClick={() => setDueDate(null)}>
              クリア
            </button>
          )}
        </div>

        <div className="field-group">
          <span className="field-group__label">時間帯</span>
          <div className="chip-row">
            {TIME_OF_DAY_OPTIONS.map((opt) => (
              <button
                type="button"
                key={opt.value}
                className={`chip ${timeOfDay === opt.value ? "chip--selected" : ""}`}
                onClick={() => onTimeOfDayChange(opt.value)}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {dueDate != null && (
          <>
            <div className="field-group">
              <span className="field-group__label">リマインダー通知</span>
              <div className="chip-row">
                {REMINDER_OPTIONS.map((opt) => (
                  <button
                    type="button"
                    key={opt.label}
                    className={`chip ${reminderOffsetMinutes === opt.minutes ? "chip--selected" : ""}`}
                    onClick={() => setReminderOffsetMinutes(opt.minutes)}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
              <p className="hint">
                ※ Web版はブラウザを開いている間のみ通知します。確実な通知はAndroidアプリをご利用ください。
              </p>
            </div>

            <a
              className="secondary-button"
              href={googleCalendarUrl(title || "タスク", description, dueDate)}
              target="_blank"
              rel="noreferrer"
            >
              📅 Googleカレンダーに追加
            </a>
          </>
        )}
      </form>

      {!isNew && id && (
        <div className="field-group subtasks">
          <span className="field-group__label">
            {subtasks.length === 0
              ? "サブタスクで作業を分解する"
              : `サブタスク ${subtasks.filter((s) => s.isCompleted).length}/${subtasks.length}`}
          </span>
          {subtasks.map((s) => (
            <div key={s.id} className="subtask-row">
              <input
                type="checkbox"
                checked={s.isCompleted}
                onChange={(e) => onToggleSubtask(s.id, e.target.checked)}
              />
              <span className={s.isCompleted ? "subtask-row__title--done" : ""}>{s.title}</span>
              <button type="button" onClick={() => onDeleteSubtask(s.id)} aria-label="削除">
                ×
              </button>
            </div>
          ))}
          <form className="quick-add" onSubmit={onAddSubtask}>
            <input
              type="text"
              value={subtaskText}
              onChange={(e) => setSubtaskText(e.target.value)}
              placeholder="小さいステップを追加…"
            />
            <button type="submit">追加</button>
          </form>
        </div>
      )}

      <div className="task-form">
        <button type="submit" form="task-form" className="primary-button" disabled={!title.trim()}>
          保存
        </button>
      </div>
    </div>
  );
}
