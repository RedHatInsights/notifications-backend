import * as React from 'react';
import { useParameterizedQuery } from 'react-fetching-library';

import { Operations } from '../generated/OpenapiInternal';
import { Validator } from '../components/Validator/Validator';

export const MessageValidatorPage: React.FunctionComponent = () => {
    const validateService = useParameterizedQuery(Operations.ValidationResourceValidateMessage.actionCreator);

    const validate = React.useCallback(async (jsonMessage: string) => {
        const query = validateService.query;
        const response = await query({
            body: jsonMessage
        });

        if (response.payload?.status === 200) {
            return {};
        } else if (response.payload?.status === 400) {
            return response.payload.value.errors;
        }

        return {};
    }, [validateService.query]);

    return <Validator validate={validate} />;
};
