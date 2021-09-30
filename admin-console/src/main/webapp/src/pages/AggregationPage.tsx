import '@patternfly/react-core/dist/styles/base.css';

import { CodeEditor, CodeEditorControl, Language } from '@patternfly/react-code-editor';
import { PageSection, Title } from '@patternfly/react-core';
import { PlayIcon } from '@patternfly/react-icons';
import * as React from 'react';
import { useMemo } from 'react';

const aggregationTemplate = `
// Here we can initialize the value of the Aggregation result.
const result = {
};

// This function will be called by every action that was sent to notification server
// to update the result with its values.
const aggregate = (action) => {

    return result;
};
`.trimLeft();

export const AggregationPage: React.FunctionComponent<unknown> = () => {

    const onEditorDidMount = React.useCallback((editor, monaco) => {
        editor.layout();
        editor.focus();
        monaco.editor.getModels()[0].updateOptions({ tabSize: 5 });
    }, []);

    const onChange = React.useCallback((_value) => {
        // does not do anything yet
    }, []);

    const controls = useMemo(() => [
        <CodeEditorControl
            key="test"
            icon={ <PlayIcon /> }
            toolTipText="Test aggregation"
            onClick={ () => console.log('testing') }
            isVisible={ true }
        />
    ], []);

    return <>
        <PageSection>
            <Title headingLevel="h1">Aggregation templates</Title>
        </PageSection>
        <PageSection>
            <CodeEditor
                isDownloadEnabled
                isUploadEnabled
                customControls={ controls }
                isLineNumbersVisible
                isLanguageLabelVisible
                isMinimapVisible={ false }
                language={ Language.javascript }
                onEditorDidMount={ onEditorDidMount }
                onChange={ onChange }
                code={ aggregationTemplate }
                height="300px"
            />
        </PageSection>
    </>;
};
