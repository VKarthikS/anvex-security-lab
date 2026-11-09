import { Icon, Meter, Panel, Stat, StatusPill, formatInteger } from '@anvex/shared';

import { useDefender } from '../state/DefenderContext';
import { BlockList } from './BlockList';

function ProtectionStateCard() {
  const { snapshot } = useDefender();
  const mode = snapshot?.protection.mode ?? null;

  if (!mode) {
    return (
      <div className="protection-card protection-card--unknown">
        <span className="label">Protection state</span>
        <p className="protection-card__headline">STATE UNKNOWN</p>
        <p className="protection-card__note">The dashboard snapshot has not been read yet.</p>
      </div>
    );
  }

  if (mode === 'OFF') {
    return (
      <div className="protection-card protection-card--off" role="alert">
        <div className="row row--between">
          <span className="label label--warn">Protection state</span>
          <StatusPill tone="warn">Off</StatusPill>
        </div>
        <p className="protection-card__headline">OBSERVATION ONLY</p>
        <p className="protection-card__note">
          Protection is <strong>OFF</strong>. Detections and evidence are still produced, but nothing is rate limited
          and no source is blocked.
        </p>
      </div>
    );
  }

  return (
    <div className="protection-card protection-card--on">
      <div className="row row--between">
        <span className="label label--signal">Protection state</span>
        <StatusPill tone="signal">On</StatusPill>
      </div>
      <p className="protection-card__headline">PROTECTION ON</p>
      <p className="protection-card__note">
        Enforcement is active: matching sources are rate limited and temporarily blocked.
      </p>
    </div>
  );
}

function PipelineHealth() {
  const { snapshot } = useDefender();
  const pipeline = snapshot?.eventPipeline;

  if (!pipeline) {
    return <p className="mono dim">Pipeline diagnostics are not available yet.</p>;
  }

  const telemetryRatio = pipeline.telemetryQueueDepth / Math.max(1, pipeline.telemetryQueueCapacity);
  const criticalRatio = pipeline.criticalQueueDepth / Math.max(1, pipeline.criticalQueueCapacity);

  return (
    <div className="stack stack--tight">
      <div className="row row--between">
        <span className="label">Telemetry queue</span>
        <span className="mono">
          {formatInteger(pipeline.telemetryQueueDepth)} / {formatInteger(pipeline.telemetryQueueCapacity)}
        </span>
      </div>
      <Meter
        value={pipeline.telemetryQueueDepth}
        max={pipeline.telemetryQueueCapacity}
        tone={telemetryRatio > 0.8 ? 'warn' : 'signal'}
        label="Telemetry queue depth"
      />

      <div className="row row--between">
        <span className="label">Critical queue</span>
        <span className="mono">
          {formatInteger(pipeline.criticalQueueDepth)} / {formatInteger(pipeline.criticalQueueCapacity)}
        </span>
      </div>
      <Meter
        value={pipeline.criticalQueueDepth}
        max={pipeline.criticalQueueCapacity}
        tone={criticalRatio > 0.8 ? 'alert' : 'violet'}
        label="Critical queue depth"
      />

      <dl className="kv">
        <dt className="kv__key">Pipeline</dt>
        <dd className="kv__value">{pipeline.running ? 'running' : 'stopped'}</dd>
        <dt className="kv__key">Published</dt>
        <dd className="kv__value">{formatInteger(pipeline.publishedEvents)}</dd>
        <dt className="kv__key">Aggregated</dt>
        <dd className="kv__value">{formatInteger(pipeline.aggregatedEvents)}</dd>
        <dt className="kv__key">Dropped</dt>
        <dd className="kv__value">{formatInteger(pipeline.droppedEvents)}</dd>
        <dt className="kv__key">Critical dropped</dt>
        <dd className="kv__value">{formatInteger(pipeline.criticalDroppedEvents)}</dd>
        <dt className="kv__key">Overload responses</dt>
        <dd className="kv__value">{formatInteger(pipeline.overloadResponses)}</dd>
        <dt className="kv__key">Subscribers</dt>
        <dd className="kv__value">{formatInteger(pipeline.subscriberCount)}</dd>
      </dl>
    </div>
  );
}

/** The always-visible protection column. */
export function ProtectionColumn() {
  const { snapshot } = useDefender();
  const counters = snapshot?.counters;

  return (
    <div className="stack">
      <ProtectionStateCard />

      <Panel title="Active blocks" meta={snapshot ? `${formatInteger(snapshot.activeBlocks.length)} active` : '—'}>
        <BlockList
          blocks={snapshot?.activeBlocks ?? []}
          emptyTitle="No source is blocked"
          emptyNote="Blocks appear here when protection is ON and a rule matches."
        />
      </Panel>

      <Panel title="Totals" meta={snapshot?.protection.policyVersion ?? '—'}>
        <div className="stack stack--tight">
          <Stat label="Incoming" value={counters ? formatInteger(counters.incomingRequests) : '—'} unit="requests" small />
          <Stat
            label="Reached authentication"
            value={counters ? formatInteger(counters.requestsReachingAuthentication) : '—'}
            unit="requests"
            small
          />
          <Stat label="Detection alerts" value={counters ? formatInteger(counters.detectionAlerts) : '—'} unit="alerts" small />
        </div>
      </Panel>

      <Panel title="Pipeline health">
        <PipelineHealth />
      </Panel>

      <p className="side-note">
        <Icon name="shield" size={13} />
        <span>Analyst controls live in Settings and Diagnostics and require a loopback caller.</span>
      </p>
    </div>
  );
}
