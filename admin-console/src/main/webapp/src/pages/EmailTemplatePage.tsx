import {
    ActionGroup,
    Button,
    HelperText,
    HelperTextItem,
    PageSection,
    Spinner,
    Split,
    SplitItem,
    Stack,
    StackItem,
    Title
} from '@patternfly/react-core';
import * as React from 'react';
import { useParams } from 'react-router-dom';
import { useUserPermissions } from '../app/PermissionContext';
import { useCreateTemplate } from '../services/EmailTemplates/CreateTemplate';
import { Template } from '../types/Notifications';
import { useGetTemplate } from '../services/EmailTemplates/GetTemplate';
import { EmailTemplateForm } from '../components/EmailTemplates/EmailTemplateForm';
import { useEffect } from 'react';
import { useRenderEmailRequest } from '../services/RenderEmailRequest';

const defaultContentTemplate = `
Important email to {user.firstName} from MyCoolApp!

<div>Hello {user.firstName} {user.lastName},</div>
<div>We have some important news for you, MyApp has a notification for you</div>
<div>As a reminder, current user: {user.username}: is active? {user.isActive}; is admin? {user.isAdmin}</div>
<div>
    System with name <strong>{action.context.display_name}</strong> (<strong>{action.context.inventory_id}</strong>) 
    did a check in at {action.context.system_check_in.toUtcFormat()}. 
    It was about {action.context.system_check_in.toTimeAgo()}
</div>
<div>This is a loop:</div>
{#if action.events.size() > 0}
<ul>
    {#each action.events}
        <li>
            <a href="http://google.com?q={it.payload.my_id}" target="_blank">{it.payload.my_name}</a>
        </li>
    {/each}
</ul>
<div>Have a nice day!</div>
{/if}
`.trimLeft();

type RenderedTemplateProps = {
    isLoading: true;
} | {
   isLoading: false;
   succeeded: true;
   subject: string;
   body: string;
} | {
    isLoading: false;
    succeeded: false;
    error: string;
};

const RenderedTemplate: React.FunctionComponent<RenderedTemplateProps> = props => {
    if (props.isLoading) {
        return <Spinner />;
    }

    if (props.succeeded) {
        return (
            <>
                <StackItem>
                    <strong>Content:</strong>
                </StackItem>
                <StackItem>
                    <iframe width="100%" srcDoc={ props.body } />
                </StackItem>
            </>
        );
    }

    return (
        <StackItem>
            <HelperText>
                <HelperTextItem variant="error">{ props.error }</HelperTextItem>
            </HelperText>
        </StackItem>
    );
};

type EmailPageParams = {
    templateId: string;
}

type NewTemplate = Omit<Template, 'id'> & Partial<Pick<Template, 'id'>>;

const isNewTemplate = (partialTemplate: Partial<Template>): partialTemplate is NewTemplate => {
    return !!partialTemplate.name && !!partialTemplate.description && !!partialTemplate.data;
};

export const EmailTemplatePage: React.FunctionComponent = () => {
    const { isAdmin } = useUserPermissions();
    const { templateId } = useParams<EmailPageParams>();
    const emailTemplate = useRenderEmailRequest();

    const originalTemplate = useGetTemplate(templateId);

    const handleBackClick = React.useCallback(() => {
        history.back();
    }, []);

    const newTemplate = useCreateTemplate();
    const [ template, setTemplate ] = React.useState<Partial<Template>>({
        data: defaultContentTemplate
    });

    React.useEffect(() => {
        const mutate = emailTemplate.mutate;
        mutate({
            subject: template.name ?? '',
            body: template.data ?? '',
            payload: template.data ?? ''
        });
        // We only want to activate this once
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ ]);

    const updateTemplate = (updateTemplate: Partial<Template>) => {
        setTemplate(prev => ({
            ...prev,
            ...updateTemplate
        }));
    };

    const handleSave = () => {
        if (template && isNewTemplate(template)) {
            const mutate = newTemplate.mutate;
            mutate({
                id: template.id ?? undefined,
                data: template.data,
                name: template.name,
                description: template.description
            }).then(() => {
                handleBackClick();
            });
        }
    };

    useEffect(() => {
        if (templateId && !originalTemplate.loading) {
            if (originalTemplate.payload?.status === 200) {
                setTemplate({
                    ...originalTemplate.payload.value,
                    id: originalTemplate.payload.value.id ?? undefined
                });
            }
        }
    }, [templateId, originalTemplate.loading, originalTemplate.payload]);

    const onRender = React.useCallback(() => {
        const mutate = emailTemplate.mutate;
        mutate({
            subject: template.name ?? '' ,
            body: template.data ?? '',
            payload: template.data ?? ''
        });
    }, [emailTemplate.mutate, template.data, template.name]);

    let renderedProps: RenderedTemplateProps;

    if (emailTemplate.loading) {
        renderedProps = {
            isLoading: true
        };
    } else if (emailTemplate.payload?.status === 200) {
        renderedProps = {
            isLoading: false,
            succeeded: true,
            subject: emailTemplate.payload.value.subject ?? '',
            body: emailTemplate.payload.value.body ?? ''
        };
    } else if (emailTemplate.payload?.status === 400) {
        renderedProps = {
            isLoading: false,
            succeeded: false,
            error: emailTemplate.payload.value.message ?? 'Unknown error'
        };
    } else {
        renderedProps = {
            isLoading: false,
            succeeded: false,
            error: 'Unknown error'
        };
    }

    return (
        <>{ isAdmin &&
            <><PageSection>
                <Split>
                    <SplitItem isFilled>
                        <Title headingLevel="h1">{ templateId ? 'Update' : 'Create'} an Email Template</Title>
                    </SplitItem>
                    <SplitItem>
                        <Button onClick={ onRender }>Render</Button>
                    </SplitItem>
                </Split>
            </PageSection>
            <PageSection>
                <Stack>
                    <StackItem>
                        <Title headingLevel="h2">Result</Title>
                    </StackItem>
                    <RenderedTemplate { ...renderedProps }  />
                </Stack>
            </PageSection>
            <PageSection>
                <EmailTemplateForm
                    isLoading={ originalTemplate.loading }
                    template={template}
                    updateTemplate={ updateTemplate }
                />
            </PageSection>
            <PageSection>
                <ActionGroup>
                    <Split hasGutter>
                        <SplitItem>
                            <Button
                                variant="primary"
                                onClick={ handleSave }
                                isDisabled={!isNewTemplate(template)}
                            >
                                Save
                            </Button>
                        </SplitItem>
                        <SplitItem>
                            <Button variant='secondary' onClick={ handleBackClick }>
                                Back
                            </Button>
                        </SplitItem>
                    </Split>
                </ActionGroup>
            </PageSection>
            </>
        }
        </>
    );
};
