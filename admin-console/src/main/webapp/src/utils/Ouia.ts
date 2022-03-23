export interface OuiaComponentProps {
    ouiaId?: string;
    ouiaSafe?: boolean;
}

export const withoutOuiaProps = <T extends OuiaComponentProps>(props: T): Omit<T, 'ouiaId' | 'ouiaSafe'> => {
    const { ouiaId, ouiaSafe, ...rest } = props;

    return rest;
};

export const ouiaIdConcat = (ouiaIdParent: string | undefined, ouiaId: string) => {
    if (ouiaIdParent) {
        return `${ouiaIdParent}.${ouiaId}`;
    }

    return ouiaId;
};

