import produce from 'immer';
import { useCallback, useEffect, useState } from 'react';
import { useClient } from 'react-fetching-library';
import { useUnmountPromise } from 'react-use';

import { Operations } from '../../generated/OpenapiInternal';
import { Bundle } from '../../types/Notifications';

export const useBundles = () => {
    const client = useClient();
    const mounted = useUnmountPromise();

    const [ bundles, setBundles ] = useState<ReadonlyArray<Bundle>>([]);

    const [ isLoading, setLoading ] = useState<boolean>();

    const query = useCallback(async () => {
        const cQuery = client.query;
        setLoading(true);

        const bundleResponse = await cQuery(Operations.InternalServiceGetBundles.actionCreator());

        if (bundleResponse.payload?.status === 200) {

            const bundles: ReadonlyArray<Bundle> = bundleResponse.payload.value.map(bundleResponse => ({
                id: bundleResponse.id ?? '',
                displayName: bundleResponse.display_name,
                applications: []
            }));

            const applicationsPromises = [];
            for (const bundle of bundleResponse.payload.value) {
                if (bundle.id) {
                    applicationsPromises.push(cQuery(Operations.InternalServiceGetApplications.actionCreator({
                        bundleId: bundle.id
                    })));
                }
            }

            const applicationResponses = await Promise.all(applicationsPromises);

            const reducedBundles = applicationResponses.map(r => r.payload).reduce((bundles, applications) => produce(bundles, draftBundles => {
                if (applications?.status === 200) {
                    const draftBundle = draftBundles.find(b => applications.value.length > 0 && b.id === applications.value[0].bundle_id);
                    if (draftBundle) {
                        draftBundle.applications = applications.value.map(a => ({
                            id: a.id ?? '',
                            displayName: a.display_name,
                            name: a.name,
                            bundleId: a.bundle_id
                        }));
                    }
                }
            }), bundles);

            (await mounted(Promise.resolve(() => setBundles((reducedBundles)))))();
        }

        setLoading(false);
    }, [ client.query, mounted ]);

    useEffect(() => {
        query();
    }, [ query ]);

    return {
        bundles,
        isLoading
    };
};
