export const config = {
  matcher: "/:path*"
};

// Password-gates the whole site with HTTP Basic Auth. Works on Vercel's
// Edge Middleware regardless of plan (unlike the dashboard's official
// "Password Protection", which requires Pro/Enterprise).
//
// Set BASIC_AUTH_USER and BASIC_AUTH_PASSWORD as Environment Variables in
// the Vercel project settings. If either is unset, the middleware lets
// everyone through instead of locking everyone out by accident.
export default function middleware(request: Request): Response | undefined {
  const validUser = process.env.BASIC_AUTH_USER;
  const validPassword = process.env.BASIC_AUTH_PASSWORD;

  if (!validUser || !validPassword) {
    return undefined;
  }

  const authHeader = request.headers.get("authorization");
  if (authHeader?.startsWith("Basic ")) {
    const decoded = atob(authHeader.slice("Basic ".length));
    const separatorIndex = decoded.indexOf(":");
    const user = decoded.slice(0, separatorIndex);
    const password = decoded.slice(separatorIndex + 1);
    if (user === validUser && password === validPassword) {
      return undefined;
    }
  }

  return new Response("認証が必要です", {
    status: 401,
    headers: {
      "WWW-Authenticate": 'Basic realm="Task Manager"'
    }
  });
}
