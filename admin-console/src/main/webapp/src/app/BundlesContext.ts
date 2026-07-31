import * as React from 'react';
import { useContext } from 'react';

export interface BundlesContext {
    refreshBundles: () => void;
}

export const BundlesContext = React.createContext<BundlesContext>({
    refreshBundles: () => {
        // noop by default
    }
});

export const useBundlesRefresh = () => {
    return useContext(BundlesContext);
};
