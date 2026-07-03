import "dotenv/config";
import express from "express";
import cors from "cors";
import { authRouter } from "./routes/auth";
import { tasksRouter } from "./routes/tasks";

const app = express();
app.use(cors());
app.use(express.json());

app.get("/api/health", (_req, res) => {
  res.json({ ok: true });
});

app.use("/api/auth", authRouter);
app.use("/api/tasks", tasksRouter);

const port = Number(process.env.PORT) || 3000;
app.listen(port, () => {
  console.log(`Task Manager API listening on port ${port}`);
});
