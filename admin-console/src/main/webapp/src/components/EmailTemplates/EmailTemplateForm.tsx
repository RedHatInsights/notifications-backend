import * as React from 'react';
import {Template} from '../../types/Notifications';
import {Form, FormGroup, HelperText, HelperTextItem, Skeleton, SkeletonProps, TextInput} from '@patternfly/react-core';
import {CodeEditor} from '@patternfly/react-code-editor';

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

export const EmailTemplateForm: React.FunctionComponent<EmailTemplateFormProps> = props => {

    return (
        <>
            <Form>
                <FormGroup
                    label='Name'
                    isRequired
                >
                    <SkeletonIfLoading isLoading={ props.isLoading }>
                        <TextInput
                            type='text'
                            id='name'
                            name="name"
                            value={ props.template?.name ?? '' }
                            onChange={ (_event, name) => props.updateTemplate({ name })}
                        />
                    </SkeletonIfLoading>
                    <HelperText>
                        <HelperTextItem>
                            Enter a name for your template
                        </HelperTextItem>
                    </HelperText>
                </FormGroup>
                <FormGroup
                    label='Description'
                    isRequired
                >
                    <SkeletonIfLoading isLoading={props.isLoading}>
                        <TextInput
                            type='text'
                            id='description'
                            name="description"
                            value={ props.template?.description ?? '' }
                            onChange={ (_event, description) => props.updateTemplate({ description })}
                        />
                    </SkeletonIfLoading>
                    <HelperText>
                        <HelperTextItem>
                            Enter a brief description for your template
                        </HelperTextItem>
                    </HelperText>
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
            </Form>
        </>
    );
};
