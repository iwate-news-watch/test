import { NextFunction, Request, Response } from "express";
import { verifyToken } from "../auth";

export interface AuthedRequest extends Request {
  userId?: string;
}

export function requireAuth(req: AuthedRequest, res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith("Bearer ")) {
    res.status(401).json({ error: "認証が必要です" });
    return;
  }
  const token = header.slice("Bearer ".length);
  try {
    req.userId = verifyToken(token);
    next();
  } catch {
    res.status(401).json({ error: "トークンが無効です" });
  }
}
