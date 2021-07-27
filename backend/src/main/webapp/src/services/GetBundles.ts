import produce from 'immer';
import { useCallback, useEffect, useState } from 'react';
import { useClient } from 'react-fetching-library';

// Todo: add types
export const useBundles = () => {
    const client = useClient();
    const [ bundles, setBundles ] = useState<Array<any>>([]);

    const [ isLoading, setLoading ] = useState<boolean>();

    const query = useCallback(async () => {
        const cQuery = client.query;
        setLoading(true);

        const bundleResponse = await cQuery({
            endpoint: './bundles',
            method: 'GET'
        });

        if (bundleResponse.status === 200) {
            const applicationsPromises = [];
            for (const bundle of bundleResponse.payload) {
                applicationsPromises.push(cQuery({
                    method: 'GET',
                    endpoint: `./bundles/${bundle.id}/applications`
                }));
            }

            const applicationResponses = await Promise.all(applicationsPromises);

            const reducedBundles = applicationResponses.map(r => r.payload).reduce((bundles, applications) => produce(bundles, (draftBundle: any) => {
                if (applications.length > 0) {
                    draftBundle.find((b: any) => b.id === applications[0].bundle_id).applications = applications;
                }
            }), bundleResponse.payload);

            setBundles(reducedBundles);
        }

        setLoading(false);
    }, [ client.query ]);

    useEffect(() => {
        query();
    }, [ query ]);

    return {
        bundles,
        isLoading
    };
};
