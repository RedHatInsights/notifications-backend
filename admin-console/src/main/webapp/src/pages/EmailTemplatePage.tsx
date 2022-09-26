import {
    ActionGroup,
    Button,
    PageSection,
    Split,
    SplitItem,
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

    const originalTemplate = useGetTemplate(templateId);

    const handleBackClick = React.useCallback(() => {
        history.back();
    }, []);

    const newTemplate = useCreateTemplate();
    const [ template, setTemplate ] = React.useState<Partial<Template>>({
        data: defaultContentTemplate
    });

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

    return (
        <>{ isAdmin &&
            <><PageSection>
                <Split>
                    <SplitItem isFilled>
                        <Title headingLevel="h1">{ templateId ? 'Update' : 'Create'} an Email Template</Title>
                    </SplitItem>
                </Split>
            </PageSection><PageSection>
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
