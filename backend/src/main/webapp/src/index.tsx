import '@patternfly/react-core/dist/styles/base.css';

import { validateSchemaResponseInterceptor } from 'openapi2typescript/react-fetching-library';
import * as React from 'react';
import ReactDOM from 'react-dom';
import { ClientContextProvider, createClient } from 'react-fetching-library';
import { HashRouter } from 'react-router-dom';

import { App } from './app/App';

const client = createClient({
    responseInterceptors: [ validateSchemaResponseInterceptor ]
});

ReactDOM.render(
    <React.StrictMode>
        <HashRouter>
            <ClientContextProvider client={ client }>
                <App />
            </ClientContextProvider>
        </HashRouter>
    </React.StrictMode>,
    document.getElementById('root')
);
