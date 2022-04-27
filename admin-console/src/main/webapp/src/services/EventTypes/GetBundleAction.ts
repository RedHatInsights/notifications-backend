import { Operations } from '../../generated/OpenapiInternal';

export const getBundleAction = (bundleId: string) => {
    return Operations.InternalResourceGetBundle.actionCreator({
        bundleId
    });
};
