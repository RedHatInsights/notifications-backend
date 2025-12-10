import '@patternfly/react-core/dist/styles/base.css';

import { validateSchemaResponseInterceptor } from 'openapi2typescript/react-fetching-library';
import * as React from 'react';
import { createRoot } from 'react-dom/client';
import { ClientContextProvider, createClient } from 'react-fetching-library';
import { HashRouter } from 'react-router-dom';
import { Buffer } from 'buffer';

import { App } from './app/App';

const client = createClient({
    responseInterceptors: [ validateSchemaResponseInterceptor ]
});

window.Buffer = Buffer;

const container = document.getElementById('root');
if (!container) throw new Error('Failed to find the root element');
const root = createRoot(container);

root.render(
    <HashRouter>
        <ClientContextProvider client={ client }>
            <App />
        </ClientContextProvider>
    </HashRouter>
);
