import { ActionGroup, Button, Modal, ModalVariant, Spinner, TextInput } from '@patternfly/react-core';
import React from 'react';

import { BehaviorGroup } from '../../types/Notifications';

interface DeleteBehaviorGroupModalProps {
    onDelete: (systemBehaviorGroup?: BehaviorGroup) => Promise<boolean>;
    bundleName?: string;
    systemBehaviorGroupName?: string;
    eventTypeName: string;
    isOpen: boolean;
    onClose: () => void;

}
export const DeleteBehaviorGroupModal: React.FunctionComponent<DeleteBehaviorGroupModalProps> = (props) => {
    const [ errors, setErrors ] = React.useState(true);

    const onDelete = React.useCallback(async () => {
        const onDeleteImpl = props.onDelete;
        const response = await onDeleteImpl();
        if (response) {
            props.onClose();
        } else {
            alert('Could not delete system behavior group, please try again.');
        }
    }, [ props ]);

    const handleDeleteChange = (value: string, event: React.FormEvent<HTMLInputElement>) => {
        const target = event.target as HTMLInputElement;
        if (target.value !== props.systemBehaviorGroupName) {
            return setErrors(true);
        } else if (target.value === props.systemBehaviorGroupName) {
            return setErrors(false);
        }
    };

    return (
        <React.Fragment>
            <Modal variant={ ModalVariant.small } titleIconVariant="warning" isOpen={ props.isOpen }
                onClose={ props.onClose }
                title={ `Permanently delete ${ props.systemBehaviorGroupName }` }>
                { <b>{ props.systemBehaviorGroupName }</b> } {`from ${ props.bundleName ? props.bundleName :
                    <Spinner /> } will be deleted. You will no longer be able to assign this system behavior
                    group to event types. `}
                <br />
                <br />
                        Type <b>{ props.systemBehaviorGroupName }</b> to confirm:
                <br />
                <TextInput type='text' onChange={ handleDeleteChange } id='name' name="name" isRequired />
                <br />
                <br />
                <ActionGroup>
                    <Button variant='danger' type='button' isDisabled = { errors }
                        onClick={ onDelete }>Delete</Button>
                    <Button variant='link' type='button' onClick={ props.onClose }>Cancel</Button>
                </ActionGroup>
            </Modal>
        </React.Fragment>
    );
};
