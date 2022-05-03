import { CodeEditor, CodeEditorControl, Language } from '@patternfly/react-code-editor';
import {
    Alert,
    AlertVariant,
    List,
    ListItem,
    PageSection,
    Stack, StackItem,
    Title
} from '@patternfly/react-core';
import { CheckCircleIcon } from '@patternfly/react-icons';
import { global_palette_green_400 } from '@patternfly/react-tokens';
import * as React from 'react';
import { useParameterizedQuery } from 'react-fetching-library';

import { Operations } from '../generated/OpenapiInternal';

const defaultPayload = JSON.stringify({}, null, 2);

type ValidationResult = {
    code: string;
    errors: Record<string, Array<string>>;
}

const renderErrors = (errors: Record<string, Array<string>>) => {
    const sortedKeys = Object.keys(errors).sort();
    const flatErrors: Array<string> = [];
    sortedKeys.forEach(key => {
        errors[key].forEach(error => flatErrors.push(error));
    });

    return <List>
        { flatErrors.map((value, index) => (<ListItem key={ index }>{ value }</ListItem>)) }
    </List>;
};

export const MessageValidatorPage: React.FunctionComponent = () => {

    const validateService = useParameterizedQuery(Operations.ValidationResourceValidateMessage.actionCreator);

    const [ message, setMessage ] = React.useState<string>(defaultPayload);
    const [ validationResult, setValidationResult ] = React.useState<ValidationResult>();

    const runValidation = React.useCallback(async () => {

        let jsonMessage;
        try {
            jsonMessage = JSON.parse(message);
        } catch (e: any) {
            console.log(e);
            setValidationResult({
                errors: {
                    $: [
                        'Not a valid json: ' + e.message
                    ]
                },
                code: message
            });
            return;
        }

        const response = await validateService.query({
            body: jsonMessage
        });

        if (response.payload?.status === 200) {
            setValidationResult({
                errors: {},
                code: message
            });
        } else if (response.payload?.status === 400) {
            setValidationResult({
                errors: response.payload.value.errors,
                code: message
            });
        }

    }, [ validateService, message ]);

    const validatePayloadButton = <CodeEditorControl
        icon={ <CheckCircleIcon color={ global_palette_green_400.value } /> }
        toolTipText="Validates the message"
        onClick={ runValidation }
    />;

    let validationStatus: AlertVariant;
    let validationMessage;

    if (validationResult?.code === message) {
        if (Object.keys(validationResult.errors).length === 0) {
            validationStatus = AlertVariant.success;
            validationMessage = 'Message is valid';
        } else {
            validationStatus = AlertVariant.danger;
            validationMessage = <>
                <div>Message is invalid:</div>
                {renderErrors(validationResult.errors)}
            </>;
        }
    } else {
        validationStatus = AlertVariant.warning;
        validationMessage = <>
            <div>Message has not been validated. Use the validation button</div>
            { validationResult?.errors && Object.keys(validationResult.errors).length > 0 && (
                <>
                    <div>Previous errors:</div>
                    { renderErrors(validationResult.errors) }
                </>
            ) }
        </>;
    }

    return  (
        <>
            <PageSection>
                <Stack>
                    <StackItem isFilled>
                        <Title headingLevel="h1" >Notification validator</Title>
                    </StackItem>
                    <StackItem>
                        <span>You can use this utility to verify the notification you are sending is valid and tweak as needed.</span>
                    </StackItem>
                </Stack>
            </PageSection>
            <PageSection>
                <Alert isInline title={ validationMessage } variant={ validationStatus }  />
                <CodeEditor
                    showEditor={ true }
                    isUploadEnabled
                    isDownloadEnabled
                    isCopyEnabled
                    isMinimapVisible={ false }
                    customControls={ validatePayloadButton }
                    code={ message }
                    height="500px"
                    isLanguageLabelVisible
                    language={ Language.json }
                    onChange={ setMessage }
                />
            </PageSection>
        </>
    );
};
