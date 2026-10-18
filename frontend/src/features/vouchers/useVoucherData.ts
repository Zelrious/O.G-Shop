import { useEffect, useState } from 'react';
/** Effect cancellation prevents a late response from replacing a newer filter/owner snapshot. */
export function useVoucherData<T>(load: () => Promise<T>) {
  const [snapshot, setSnapshot] = useState<{ loader: typeof load; data?: T; error?: string }>({ loader: load });
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let live = true;
    load().then(data => { if (live) setSnapshot({ loader: load, data }); }).catch(e => { if (live) setSnapshot({ loader: load, error: e instanceof Error ? e.message : 'Không tải được dữ liệu.' }); });
    return () => { live = false; };
  }, [load, revision]);
  const current = snapshot.loader === load ? snapshot : { loader: load };
  return { ...current, reload: () => { setSnapshot({ loader: load }); setRevision(v => v + 1); }, loading: current.data === undefined && !current.error };
}
export function useVoucherClock() {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 10000); return () => window.clearInterval(timer); }, []);
  return now;
}
