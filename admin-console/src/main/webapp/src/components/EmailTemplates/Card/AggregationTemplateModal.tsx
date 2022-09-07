import {
    ActionGroup,
    Button,
    Form,
    FormGroup,
    FormSelect,
    FormSelectOption,
    Modal,
    ModalVariant
} from '@patternfly/react-core';
import React from 'react';

import { AggregationTemplate, Template } from '../../../types/Notifications';

interface AggregationTemplateModalProps {
    showModal: boolean;
    isEdit: boolean;
    onClose: () => void;
    templates: readonly Template[] | undefined;
    initialAggregationTemplate?: Partial<AggregationTemplate>;
    onSubmit: (aggregationTemplate: AggregationTemplate) => void;
}

export const AggregationTemplateModal: React.FunctionComponent<AggregationTemplateModalProps> = (props) => {

    const [ aggregationTemplate, setAggregationTemplate ] = React.useState<Partial<AggregationTemplate>>({});

    const templateOption = [
        <FormSelectOption key='choose template' isPlaceholder label='Choose a template' />,
        ...(props.templates ? props.templates.map(template => (
            <FormSelectOption key={ template.id } label={ template.name } value={ template.id } />
        )) : [])
    ];

    const handleChange = (value: string, event: React.FormEvent<HTMLFormElement> | React.FormEvent<HTMLSelectElement>) => {
        const target = event.target as HTMLSelectElement;
        setAggregationTemplate(prev => ({ ...prev, [target.name]: target.value }));
    };

    const onSubmitLocal = React.useCallback((evt) => {
        evt.preventDefault();
        const onSubmit = props.onSubmit;
        if (aggregationTemplate.bodyTemplateId && aggregationTemplate.subjectTemplateId && aggregationTemplate.applicationId) {
            onSubmit(aggregationTemplate as AggregationTemplate);
        }
        return false;
    }, [aggregationTemplate, props.onSubmit]);

    React.useEffect(() => {
        setAggregationTemplate(props.initialAggregationTemplate ?? {});
    }, [props.initialAggregationTemplate, props.showModal]);

    return (
        <React.Fragment>
            <Modal
                isOpen={ props.showModal }
                variant={ ModalVariant.medium }
                title={ `${ props.isEdit ? 'Update' : 'Select'} your aggregation email templates` }
                onClose={ props.onClose }
            >
                <Form isHorizontal>
                    <FormGroup label='Subject template' fieldId='subject-template'>
                        <FormSelect
                            id="subject-template"
                            name="subjectTemplateId"
                            aria-label="Subject template"
                            onChange={ handleChange }
                            isRequired
                            value={ aggregationTemplate.subjectTemplateId }
                        >
                            { templateOption }
                        </FormSelect>
                    </FormGroup>
                    <FormGroup label='Body template' fieldId='body-template'>
                        <FormSelect
                            id="body-template"
                            name="bodyTemplateId"
                            aria-label="Body template"
                            onChange={ handleChange }
                            isRequired
                            value={ aggregationTemplate.bodyTemplateId }
                        >
                            { templateOption }
                        </FormSelect>
                    </FormGroup>
                    <ActionGroup>
                        <Button variant='primary' type='submit'
                            onClick={ onSubmitLocal }
                        >{ props.isEdit ? 'Update' : 'Submit' }</Button>
                        <Button variant='link' type='reset'
                            onClick={ props.onClose }>Cancel</Button>
                    </ActionGroup>
                </Form>
            </Modal>
        </React.Fragment>
    );
};
