import {
    Alert,
    AlertVariant,
    Button,
    Form,
    FormGroup,
    HelperText,
    HelperTextItem,
    Modal,
    ModalBody,
    ModalFooter,
    ModalHeader,
    ModalVariant,
    PageSection,
    Stack,
    StackItem,
    TextInput,
    Title
} from '@patternfly/react-core';
import * as React from 'react';
import { useClient, useParameterizedQuery } from 'react-fetching-library';

import { Operations, Schemas } from '../generated/OpenapiInternal';

interface DailyDigestFormState {
    application_name: string;
    bundle_name: string;
    org_id: string;
    start: string;
    end: string;
}

type RequestResult = {
    type: 'success';
    message: string;
} | null;

type ConstraintViolation = {
    field?: string;
    message?: string;
};

type ErrorPayload = {
    title?: string;
    description?: string;
    violations?: ConstraintViolation[];
};

type TriggerDailyDigestRequestWithIds = Schemas.TriggerDailyDigestRequest & {
    bundle_id: string;
    application_id: string;
};

const initialFormState: DailyDigestFormState = {
    application_name: '',
    bundle_name: '',
    org_id: '',
    start: '',
    end: ''
};

const friendlyFieldLabels: Record<string, string> = {
    applicationId: 'application',
    bundleId: 'bundle',
    application_name: 'application name',
    bundle_name: 'bundle name',
    org_id: 'organization ID',
    start: 'start date',
    end: 'end date'
};

const toFriendlyViolationMessage = (violation: ConstraintViolation): string | null => {
    const rawField = typeof violation.field === 'string' ? violation.field.trim() : '';
    const rawMessage = typeof violation.message === 'string' ? violation.message.trim() : '';
    const fieldKey = rawField.split('.').pop() ?? rawField;
    const fieldLabel = friendlyFieldLabels[fieldKey] ?? fieldKey;

    if (fieldLabel === '' && rawMessage === '') {
        return null;
    }

    if (rawMessage === 'must not be null' || rawMessage === 'must not be blank') {
        return `${fieldLabel} is required`;
    }

    if (fieldLabel !== '' && rawMessage !== '') {
        return `${fieldLabel}: ${rawMessage}`;
    }

    return rawMessage || fieldLabel || null;
};

const formatErrorPayload = (value: unknown): string | null => {
    if (typeof value !== 'object' || value === null) {
        return null;
    }

    const payload = value as ErrorPayload;
    const title = typeof payload.title === 'string' ? payload.title.trim() : '';
    const description = typeof payload.description === 'string' ? payload.description.trim() : '';
    const messageParts: string[] = [ 'We could not submit the daily digest request.' ];

    if (Array.isArray(payload.violations) && payload.violations.length > 0) {
        const violations = payload.violations
            .map(toFriendlyViolationMessage)
            .filter(Boolean);

        if (violations.length > 0) {
            messageParts.push(`Please check the following fields: ${violations.join('; ')}.`);
        }
    }

    if (description !== '') {
        messageParts.push(description);
    }

    if (title !== '' && title !== 'Constraint Violation') {
        messageParts.push(`Details: ${title}.`);
    }

    return messageParts.length > 1 || description !== '' || title !== '' ? messageParts.join(' ') : null;
};

const validateDateFormat = (dateString: string): boolean => {
    if (dateString.trim() === '') {
        return true;
    }

    const iso8601Regex = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})\.(\d{3})$/;
    const match = dateString.match(iso8601Regex);
    if (!match) {
        return false;
    }

    const [ , yearPart, monthPart, dayPart, hourPart, minutePart, secondPart, millisecondPart ] = match;
    const year = Number(yearPart);
    const month = Number(monthPart);
    const day = Number(dayPart);
    const hour = Number(hourPart);
    const minute = Number(minutePart);
    const second = Number(secondPart);
    const millisecond = Number(millisecondPart);

    const date = new Date(Date.UTC(year, month - 1, day, hour, minute, second, millisecond));
    return date.getUTCFullYear() === year
        && date.getUTCMonth() === month - 1
        && date.getUTCDate() === day
        && date.getUTCHours() === hour
        && date.getUTCMinutes() === minute
        && date.getUTCSeconds() === second
        && date.getUTCMilliseconds() === millisecond;
};

const validateDateRange = (start: string, end: string): { start?: string; end?: string } => {
    const errors: { start?: string; end?: string } = {};

    if (start.trim() !== '' && !validateDateFormat(start)) {
        errors.start = 'Invalid date format. Use: YYYY-MM-DDTHH:mm:ss.SSS';
    }

    if (end.trim() !== '' && !validateDateFormat(end)) {
        errors.end = 'Invalid date format. Use: YYYY-MM-DDTHH:mm:ss.SSS';
    }

    if (!errors.start && !errors.end && start.trim() !== '' && end.trim() !== '') {
        const startDate = new Date(start);
        const endDate = new Date(end);
        if (startDate >= endDate) {
            errors.end = 'End date must be after start date';
        }
    }

    return errors;
};

