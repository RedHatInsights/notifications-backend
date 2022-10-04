import produce, { original } from 'immer';
import { useEffect, useState } from 'react';
import { useClient } from 'react-fetching-library';

import {
    Operations
} from '../../generated/OpenapiInternal';
import { useApplicationTypes as useGetApplicationTypes } from '../../services/EventTypes/GetApplication';
import {AggregationCardRow, AggregationTemplate } from '../../types/Notifications';

interface ApplicationController {
    payload: any;
    data: ReadonlyArray<AggregationCardRow> | undefined;
    loading: boolean;
    reload: () => void;
    error: any;
}

export const useApplicationTypes = (applicationId: string): ApplicationController => {
    const [ aggregationRows, setAggregationRows ] = useState<ReadonlyArray<AggregationCardRow>>();
    const applicationTypesQuery = useGetApplicationTypes(applicationId);

    const { query } = useClient();

    useEffect(() => {
        setAggregationRows(undefined);
    }, [ applicationId ]);

    useEffect(() => {
        if (applicationTypesQuery.payload?.status === 200) {
            const application = {
                ...applicationTypesQuery.payload.value,
                aggregationEmail: {
                    isLoading: true
                }
            };

            setAggregationRows(aggregationRows);

            if (application) {
                let row;
                query(Operations.TemplateResourceGetAggregationEmailTemplatesByApplication.actionCreator({
                    appId: application.id ?? ''
                }))
                .then(result => {
                    if (result.payload?.status === 200 || result.payload?.status === 401) {
                        const value: Partial<AggregationTemplate> = result.payload.status === 200 ? {
                            id: result.payload.value ?? undefined,
                            bodyTemplateId: result.payload.value ?? undefined,
                            subjectTemplateId: result.payload.value ?? undefined,
                            applicationId: result.payload.value ?? application.id

                        } : {
                            applicationId: application.id
                        };
                        setAggregationRows(produce(draft => {
                            const originalValue = original(draft);
                            if (draft && originalValue) {
                                const index = originalValue.findIndex(r => r.id === row.id);
                                if (index !== -1) {
                                    draft[index].aggregationEmail = {
                                        isLoading: false,
                                        ...value
                                    };
                                }
                            }
                        }));
                    }
                });
            }
        }
    }, [query, applicationTypesQuery.payload?.status, applicationTypesQuery.payload?.value, aggregationRows, applicationTypesQuery.payload]);

    return {
        payload: aggregationRows,
        data: aggregationRows,
        loading: applicationTypesQuery.loading,
        reload: applicationTypesQuery.query,
        error: applicationTypesQuery.errorObject
    };
};
