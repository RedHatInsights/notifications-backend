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

import { InstantTemplate, Template } from '../../types/Notifications';

interface InstantTemplateModalProps {
    showModal: boolean;
    isEdit: boolean;
    onClose: () => void;
    templates: readonly Template[] | undefined;
    initialInstantTemplate?: Partial<InstantTemplate>;
    onSubmit: (instantTemplate: InstantTemplate) => void;
}

export const InstantTemplateModal: React.FunctionComponent<InstantTemplateModalProps> = (props) => {

    const [ instantTemplate, setInstantTemplate ] = React.useState<Partial<InstantTemplate>>({});

    const templateOption = [
        <FormSelectOption key='choose template' isPlaceholder label='Choose a template' />,
        ...(props.templates ? props.templates.map(template => (
            <FormSelectOption key={ template.id } label={ template.name } value={ template.id } />
        )) : [])
    ];

    const handleChange = (value: string, event: React.FormEvent<HTMLFormElement> | React.FormEvent<HTMLSelectElement>) => {
        const target = event.target as HTMLSelectElement;
        setInstantTemplate(prev => ({ ...prev, [target.name]: target.value }));
    };

    const onSubmitLocal = React.useCallback((evt) => {
        evt.preventDefault();
        if (instantTemplate.bodyTemplateId && instantTemplate.subjectTemplateId && instantTemplate.eventTypeId) {
            props.onSubmit(instantTemplate as InstantTemplate);
        }
        return false;
    }, [ instantTemplate, props ]);

    React.useEffect(() => {
        setInstantTemplate(props.initialInstantTemplate ?? {});
    }, [ props.initialInstantTemplate, props.showModal ]);

    return (
        <React.Fragment>
            <Modal
                variant={ ModalVariant.medium }
                title={ `${ props.isEdit ? 'Update' : 'Select'} your instant email templates` }
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
                            value={ instantTemplate.subjectTemplateId }
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
                            value={ instantTemplate.bodyTemplateId }
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
