import { Router } from "express";
import { z } from "zod";
import { prisma } from "../db";
import { AuthedRequest, requireAuth } from "../middleware/requireAuth";
import { serializeTask } from "../serialize";

export const tasksRouter = Router();
tasksRouter.use(requireAuth);

const priorityEnum = z.enum(["LOW", "MEDIUM", "HIGH"]);

const createTaskSchema = z.object({
  title: z.string().trim().min(1),
  description: z.string().trim().optional(),
  priority: priorityEnum.optional(),
  dueDate: z.number().nullable().optional(),
  reminderOffsetMinutes: z.number().nullable().optional(),
  parentTaskId: z.string().nullable().optional()
});

const updateTaskSchema = z.object({
  title: z.string().trim().min(1).optional(),
  description: z.string().trim().optional(),
  isCompleted: z.boolean().optional(),
  priority: priorityEnum.optional(),
  dueDate: z.number().nullable().optional(),
  reminderOffsetMinutes: z.number().nullable().optional(),
  snoozedUntil: z.number().nullable().optional()
});

async function withSubtaskCounts(userId: string, tasks: Awaited<ReturnType<typeof prisma.task.findMany>>) {
  const ids = tasks.map((t) => t.id);
  const subtasks = ids.length
    ? await prisma.task.findMany({ where: { userId, parentTaskId: { in: ids } } })
    : [];
  const counts = new Map<string, { total: number; completed: number }>();
  for (const sub of subtasks) {
    const key = sub.parentTaskId!;
    const entry = counts.get(key) ?? { total: 0, completed: 0 };
    entry.total += 1;
    if (sub.isCompleted) entry.completed += 1;
    counts.set(key, entry);
  }
  return tasks.map((t) => {
    const c = counts.get(t.id);
    return serializeTask(t, c?.total ?? 0, c?.completed ?? 0);
  });
}

// Top-level tasks only (parentTaskId null), with subtask progress attached.
tasksRouter.get("/", async (req: AuthedRequest, res) => {
  const tasks = await prisma.task.findMany({
    where: { userId: req.userId!, parentTaskId: null },
    orderBy: [{ isCompleted: "asc" }, { createdAt: "desc" }]
  });
  res.json(await withSubtaskCounts(req.userId!, tasks));
});

tasksRouter.get("/:id/subtasks", async (req: AuthedRequest, res) => {
  const subtasks = await prisma.task.findMany({
    where: { userId: req.userId!, parentTaskId: String(req.params.id) },
    orderBy: { createdAt: "asc" }
  });
  res.json(subtasks.map((t) => serializeTask(t)));
});

tasksRouter.get("/:id", async (req: AuthedRequest, res) => {
  const task = await prisma.task.findFirst({ where: { id: String(req.params.id), userId: req.userId! } });
  if (!task) {
    res.status(404).json({ error: "タスクが見つかりません" });
    return;
  }
  res.json(serializeTask(task));
});

tasksRouter.post("/", async (req: AuthedRequest, res) => {
  const parsed = createTaskSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.issues[0]?.message ?? "入力内容が不正です" });
    return;
  }
  const data = parsed.data;

  if (data.parentTaskId) {
    const parent = await prisma.task.findFirst({ where: { id: data.parentTaskId, userId: req.userId! } });
    if (!parent) {
      res.status(404).json({ error: "親タスクが見つかりません" });
      return;
    }
  }

  const task = await prisma.task.create({
    data: {
      userId: req.userId!,
      title: data.title,
      description: data.description ?? "",
      priority: data.priority ?? "MEDIUM",
      dueDate: data.dueDate != null ? new Date(data.dueDate) : null,
      reminderOffsetMinutes: data.reminderOffsetMinutes ?? null,
      parentTaskId: data.parentTaskId ?? null
    }
  });
  res.status(201).json(serializeTask(task));
});

tasksRouter.patch("/:id", async (req: AuthedRequest, res) => {
  const parsed = updateTaskSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.issues[0]?.message ?? "入力内容が不正です" });
    return;
  }
  const existing = await prisma.task.findFirst({ where: { id: String(req.params.id), userId: req.userId! } });
  if (!existing) {
    res.status(404).json({ error: "タスクが見つかりません" });
    return;
  }

  const data = parsed.data;
  const task = await prisma.task.update({
    where: { id: existing.id },
    data: {
      ...(data.title !== undefined && { title: data.title }),
      ...(data.description !== undefined && { description: data.description }),
      ...(data.isCompleted !== undefined && { isCompleted: data.isCompleted }),
      ...(data.priority !== undefined && { priority: data.priority }),
      ...(data.dueDate !== undefined && { dueDate: data.dueDate != null ? new Date(data.dueDate) : null }),
      ...(data.reminderOffsetMinutes !== undefined && { reminderOffsetMinutes: data.reminderOffsetMinutes }),
      ...(data.snoozedUntil !== undefined && {
        snoozedUntil: data.snoozedUntil != null ? new Date(data.snoozedUntil) : null
      })
    }
  });
  res.json(serializeTask(task));
});

tasksRouter.delete("/:id", async (req: AuthedRequest, res) => {
  const existing = await prisma.task.findFirst({ where: { id: String(req.params.id), userId: req.userId! } });
  if (!existing) {
    res.status(404).json({ error: "タスクが見つかりません" });
    return;
  }
  await prisma.task.delete({ where: { id: existing.id } });
  res.status(204).send();
});
