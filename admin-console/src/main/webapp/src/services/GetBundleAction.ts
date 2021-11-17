import { Operations } from '../generated/OpenapiInternal';

export const getBundleAction = (bundleId: string) => {
    return Operations.InternalServiceGetBundle.actionCreator({
        bundleId
    });
};
