import { useCallback, useState } from 'react';

interface SaveModalState<T> {
    isOpen: boolean;
    data?: T;
    isEdit: boolean;
}

export const useSaveModal = <T>() => {
    const [ state, setState ] = useState<SaveModalState<T>>({
        isOpen: false,
        data: undefined,
        isEdit: false
    });

    const open = useCallback((data?: T, isEdit?: boolean) => {
        setState({
            isOpen: true,
            data,
            isEdit: !!isEdit
        });
    }, [ setState ]);

    const close = useCallback(() => {
        setState({
            isOpen: false,
            data: undefined,
            isEdit: false
        });
    }, [ setState ]);

    return {
        isOpen: state.isOpen,
        template: state.data,
        isEdit: state.isEdit,
        open,
        close
    };
};
