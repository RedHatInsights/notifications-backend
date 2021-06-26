import * as React from 'react';
import { PageSection, Title } from "@patternfly/react-core";
import {CodeEditor, CodeEditorControl, Language} from "@patternfly/react-code-editor";
import {useMemo} from "react";
import { PlayIcon } from "@patternfly/react-icons";

const template = "const aggregation = (action) => {\n" +
    "\n" +
    "}\n";

export const AggregationPage: React.FunctionComponent<unknown> = () => {

    const onEditorDidMount = React.useCallback((editor, monaco) => {
        editor.layout();
        editor.focus();
        monaco.editor.getModels()[0].updateOptions({ tabSize: 5 });
    }, []);

    const onChange = React.useCallback((value) => {
        console.log(value);
    }, []);

    const controls = useMemo(() => [
        <CodeEditorControl
            icon={ <PlayIcon /> }
            toolTipText="Test aggregation"
            onClick={ () => console.log('testing')}
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
                code={template}
                height="300px"
            />
        </PageSection>
    </>;
};
