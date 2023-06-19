import * as React from 'react';
import {Button, Form, FormGroup, HelperText, HelperTextItem, PageSection, Skeleton, SkeletonProps, Spinner, Split, SplitItem, StackItem, TextInput, Title} from '@patternfly/react-core';
import {CodeEditor, Language} from '@patternfly/react-code-editor';
import { useRenderEmailRequest } from '../../services/RenderEmailRequest';
import { Template } from '../../types/Notifications';

export interface  EmailTemplateFormProps {
    isLoading: boolean;
    template: Partial<Template>;
    updateTemplate: (template: Partial<Template>) => void;
}

interface SkeletonIfLoading extends SkeletonProps {
    children: React.ReactNode;
    isLoading?: boolean;
}

const SkeletonIfLoading: React.FunctionComponent<SkeletonIfLoading> = props => (
    props.isLoading ? <Skeleton { ...props } /> : <> { props.children } </>
);

const defaultPayload = JSON.stringify({
    bundle: 'rhel',
    application: 'policies',
    event_type: 'policy-triggered',
    timestamp: '2021-08-05T16:21:14.243',
    org_id: '5758117',
    // eslint-disable-next-line max-len
    context: '{"inventory_id":"80f7e57d-a16a-4189-82af-1d68a747c8b3","system_check_in":"2021-08-05T16:21:12.953036","display_name":"cool display name"}',
    events: [
        {
            metadata: {},
            payload: '{"my_id":"3df53241-3e09-481b-a322-4892caaaaadc","my_name":"Red color"}'
        },
        {
            metadata: {},
            payload: '{"my_id":"6c5e8451-a40a-4bb7-ab9a-0cb10a4c577d","my_name":"Green color"}'
        },
        {
            metadata: {},
            payload: '{"my_id":"b4c6378a-c1fb-4d3e-8e9b-7e5bdfc09dd3","my_name":"Blue color"}'
        }
    ]
}, null, 2);

type RenderedTemplateProps = {
    isLoading: true;
} | {
   isLoading: false;
   succeeded: true;
   template: Array<string>;
} | {
    isLoading: false;
    succeeded: false;
    error: string;
};

const RenderedTemplate: React.FunctionComponent<RenderedTemplateProps> = props => {
    if (props.isLoading) {
        return <Spinner />;
    }

    if (props.succeeded) {
        return (
            <>
                <StackItem>
                    <strong>Content:</strong>
                </StackItem>
                <StackItem>
                    {/* eslint-disable-next-line @typescript-eslint/ban-ts-comment*/}
                    {/* @ts-ignore */}
                    <iframe width="100%" style={{ resize: 'both' }} srcDoc={ props.template } />
                </StackItem>
            </>
        );
    }

    return (
        <StackItem>
            <HelperText>
                <HelperTextItem variant="error">{ props.error }</HelperTextItem>
            </HelperText>
        </StackItem>
    );
};

export const RenderEmailTemplateForm: React.FunctionComponent<EmailTemplateFormProps> = props => {
    const emailTemplate = useRenderEmailRequest();
    const [ payload, setPayload ] = React.useState<string | undefined>(defaultPayload);

    React.useEffect(() => {
        const mutate = emailTemplate.mutate;
        mutate({
            subject: '',
            body: props.template.data ?? '',
            payload: payload ?? ''
        });
        // We only want to activate this once
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ ]);

    let renderedProps: RenderedTemplateProps;

    if (emailTemplate.loading) {
        renderedProps = {
            isLoading: true
        };
    } else if (emailTemplate.payload?.status === 200) {
        renderedProps = {
            isLoading: false,
            succeeded: true,
            template: emailTemplate.payload.value.result ?? []
        };
    } else if (emailTemplate.payload?.status === 400) {
        renderedProps = {
            isLoading: false,
            succeeded: false,
            error: `Failed to render template: ${emailTemplate.payload.value.message}` ?? 'Unknown error'
        };
    } else {
        renderedProps = {
            isLoading: false,
            succeeded: false,
            error: 'Unknown error'
        };
    }

    const onRender = React.useCallback(() => {
        const mutate = emailTemplate.mutate;
        mutate({
            subject: '',
            body: props.template.data ?? '',
            payload: payload ?? ''
        });
    }, [ emailTemplate.mutate, payload, props.template.data ]);

    return (
        <>
            <PageSection>
                <Split hasGutter>
                    <SplitItem isFilled>
                        <Title headingLevel="h2">Result</Title>
                    </SplitItem>
                    <SplitItem>
                        <Button onClick={ onRender }>Render</Button>
                    </SplitItem>
                </Split>
                <RenderedTemplate { ...renderedProps }  />
            </PageSection>
            <Form>
                <FormGroup
                    label='Name'
                    isRequired
                    helperText='Enter a name for your template'
                >
                    <SkeletonIfLoading isLoading={ props.isLoading }>
                        <TextInput
                            type='text'
                            id='name'
                            name="name"
                            value={ props.template?.name ?? '' }
                            onChange={ name => props.updateTemplate({ name })}
                        />
                    </SkeletonIfLoading>
                </FormGroup>
                <FormGroup
                    label='Description'
                    isRequired
                    helperText='Enter a brief description for your template'
                >
                    <SkeletonIfLoading isLoading={props.isLoading}>
                        <TextInput
                            type='text'
                            id='description'
                            name="description"
                            value={ props.template?.description ?? '' }
                            onChange={ description => props.updateTemplate({ description })}
                        />
                    </SkeletonIfLoading>
                </FormGroup>
                <FormGroup
                    label="Content"
                    isRequired
                >
                    <SkeletonIfLoading isLoading={props.isLoading} height="300px">
                        <CodeEditor
                            isLineNumbersVisible
                            code={ props.template?.data ?? '' }
                            isMinimapVisible={ false }
                            onChange={ data => props.updateTemplate({ data }) }
                            height="300px"
                        />
                    </SkeletonIfLoading>
                </FormGroup>
                <FormGroup
                    label="Template parameters"
                    isRequired
                >
                    <SkeletonIfLoading isLoading={props.isLoading} height="200px">
                        <CodeEditor
                            isLineNumbersVisible
                            isMinimapVisible={ false }
                            code={ defaultPayload }
                            onChange={ setPayload }
                            height="200px"
                            isLanguageLabelVisible
                            language={ Language.json }
                        />
                    </SkeletonIfLoading>
                </FormGroup>
            </Form>
        </>
    );
};
