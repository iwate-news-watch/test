export type Priority = "LOW" | "MEDIUM" | "HIGH";

export interface Task {
  id: string;
  title: string;
  description: string;
  isCompleted: boolean;
  priority: Priority;
  tag: string | null;
  dueDate: number | null;
  reminderOffsetsMinutes: number[];
  snoozedUntil: number | null;
  isRecurring: boolean;
  parentTaskId: string | null;
  createdAt: number;
  subtaskTotal: number;
  subtaskCompleted: number;
}

export type TaskFilter = "ALL" | "ACTIVE" | "COMPLETED";

export interface AuthUser {
  id: string;
  email: string;
}
