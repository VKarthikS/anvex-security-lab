import { formatClock, formatInteger, Sparkline, StatusPill } from '@anvex/shared';

import { useDefender } from '../state/DefenderContext';

/**
 * Calibration strip: a thin instrument bar under the header showing protection mode, the
 * measured event rate, queue depth and connection health.
 */
export function CalibrationStrip() {
  const { snapshot, streamStatus, samples, lastSnapshotAt } = useDefender();
  const counters = snapshot?.counters;
  const mode = snapshot?.protection.mode ?? null;
  const pipeline = snapshot?.eventPipeline;

  const incomingSeries = samples.map((sample) => sample.incoming);

  return (
    <div className="calibration" role="region" aria-label="Instrument status">
      <div className="calibration__cell calibration__cell--mode">
        <span className="label">Protection</span>
        {mode ? (
          <StatusPill tone={mode === 'ON' ? 'signal' : 'warn'}>
            {mode === 'ON' ? 'Enforcing' : 'Observation only'}
          </StatusPill>
        ) : (
          <span className="mono dim">—</span>
        )}
      </div>

      <div className="calibration__cell calibration__cell--rate">
        <span className="label">Event rate</span>
        <div className="calibration__spark">
          <Sparkline values={incomingSeries} width={160} height={26} ariaLabel="Incoming requests per sample" />
        </div>
        <span className="mono calibration__value">
          {incomingSeries.length > 0 ? `${formatInteger(incomingSeries[incomingSeries.length - 1])} / 2s` : '—'}
        </span>
      </div>

      <div className="calibration__cell">
        <span className="label">Queues</span>
        <span className="mono calibration__value">
          {pipeline
            ? `${formatInteger(pipeline.telemetryQueueDepth)}/${formatInteger(pipeline.telemetryQueueCapacity)}`
            : '—'}
        </span>
        <span className="mono dim">telemetry</span>
      </div>

      <div className="calibration__cell">
        <span className="label">Critical</span>
        <span className="mono calibration__value">
          {pipeline
            ? `${formatInteger(pipeline.criticalQueueDepth)}/${formatInteger(pipeline.criticalQueueCapacity)}`
            : '—'}
        </span>
        <span className="mono dim">queue</span>
      </div>

      <div className="calibration__cell">
        <span className="label">Incoming</span>
        <span className="mono calibration__value">{counters ? formatInteger(counters.incomingRequests) : '—'}</span>
        <span className="mono dim">total</span>
      </div>

      <div className="calibration__cell calibration__cell--end">
        <span className="label">Link</span>
        {streamStatus === 'live' ? (
          <StatusPill tone="signal">Live</StatusPill>
        ) : streamStatus === 'connecting' ? (
          <StatusPill tone="mute">Connecting</StatusPill>
        ) : (
          <StatusPill tone="warn">Stale — reconnecting</StatusPill>
        )}
        <span className="mono dim">{lastSnapshotAt ? `snapshot ${formatClock(new Date(lastSnapshotAt).toISOString())}` : 'no snapshot'}</span>
      </div>
    </div>
  );
}
