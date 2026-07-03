import type { Priority, Task } from "../types";

export function isOverdue(task: Task, now: number = Date.now()): boolean {
  return !task.isCompleted && task.dueDate != null && task.dueDate < now;
}

export function reminderTriggerAt(task: Task): number | null {
  if (task.snoozedUntil != null) return task.snoozedUntil;
  if (task.dueDate == null || task.reminderOffsetMinutes == null) return null;
  return task.dueDate - task.reminderOffsetMinutes * 60_000;
}

export function priorityLabel(priority: Priority): string {
  switch (priority) {
    case "LOW":
      return "低";
    case "MEDIUM":
      return "中";
    case "HIGH":
      return "高";
  }
}

export function priorityRank(priority: Priority): number {
  switch (priority) {
    case "LOW":
      return 0;
    case "MEDIUM":
      return 1;
    case "HIGH":
      return 2;
  }
}

export function formatDueDate(millis: number): string {
  const d = new Date(millis);
  return d.toLocaleString("ja-JP", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

export function sortAllTasks(tasks: Task[]): Task[] {
  return [...tasks].sort((a, b) => {
    if (a.isCompleted !== b.isCompleted) return a.isCompleted ? 1 : -1;
    const aNoDate = a.dueDate == null;
    const bNoDate = b.dueDate == null;
    if (aNoDate !== bNoDate) return aNoDate ? 1 : -1;
    if (a.dueDate != null && b.dueDate != null && a.dueDate !== b.dueDate) {
      return a.dueDate - b.dueDate;
    }
    return priorityRank(b.priority) - priorityRank(a.priority);
  });
}

export function sortForFocus(tasks: Task[], limit: number): Task[] {
  const now = Date.now();
  return tasks
    .filter((t) => !t.isCompleted)
    .sort((a, b) => {
      const aOverdue = isOverdue(a, now);
      const bOverdue = isOverdue(b, now);
      if (aOverdue !== bOverdue) return aOverdue ? -1 : 1;
      const aNoDate = a.dueDate == null;
      const bNoDate = b.dueDate == null;
      if (aNoDate !== bNoDate) return aNoDate ? 1 : -1;
      if (a.dueDate != null && b.dueDate != null && a.dueDate !== b.dueDate) {
        return a.dueDate - b.dueDate;
      }
      return priorityRank(b.priority) - priorityRank(a.priority);
    })
    .slice(0, limit);
}

const ENCOURAGEMENTS = [
  "やった、1つ終わった!",
  "その調子!",
  "えらい、進んでる!",
  "ナイス完了!",
  "着実に前に進んでるよ"
];

export function randomEncouragement(): string {
  return ENCOURAGEMENTS[Math.floor(Math.random() * ENCOURAGEMENTS.length)];
}
