export interface WordmarkProps {
  /** Station label rendered after the mark, e.g. "DEFENDER" or "SIMULATOR". */
  station?: string;
  size?: number;
}

/** ANVEX wordmark with the compact AVX monogram. */
export function Wordmark({ station, size = 26 }: WordmarkProps) {
  return (
    <span className="wordmark">
      <svg
        className="wordmark__mark"
        width={size}
        height={size}
        viewBox="0 0 32 32"
        role="img"
        aria-label="ANVEX"
        focusable="false"
      >
        <title>ANVEX</title>
        <path d="M16 2.5 29 9v14L16 29.5 3 23V9z" fill="none" stroke="currentColor" strokeWidth="1.5" />
        <path d="M10 20.5 14.4 11h3.2L22 20.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        <path d="M12.1 17.2h7.8" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        <path d="M16 2.5v6.2" fill="none" stroke="currentColor" strokeWidth="1.5" />
      </svg>
      <span className="wordmark__text">
        ANVEX
        {station ? <span className="wordmark__station">{station}</span> : null}
      </span>
    </span>
  );
}
