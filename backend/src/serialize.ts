import { Task } from "@prisma/client";

export function serializeTask(task: Task, subtaskTotal = 0, subtaskCompleted = 0) {
  return {
    id: task.id,
    title: task.title,
    description: task.description,
    isCompleted: task.isCompleted,
    priority: task.priority,
    dueDate: task.dueDate ? task.dueDate.getTime() : null,
    reminderOffsetMinutes: task.reminderOffsetMinutes,
    snoozedUntil: task.snoozedUntil ? task.snoozedUntil.getTime() : null,
    parentTaskId: task.parentTaskId,
    createdAt: task.createdAt.getTime(),
    subtaskTotal,
    subtaskCompleted
  };
}
