import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import '@anvex/shared/styles/tokens.css';
import '@anvex/shared/styles/base.css';
import './styles.css';

import { App } from './App';

const container = document.getElementById('root');
if (!container) {
  throw new Error('ANVEX simulator station: #root container is missing.');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
