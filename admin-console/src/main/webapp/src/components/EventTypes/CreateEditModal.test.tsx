import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { CreateEditModal } from './CreateEditModal';

// Mock the severities hook so the component renders without a real API
vi.mock('../../services/Notifications/GetSeverities', () => ({
    useGetSeverities: () => ({ severities: [], loading: false }),
}));

describe('CreateEditModal', () => {
    const baseProps = {
        isEdit: false,
        showModal: true,
        applicationName: 'TestApp',
        isLoading: false,
        onClose: vi.fn(),
        onSubmit: vi.fn(),
    };

    it('renders empty fields when initialEventType omits name, displayName, and description', () => {
        render(
            <CreateEditModal
                { ...baseProps }
                initialEventType={{ visible: true, includedInDrawer: false }}
            />
        );

        const nameInput = screen.getByRole('textbox', { name: /^Name$/i });
        const displayNameInput = screen.getByRole('textbox', { name: /display name/i });
        const descriptionInput = screen.getByRole('textbox', { name: /description/i });

        expect(nameInput).toHaveValue('');
        expect(displayNameInput).toHaveValue('');
        expect(descriptionInput).toHaveValue('');
    });

    it('renders provided values when initialEventType includes name, displayName, and description', () => {
        render(
            <CreateEditModal
                { ...baseProps }
                initialEventType={{
                    name: 'test-event',
                    displayName: 'Test Event',
                    description: 'A test description',
                    visible: true,
                    includedInDrawer: false,
                }}
            />
        );

        const nameInput = screen.getByRole('textbox', { name: /^Name$/i });
        const displayNameInput = screen.getByRole('textbox', { name: /display name/i });
        const descriptionInput = screen.getByRole('textbox', { name: /description/i });

        expect(nameInput).toHaveValue('test-event');
        expect(displayNameInput).toHaveValue('Test Event');
        expect(descriptionInput).toHaveValue('A test description');
    });

    it('renders empty fields when no initialEventType is provided', () => {
        render(<CreateEditModal { ...baseProps } />);

        const nameInput = screen.getByRole('textbox', { name: /^Name$/i });
        const displayNameInput = screen.getByRole('textbox', { name: /display name/i });
        const descriptionInput = screen.getByRole('textbox', { name: /description/i });

        expect(nameInput).toHaveValue('');
        expect(displayNameInput).toHaveValue('');
        expect(descriptionInput).toHaveValue('');
    });
});
