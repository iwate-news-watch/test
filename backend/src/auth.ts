import jwt from "jsonwebtoken";

function requireSecret(): string {
  const secret = process.env.JWT_SECRET;
  if (!secret) {
    throw new Error("JWT_SECRET environment variable is required");
  }
  return secret;
}

export function signToken(userId: string): string {
  return jwt.sign({ sub: userId }, requireSecret(), { expiresIn: "30d" });
}

export function verifyToken(token: string): string {
  const payload = jwt.verify(token, requireSecret()) as { sub: string };
  return payload.sub;
}
