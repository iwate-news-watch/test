import type { Task } from "../types";

export interface CalendarCell {
  date: Date | null;
}

export function startOfMonth(reference: Date): Date {
  return new Date(reference.getFullYear(), reference.getMonth(), 1);
}

export function addMonths(reference: Date, delta: number): Date {
  return new Date(reference.getFullYear(), reference.getMonth() + delta, 1);
}

export function monthLabel(reference: Date): string {
  return `${reference.getFullYear()}年${reference.getMonth() + 1}月`;
}

export function dateKey(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function buildMonthGrid(reference: Date): CalendarCell[] {
  const first = startOfMonth(reference);
  const daysInMonth = new Date(reference.getFullYear(), reference.getMonth() + 1, 0).getDate();
  const leadingBlanks = first.getDay(); // 0 = Sunday

  const cells: CalendarCell[] = [];
  for (let i = 0; i < leadingBlanks; i++) {
    cells.push({ date: null });
  }
  for (let day = 1; day <= daysInMonth; day++) {
    cells.push({ date: new Date(reference.getFullYear(), reference.getMonth(), day) });
  }
  while (cells.length % 7 !== 0) {
    cells.push({ date: null });
  }
  return cells;
}

export function groupTasksByDate(tasks: Task[]): Map<string, Task[]> {
  const map = new Map<string, Task[]>();
  for (const task of tasks) {
    if (task.dueDate == null) continue;
    const key = dateKey(new Date(task.dueDate));
    const list = map.get(key) ?? [];
    list.push(task);
    map.set(key, list);
  }
  return map;
}
