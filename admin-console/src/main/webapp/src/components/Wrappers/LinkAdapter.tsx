import * as React from 'react';
import { Link } from 'react-router-dom';

type LinkAdapterProps = any & {
    href: string;
};

export const LinkAdapter: React.FunctionComponent<LinkAdapterProps> = (props) => {
    const { href, ...restProps } = props;
    return (
        <Link to={ href } { ...restProps }>{ props.children }</Link>
    );
};
