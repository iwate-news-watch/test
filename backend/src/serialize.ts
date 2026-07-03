import { Task } from "@prisma/client";

function parseReminderOffsets(raw: string | null): number[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((n) => typeof n === "number") : [];
  } catch {
    return [];
  }
}

export function serializeTask(task: Task, subtaskTotal = 0, subtaskCompleted = 0) {
  return {
    id: task.id,
    title: task.title,
    description: task.description,
    isCompleted: task.isCompleted,
    priority: task.priority,
    tag: task.tag,
    dueDate: task.dueDate ? task.dueDate.getTime() : null,
    reminderOffsetsMinutes: parseReminderOffsets(task.reminderOffsetsMinutes),
    snoozedUntil: task.snoozedUntil ? task.snoozedUntil.getTime() : null,
    parentTaskId: task.parentTaskId,
    createdAt: task.createdAt.getTime(),
    subtaskTotal,
    subtaskCompleted
  };
}
