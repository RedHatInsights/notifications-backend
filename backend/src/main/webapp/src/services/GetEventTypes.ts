import { useQuery } from 'react-fetching-library';

export const useEventTypes = (applicationId: string) => {
    return useQuery({
        method: 'GET',
        endpoint: `./applications/${applicationId}/eventTypes`
    });
};
