// The open line to the app: ordered answers, and the old way whenever it breaks.
import { IDLE_CLOSE_MS, PORT_RETRY_MS, createLink } from "../lib/native-link.js";

let checks = 0, failures = 0;
function check(ok, label) { checks++; if (!ok) { failures++; print(`  FAIL  ${label}`); } }
const settle = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };

function rig({ canConnect = true } = {}) {
  let clock = 0, nextTimer = 1;
  const timers = new Map();
  const r = { ports: [], sent: [], connects: 0 };
  r.advance = (ms) => {
    clock += ms;
    for (const [id, timer] of [...timers]) if (timer.at <= clock) { timers.delete(id); timer.fn(); }
  };
  r.link = createLink({
    connect: canConnect ? () => {
      r.connects++;
      const port = { posted: [], open: true, onMessage: { addListener: (fn) => { port.message = fn; } },
                     onDisconnect: { addListener: (fn) => { port.gone = fn; } },
                     postMessage: (m) => { if (!port.open) throw new Error("closed"); port.posted.push(m); },
                     disconnect: () => { port.open = false; } };
      r.ports.push(port);
      return port;
    } : undefined,
    send: async (m) => { r.sent.push(m); return { ok: true, via: "process", n: m.n }; },
    now: () => clock,
    setTimer: (fn, ms) => { const id = nextTimer++; timers.set(id, { fn, at: clock + ms }); return id; },
    clearTimer: (id) => timers.delete(id),
  });
  return r;
}

{
  const r = rig();
  const a = r.link.ask({ n: 1 }, 6000), b = r.link.ask({ n: 2 }, 6000);
  check(r.connects === 1 && r.ports[0].posted.length === 2 && r.sent.length === 0, "two questions share one port and start no process");
  r.ports[0].message({ ok: true, n: 1 });
  r.ports[0].message({ ok: true, n: 2 });
  check((await a).n === 1 && (await b).n === 2, "answers go to their questions in order");
  await r.link.ask({ n: 3 }, 6000).constructor;
  r.ports[0].message({ ok: true, n: 3 });
  check(r.connects === 1, "the port is reused for the next question");
  r.advance(IDLE_CLOSE_MS + 1);
  check(!r.link.isOpen() && r.ports[0].open === false, "an idle port is closed");
  const c = r.link.ask({ n: 4 }, 6000);
  check(r.connects === 2, "a port that worked is opened again after idling");
  r.ports[1].message({ ok: true, n: 4 });
  check((await c).n === 4, "and answers");
}

{
  const r = rig();
  const a = r.link.ask({ n: 1 }, 6000), b = r.link.ask({ n: 2 }, 6000);
  r.ports[0].gone();
  const [ra, rb] = [await a, await b];
  check(ra.via === "process" && rb.via === "process" && ra.n === 1 && rb.n === 2, "a port that drops has its questions asked the old way");
  const c = await r.link.ask({ n: 3 }, 6000);
  check(r.connects === 1 && c.via === "process", "a port that never answered is not reopened at once");
  r.advance(PORT_RETRY_MS + 1);
  r.link.ask({ n: 4 }, 6000);
  check(r.connects === 2, "it is tried again later");
}

{
  const r = rig();
  const a = r.link.ask({ n: 1 }, 1000);
  const b = r.link.ask({ n: 2 }, 6000);
  let failed = null;
  a.catch((e) => { failed = e.message; });
  r.advance(1001);
  await settle();
  check(failed === "timed out", "a question the port never answered times out");
  check((await b).via === "process" && r.ports[0].open === false, "the port is closed so later answers cannot land on the wrong question");
}

{
  const r = rig({ canConnect: false });
  const a = await r.link.ask({ n: 1 }, 6000);
  check(a.via === "process" && r.sent.length === 1, "a browser with no ports uses one process per question");
}

print(`  ${checks - failures}/${checks} native link checks passed`);
if (failures) throw new Error(`${failures} native link check(s) failed`);
