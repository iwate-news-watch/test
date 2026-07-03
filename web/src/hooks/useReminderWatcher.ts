import { useEffect, useRef } from "react";
import { taskApi } from "../api";
import { reminderTriggerAt } from "../util/task";

const CHECK_INTERVAL_MS = 30_000;
const NOTIFY_WINDOW_MS = 5 * 60_000;

// Best-effort reminders while this tab is open. Real background delivery
// (even when the browser is closed) requires push notifications, which
// need their own server + service worker; the Android app's AlarmManager
// based reminders remain the reliable option.
export function useReminderWatcher(enabled: boolean) {
  const notified = useRef<Set<string>>(new Set());

  useEffect(() => {
    if (!enabled) return;
    if (typeof Notification !== "undefined" && Notification.permission === "default") {
      Notification.requestPermission();
    }

    const check = async () => {
      try {
        const tasks = await taskApi.list();
        const now = Date.now();
        for (const task of tasks) {
          if (task.isCompleted || notified.current.has(task.id)) continue;
          const triggerAt = reminderTriggerAt(task);
          if (triggerAt == null) continue;
          if (triggerAt <= now && now - triggerAt < NOTIFY_WINDOW_MS) {
            notified.current.add(task.id);
            if (typeof Notification !== "undefined" && Notification.permission === "granted") {
              new Notification(task.title, { body: task.description || "期限が近づいています" });
            }
          }
        }
      } catch {
        // transient network errors are fine to ignore here
      }
    };

    check();
    const interval = setInterval(check, CHECK_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [enabled]);
}
