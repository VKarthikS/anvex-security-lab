import { CountdownRing, formatClock, formatInteger, StatusPill, useNow, type SourceBlock } from '@anvex/shared';

export interface BlockListProps {
  blocks: SourceBlock[];
  emptyTitle?: string;
  emptyNote?: string;
}

/** Active source blocks with a live countdown ring. */
export function BlockList({ blocks, emptyTitle = 'No active blocks', emptyNote }: BlockListProps) {
  const now = useNow(1000);

  if (blocks.length === 0) {
    return (
      <div className="empty">
        <p className="empty__title">{emptyTitle}</p>
        {emptyNote ? <p className="empty__note">{emptyNote}</p> : null}
      </div>
    );
  }

  return (
    <ul className="blocklist">
      {blocks.map((block) => {
        const expiry = new Date(block.expiry).getTime();
        const created = new Date(block.createdAt).getTime();
        const remaining = Math.max(0, expiry - now);
        const total = Math.max(1, expiry - created);
        return (
          <li key={block.blockId} className="blocklist__item">
            <CountdownRing remainingMs={remaining} totalMs={total} size={54} label={`${block.sourceIp} block time remaining`} />
            <div className="grow">
              <div className="row row--between">
                <span className="mono blocklist__ip">{block.sourceIp}</span>
                <StatusPill tone="alert">{block.state}</StatusPill>
              </div>
              <p className="blocklist__reason">{block.reason}</p>
              <p className="mono dim blocklist__meta">
                {block.ruleId} · expires {formatClock(block.expiry)} · {formatInteger(remaining / 1000)}s left
              </p>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
