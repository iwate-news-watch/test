import type { AuthUser, Priority, Task } from "./types";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:3000/api";

export class ApiError extends Error {}

let currentToken: string | null = localStorage.getItem("token");

export function setToken(token: string | null) {
  currentToken = token;
  if (token) {
    localStorage.setItem("token", token);
  } else {
    localStorage.removeItem("token");
  }
}

export function getToken(): string | null {
  return currentToken;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string> | undefined)
  };
  if (currentToken) {
    headers.Authorization = `Bearer ${currentToken}`;
  }

  const res = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (res.status === 204) {
    return undefined as T;
  }
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new ApiError(body.error ?? `リクエストに失敗しました (${res.status})`);
  }
  return body as T;
}

export const authApi = {
  register: (email: string, password: string) =>
    request<{ token: string; user: AuthUser }>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password })
    }),
  login: (email: string, password: string) =>
    request<{ token: string; user: AuthUser }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password })
    })
};

export interface TaskInput {
  title: string;
  description?: string;
  priority?: Priority;
  tag?: string | null;
  dueDate?: number | null;
  reminderOffsetMinutes?: number | null;
  parentTaskId?: string | null;
}

export interface TaskUpdateInput {
  title?: string;
  description?: string;
  isCompleted?: boolean;
  priority?: Priority;
  tag?: string | null;
  dueDate?: number | null;
  reminderOffsetMinutes?: number | null;
  snoozedUntil?: number | null;
}

export const taskApi = {
  list: () => request<Task[]>("/tasks"),
  get: (id: string) => request<Task>(`/tasks/${id}`),
  subtasks: (id: string) => request<Task[]>(`/tasks/${id}/subtasks`),
  create: (input: TaskInput) =>
    request<Task>("/tasks", { method: "POST", body: JSON.stringify(input) }),
  update: (id: string, input: TaskUpdateInput) =>
    request<Task>(`/tasks/${id}`, { method: "PATCH", body: JSON.stringify(input) }),
  remove: (id: string) => request<void>(`/tasks/${id}`, { method: "DELETE" })
};
