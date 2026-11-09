# ANVEX — Two-Minute Live Showcase

**Purpose:** Demonstrate the actual Java detection pipeline and measured protection outcome. Do not narrate hypothetical numbers as if they were measured. This script is intended for a short portfolio video, recruiter demo or technical interview.

## Before recording

- Start the defender and simulator locally and confirm the `ANVEX` identity handshake.
- Open the simulator at `http://127.0.0.1:8081` and the defender at `http://127.0.0.1:8080` in side-by-side browser windows.
- On the defender's local Settings screen, set protection **OFF** and reset the demo counters/state. Do not use any real users or external websites.
- Choose **Repeated Guessing** with a single seeded dummy account, rate **2 requests/second**, duration **12 seconds**, request limit **100**, concurrency **4**. Keep every setting fixed for both runs.
- Check that the session is clear of old active blocks and that the two applications share the same target URL. Dummy account passwords are published in the operations guide; none are real credentials.

## Live sequence (about 2 minutes)

| Segment | Screen | Narration cue |
| --- | --- | --- |
| **0:00–0:15** | Defender → Monitor | “ANVEX is an authentication defense lab. Protection is off, so the defender observes without extra blocking.” |
| **0:15–0:35** | Simulator → Mission Control / Scenario, start run | “A separate Java simulator sends actual HTTP authentication requests. The browser isn't inventing the telemetry.” |
| **0:35–0:55** | Defender → Evidence | “The defender independently matches a rolling-window rule and records counts, timestamps and the policy used for its decision.” |
| **0:55–1:20** | Defender Settings → reset demo state, Protection ON; Simulator → run **same** configuration | “Now I enable the defensive policy. We repeat the same workload so the difference can be measured.” |
| **1:20–1:45** | Defender → Monitor and Sources | “Rate-limited or source-blocked requests no longer reach password verification. The counters distinguish a denial from a failed connection.” |
| **1:45–2:00** | Simulator → History → Compare | “This is a report from the measured counters. ANVEX marks incompatible or incomplete comparisons instead of inventing a success percentage.” |

**Practical note:** A full 12-second run plus switching screens and resetting state may require editing down a longer screen recording to two minutes. Do not cut or splice values so that unrelated runs appear matched.

## Capture checklist

- [ ] ANVEX target handshake successful
- [ ] Protection OFF visible at baseline
- [ ] Real requests sent and failure counters shown
- [ ] Evidence includes a rule and observed threshold, not just a threat label
- [ ] Protection ON visible before the second run
- [ ] Same target, scenario, account set and run configuration for both runs
- [ ] No transport errors, or acknowledge them clearly if they occur
- [ ] A real comparison report is created, **not** an empty History screenshot
- [ ] Video and README do not claim an independently verified detection accuracy %
- [ ] Readable at 1080p; private addresses/tokens/irrelevant personal details hidden

## Do not exaggerate

The simulator supports a controlled subset of login threats. A detected traffic pattern is **suspicious**, not proof of an attacker. A comparison demonstrates outcomes for one experimental workload; it is not an industry benchmark. Mission Control is experimental until its current full-stack build is verified.