export const DailyDigestEmailPage: React.FunctionComponent = () => {
    const client = useClient();
    const triggerDailyDigest = useParameterizedQuery(Operations.InternalResourceTriggerDailyDigest.actionCreator);
    const [ isModalOpen, setModalOpen ] = React.useState(false);
    const [ form, setForm ] = React.useState<DailyDigestFormState>(initialFormState);
    const [ requestResult, setRequestResult ] = React.useState<RequestResult>(null);
    const [ modalError, setModalError ] = React.useState<string | null>(null);
    const [ isResolvingIds, setResolvingIds ] = React.useState(false);
    const abortControllerRef = React.useRef<AbortController | null>(null);

    const dateValidationErrors = React.useMemo(() => {
        return validateDateRange(form.start, form.end);
    }, [ form.start, form.end ]);

    const hasValidationErrors = Object.keys(dateValidationErrors).length > 0;

    const isSubmitDisabled = form.application_name.trim() === ''
        || form.bundle_name.trim() === ''
        || form.org_id.trim() === ''
        || form.start.trim() === ''
        || hasValidationErrors
        || isResolvingIds
        || triggerDailyDigest.loading;

    React.useEffect(() => {
        return () => {
            if (abortControllerRef.current) {
                abortControllerRef.current.abort();
            }
        };
    }, [ ]);

    const updateFormField = React.useCallback((field: keyof DailyDigestFormState, value: string) => {
        setForm(prev => ({
            ...prev,
            [field]: value
        }));
    }, [ ]);

    const openModal = React.useCallback(() => {
        setModalError(null);
        setRequestResult(null);
        setModalOpen(true);
    }, [ ]);

    const closeModal = React.useCallback(() => {
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
            abortControllerRef.current = null;
        }

        setModalOpen(false);
        setForm(initialFormState);
        setModalError(null);
        setResolvingIds(false);
    }, [ ]);

    const submit = React.useCallback(async() => {
        abortControllerRef.current = new AbortController();
        const signal = abortControllerRef.current.signal;

        setModalError(null);
        setRequestResult(null);
        setResolvingIds(true);

        try {
            const bundleName = form.bundle_name.trim();
            const applicationName = form.application_name.trim();
            const bundlesResponse = await client.query(Operations.InternalResourceGetBundles.actionCreator());

            if (signal.aborted) {
                return;
            }

            if (bundlesResponse.payload?.status !== 200) {
                setModalError('We could not load bundles right now. Please try again in a moment.');
                return;
            }

            const matchedBundle = bundlesResponse.payload.value.find(
                bundle => bundle.name.trim().toLowerCase() === bundleName.toLowerCase()
            );
            if (!matchedBundle?.id) {
                setModalError(`We could not find a bundle named "${bundleName}". Please verify the bundle name and try again.`);
                return;
            }

            const applicationsResponse = await client.query(Operations.InternalResourceGetApplications.actionCreator({
                bundleId: matchedBundle.id
            }));

            if (signal.aborted) {
                return;
            }

            if (applicationsResponse.payload?.status !== 200) {
                setModalError('We could not load applications for the selected bundle. Please try again in a moment.');
                return;
            }

            const matchedApplication = applicationsResponse.payload.value.find(
                application => application.name.trim().toLowerCase() === applicationName.toLowerCase()
            );
            if (!matchedApplication?.id) {
                setModalError(
                    `We could not find an application named "${applicationName}" in bundle "${bundleName}".`
                );
                return;
            }

            const requestBody: TriggerDailyDigestRequestWithIds = {
                application_name: applicationName,
                bundle_name: bundleName,
                bundle_id: matchedBundle.id,
                application_id: matchedApplication.id,
                org_id: form.org_id.trim(),
                start: form.start.trim() === '' ? undefined : form.start.trim(),
                end: form.end.trim() === '' ? undefined : form.end.trim()
            };

            const response = await triggerDailyDigest.query({
                body: requestBody
            });

            if (signal.aborted) {
                return;
            }

            if (response.payload?.status === 201) {
                setRequestResult({
                    type: 'success',
                    message: 'Daily digest trigger request sent successfully.'
                });
                setModalOpen(false);
                setForm(initialFormState);
                return;
            }

            if (response.payload?.status === 401 || response.payload?.status === 403) {
                setModalError('You do not have permission to trigger daily digest emails in this environment.');
                return;
            }

            const detailedError = formatErrorPayload(response.payload?.value);
            setModalError(detailedError ?? 'We could not trigger the daily digest email. Please verify your values and try again.');
        } catch {
            if (!signal.aborted) {
                setModalError('An unexpected error occurred. Please try again.');
            }
        } finally {
            if (!signal.aborted) {
                setResolvingIds(false);
            }
        }
    }, [ form.application_name, form.bundle_name, form.org_id, form.start, form.end, client.query, triggerDailyDigest.query ]);

    return (
        <>
            <PageSection>
                <Stack hasGutter>
                    <StackItem>
                        <Alert
                            isInline
                            variant={ AlertVariant.warning }
                            title="Please, use this tool with caution. Generating a daily digest is an expensive operation that may end up affecting the service."
                        />
                    </StackItem>
                    <StackItem>
                        <Title headingLevel="h1">Trigger a daily digest email</Title>
                    </StackItem>
                    <StackItem>
                        <span>
                            This utility sends a daily digest trigger for a specific application, bundle and organization ID.
                            You can optionally provide a date range to generate a digest for a specific set of events.
                        </span>
                    </StackItem>
                    <StackItem>
                        <Button onClick={ openModal }>Generate</Button>
                    </StackItem>
                </Stack>
            </PageSection>
            <Modal
                variant={ ModalVariant.medium }
                isOpen={ isModalOpen }
                onClose={ closeModal }
            >
                <ModalHeader title="Trigger a daily digest email" />
                <ModalBody>
                    <Stack hasGutter>
                        { modalError && (
                            <StackItem>
                                <Alert
                                    isInline
                                    variant={ AlertVariant.danger }
                                    title={ modalError }
                                />
                            </StackItem>
                        ) }
                        <StackItem>
                            <Form isHorizontal>
                                <FormGroup label="Application's name" fieldId="application-name" isRequired>
                                    <TextInput
                                        type="text"
                                        id="application-name"
                                        name="application-name"
                                        value={ form.application_name }
                                        onChange={ (_event, value) => updateFormField('application_name', value) }
                                        placeholder="policies"
                                        isRequired
                                    />
                                    <HelperText>
                                        <HelperTextItem>The name of the application to trigger the daily digest for</HelperTextItem>
                                    </HelperText>
                                </FormGroup>
                                <FormGroup label="Bundle's name" fieldId="bundle-name" isRequired>
                                    <TextInput
                                        type="text"
                                        id="bundle-name"
                                        name="bundle-name"
                                        value={ form.bundle_name }
                                        onChange={ (_event, value) => updateFormField('bundle_name', value) }
                                        placeholder="rhel"
                                        isRequired
                                    />
                                    <HelperText>
                                        <HelperTextItem>The name of the bundle to trigger the daily digest for</HelperTextItem>
                                    </HelperText>
                                </FormGroup>
                                <FormGroup label="Organization ID" fieldId="org-id" isRequired>
                                    <TextInput
                                        type="text"
                                        id="org-id"
                                        name="org-id"
                                        value={ form.org_id }
                                        onChange={ (_event, value) => updateFormField('org_id', value) }
                                        placeholder="1371982"
                                        isRequired
                                    />
                                    <HelperText>
                                        <HelperTextItem>The organization&apos;s ID</HelperTextItem>
                                    </HelperText>
                                </FormGroup>
                                <FormGroup label="Initial date" fieldId="start-date" isRequired>
                                    <TextInput
                                        type="text"
                                        id="start-date"
                                        name="start-date"
                                        value={ form.start }
                                        onChange={ (_event, value) => updateFormField('start', value) }
                                        placeholder="2021-01-21T00:00:00.000"
                                        validated={ dateValidationErrors.start ? 'error' : 'default' }
                                        isRequired
                                    />
                                    <HelperText>
                                        <HelperTextItem>The initial date to filter the events by</HelperTextItem>
                                        { dateValidationErrors.start && (
                                            <HelperTextItem variant="error">{ dateValidationErrors.start }</HelperTextItem>
                                        ) }
                                    </HelperText>
                                </FormGroup>
                                <FormGroup label="Final date" fieldId="end-date">
                                    <TextInput
                                        type="text"
                                        id="end-date"
                                        name="end-date"
                                        value={ form.end }
                                        onChange={ (_event, value) => updateFormField('end', value) }
                                        placeholder="2021-01-23T12:30:00.000"
                                        validated={ dateValidationErrors.end ? 'error' : 'default' }
                                    />
                                    <HelperText>
                                        <HelperTextItem>The final date to filter the events by (optional)</HelperTextItem>
                                        { dateValidationErrors.end && (
                                            <HelperTextItem variant="error">{ dateValidationErrors.end }</HelperTextItem>
                                        ) }
                                    </HelperText>
                                </FormGroup>
                            </Form>
                        </StackItem>
                    </Stack>
                </ModalBody>
                <ModalFooter>
                    <Button
                        variant="primary"
                        type="submit"
                        isDisabled={ isSubmitDisabled }
                        isLoading={ triggerDailyDigest.loading || isResolvingIds }
                        onClick={ submit }
                    >
                        Submit
                    </Button>
                    <Button
                        variant="link"
                        type="reset"
                        onClick={ closeModal }
                    >
                        Cancel
                    </Button>
                </ModalFooter>
            </Modal>
            { requestResult && (
                <PageSection>
                    <Alert
                        isInline
                        variant={ AlertVariant.success }
                        title={ requestResult.message }
                    />
                </PageSection>
            ) }
        </>
    );
};