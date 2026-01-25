import { useQuery } from 'react-fetching-library';
import { useMemo } from 'react';

const getSeveritiesAction = () => ({
    method: 'GET' as const,
    endpoint: './severities'
});

export const useGetSeverities = () => {
    const query = useQuery(getSeveritiesAction());

    return useMemo(() => {
        if (query.payload && !query.error) {
            return {
                ...query,
                severities: query.payload as string[]
            };
        }

        return {
            ...query,
            severities: []
        };
    }, [ query ]);
};
