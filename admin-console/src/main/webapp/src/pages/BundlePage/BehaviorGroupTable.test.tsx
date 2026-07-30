import { describe, expect, it } from 'vitest';

import { Schemas } from '../../generated/OpenapiInternal';
import { BehaviorGroupAction } from '../../types/Notifications';
import { actionsToDropdownValue, formatActionLabel } from './BehaviorGroupTable';

const makeAction = (
    endpointType: Schemas.EndpointType,
    onlyAdmins: boolean
): BehaviorGroupAction => ({
    endpoint: {
        name: 'test',
        description: '',
        type: endpointType,
        properties: {
            ignore_preferences: false,
            only_admins: onlyAdmins
        } as Schemas.SystemSubscriptionProperties
    }
});

describe('actionsToDropdownValue', () => {
    it('returns undefined for empty or null actions', () => {
        expect(actionsToDropdownValue(undefined)).toBeUndefined();
        expect(actionsToDropdownValue(null)).toBeUndefined();
        expect(actionsToDropdownValue([])).toBeUndefined();
    });

    it('maps drawer + all users to "drawer-all"', () => {
        expect(actionsToDropdownValue([ makeAction('drawer', false) ])).toBe('drawer-all');
    });

    it('maps drawer + admins to "drawer-admin"', () => {
        expect(actionsToDropdownValue([ makeAction('drawer', true) ])).toBe('drawer-admin');
    });

    it('maps email_subscription + all users to "email-all"', () => {
        expect(actionsToDropdownValue([ makeAction('email_subscription', false) ])).toBe('email-all');
    });

    it('maps email_subscription + admins to "email-admin"', () => {
        expect(actionsToDropdownValue([ makeAction('email_subscription', true) ])).toBe('email-admin');
    });

    it('returns undefined for unsupported endpoint types', () => {
        expect(actionsToDropdownValue([ makeAction('webhook', false) ])).toBeUndefined();
        expect(actionsToDropdownValue([ makeAction('camel', false) ])).toBeUndefined();
        expect(actionsToDropdownValue([ makeAction('ansible', true) ])).toBeUndefined();
    });

    it('returns undefined when endpoint has no properties', () => {
        const action: BehaviorGroupAction = {
            endpoint: {
                name: 'test',
                description: '',
                type: 'drawer'
            }
        };
        expect(actionsToDropdownValue([ action ])).toBeUndefined();
    });
});

describe('formatActionLabel', () => {
    it('returns "Drawer: All users" for drawer with only_admins=false', () => {
        expect(formatActionLabel(makeAction('drawer', false))).toBe('Drawer: All users');
    });

    it('returns "Drawer: Admins" for drawer with only_admins=true', () => {
        expect(formatActionLabel(makeAction('drawer', true))).toBe('Drawer: Admins');
    });

    it('returns "Email: All users" for email_subscription with only_admins=false', () => {
        expect(formatActionLabel(makeAction('email_subscription', false))).toBe('Email: All users');
    });

    it('returns "Email: Admins" for email_subscription with only_admins=true', () => {
        expect(formatActionLabel(makeAction('email_subscription', true))).toBe('Email: Admins');
    });

    it('returns empty string for unsupported endpoint types', () => {
        expect(formatActionLabel(makeAction('webhook', false))).toBe('');
        expect(formatActionLabel(makeAction('camel', true))).toBe('');
        expect(formatActionLabel(makeAction('ansible', false))).toBe('');
    });

    it('returns empty string when endpoint has no properties', () => {
        const action: BehaviorGroupAction = {
            endpoint: {
                name: 'test',
                description: '',
                type: 'drawer'
            }
        };
        expect(formatActionLabel(action)).toBe('');
    });
});
