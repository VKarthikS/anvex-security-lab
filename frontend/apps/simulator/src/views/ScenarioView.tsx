import { useMemo } from 'react';
import { Banner, EmptyState, Panel, StatusPill } from '@anvex/shared';

import { CAPS, useSimulator } from '../state/SimulatorContext';

/** The run configuration accepts at most 32 target usernames. */
const ACCOUNT_LIST_CAP = 32;

interface NumericField {
  key: 'ratePerSecond' | 'durationSeconds' | 'requestLimit' | 'concurrency';
  label: string;
  unit: string;
  cap: { min: number; max: number };
  hint: string;
}

const FIELDS: NumericField[] = [
  {
    key: 'ratePerSecond',
    label: 'Rate',
    unit: 'requests / second',
    cap: CAPS.ratePerSecond,
    hint: 'The rate the scheduler targets. The measured rate is reported by the run itself.',
  },
  {
    key: 'durationSeconds',
    label: 'Duration',
    unit: 'seconds',
    cap: CAPS.durationSeconds,
    hint: 'The run stops counting when the duration is reached.',
  },
  {
    key: 'requestLimit',
    label: 'Request limit',
    unit: 'requests',
    cap: CAPS.requestLimit,
    hint: 'Upper bound on the number of planned requests.',
  },
  {
    key: 'concurrency',
    label: 'Concurrency',
    unit: 'in flight',
    cap: CAPS.concurrency,
    hint: 'Requests kept in flight at the same time.',
  },
];

export function ScenarioView() {
  const { scenarios, form, setForm, selectScenario, currentRun, error } = useSimulator();

  const scenario = scenarios.find((item) => item.id === form.scenarioId);

  const listedUsernames = useMemo(
    () =>
      form.targetUsernames
        .split(',')
        .map((value) => value.trim())
        .filter((value) => value.length > 0),
    [form.targetUsernames],
  );

  const planned = useMemo(
    () => Math.min(form.requestLimit, form.ratePerSecond * form.durationSeconds),
    [form.durationSeconds, form.ratePerSecond, form.requestLimit],
  );

  return (
    <div className="stack">
      {error ? (
        <Banner tone="alert" title="Simulator request failed">
          {error.message} ({error.code})
        </Banner>
      ) : null}

      <Panel title="Strategies" meta={`${scenarios.length} scenario(s)`}>
        {scenarios.length === 0 ? (
          <EmptyState title="No scenarios published" note="GET /api/v1/scenarios returned nothing." />
        ) : (
          <div className="scenario-grid">
            {scenarios.map((scenario) => {
              const selected = scenario.id === form.scenarioId;
              return (
                <button
                  key={scenario.id}
                  type="button"
                  className={`scenario${selected ? ' is-selected' : ''}`}
                  onClick={() => selectScenario(scenario)}
                  aria-pressed={selected}
                >
                  <span className="row row--between">
                    <span className="scenario__name">{scenario.displayName}</span>
                    {selected ? <StatusPill tone="signal">Selected</StatusPill> : null}
                  </span>
                  <span className="scenario__description">{scenario.description}</span>
                  <span className="mono scenario__defaults">
                    default {scenario.defaultRatePerSecond} req/s · {scenario.defaultDurationSeconds}s
                    {scenario.requiresAccountCount ? ` · ${scenario.defaultAccountCount} accounts` : ''}
                  </span>
                  <span className="mono dim scenario__id">{scenario.id}</span>
                </button>
              );
            })}
          </div>
        )}
      </Panel>

      <div className="grid grid--two">
        <Panel title="Parameters" meta="guard-rail caps enforced by the service">
          <div className="form-grid">
            {FIELDS.map((field) => {
              const value = form[field.key];
              const invalid = value < field.cap.min || value > field.cap.max;
              return (
                <div className="field" key={field.key}>
                  <label className="field__label" htmlFor={field.key}>
                    {field.label}
                  </label>
                  <input
                    id={field.key}
                    className="input"
                    inputMode="numeric"
                    value={value}
                    aria-invalid={invalid ? 'true' : undefined}
                    onChange={(event) => {
                      const parsed = Number.parseInt(event.target.value, 10);
                      setForm({
                        ...form,
                        [field.key]: Number.isNaN(parsed) ? 0 : parsed,
                      });
                    }}
                  />
                  <span className="field__hint">
                    {field.hint} · {field.cap.min}–{field.cap.max} {field.unit}
                  </span>
                  {invalid ? (
                    <span className="field__error">
                      The service accepts {field.cap.min}–{field.cap.max} {field.unit}.
                    </span>
                  ) : null}
                </div>
              );
            })}

            <div className="field">
              <label className="field__label" htmlFor="targetUsernames">
                Accounts (usernames)
              </label>
              <input
                id="targetUsernames"
                className="input"
                value={form.targetUsernames}
                placeholder="asha.raman, rohan.mehta"
                aria-invalid={listedUsernames.length > ACCOUNT_LIST_CAP ? 'true' : undefined}
                onChange={(event) => setForm({ ...form, targetUsernames: event.target.value })}
              />
              <span className="field__hint">
                Comma separated, at most {ACCOUNT_LIST_CAP} usernames — the service rejects a longer list.
              </span>
              {listedUsernames.length > ACCOUNT_LIST_CAP ? (
                <span className="field__error">
                  {listedUsernames.length} usernames are listed; the service accepts at most {ACCOUNT_LIST_CAP}.
                </span>
              ) : (
                <span className="field__hint">
                  {listedUsernames.length} account(s) listed.
                  {scenario?.requiresAccountCount
                    ? ` The service tops the list up to at least ${scenario.defaultAccountCount} seeded accounts of the defender.`
                    : ' When the list is empty the service uses the published demo accounts of the defender.'}
                </span>
              )}
            </div>
          </div>

          <div className="plan-note mono">
            planned requests = min(request limit, rate × duration) = {planned} · the service applies the same formula
            when it builds the plan.
          </div>
        </Panel>

        <Panel title="What happens when you start" meta={currentRun?.state ?? 'idle'}>
          <div className="stack stack--tight muted">
            <p>
              The simulator performs an ANVEX identity handshake against the target first. If the target is unreachable
              or does not speak the ANVEX protocol, the run is returned in the <span className="mono">FAILED</span>{' '}
              state and no traffic is generated.
            </p>
            <p>
              Requests are scheduled with a bounded scheduler and a bounded in-flight semaphore. There are no hidden
              retries: every scheduled request is either sent, cancelled or counted as a transport error.
            </p>
            <p>
              Outcomes are counted separately. <span className="mono">rateLimited</span> and{' '}
              <span className="mono">sourceBlocked</span> are defender rejections;{' '}
              <span className="mono">transportErrors</span> means the request never produced a defender answer.
            </p>
            <p>
              The run id is attached to every request as untrusted metadata. It groups the defender counters for a fair
              comparison and never influences detection.
            </p>
          </div>
        </Panel>
      </div>
    </div>
  );
}
