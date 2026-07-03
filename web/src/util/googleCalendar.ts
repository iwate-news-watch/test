function formatForGoogle(millis: number): string {
  return new Date(millis).toISOString().replace(/[-:]|\.\d{3}/g, "");
}

export function googleCalendarUrl(
  title: string,
  description: string,
  startMillis: number,
  endMillis: number = startMillis + 60 * 60 * 1000
): string {
  const params = new URLSearchParams({
    action: "TEMPLATE",
    text: title,
    details: description,
    dates: `${formatForGoogle(startMillis)}/${formatForGoogle(endMillis)}`
  });
  return `https://calendar.google.com/calendar/render?${params.toString()}`;
}
