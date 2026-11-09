import { useEffect, useState } from 'react';

/**
 * A clock that ticks on an interval. Used for countdowns and relative timestamps so that the
 * displayed value always corresponds to the current moment rather than the last render.
 */
export function useNow(intervalMs = 1000): number {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(timer);
  }, [intervalMs]);

  return now;
}
