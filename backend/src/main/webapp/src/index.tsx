import * as React from 'react';
import '@patternfly/react-core/dist/styles/base.css';
import ReactDOM from 'react-dom';
import { App } from './app/App';
import { BrowserRouter } from 'react-router-dom';

ReactDOM.render(
  <React.StrictMode>
      <BrowserRouter basename="/internal">
          <App />
      </BrowserRouter>
  </React.StrictMode>,
  document.getElementById('root')
);
