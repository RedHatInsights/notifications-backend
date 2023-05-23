CREATE TABLE public.drawer_notification (
    id UUID NOT NULL,
    user_id varchar(50) NOT NULL,
    org_id text NOT NULL,
    event_id uuid NOT NULL,
    created timestamp NOT NULL,
    read boolean NOT NULL default false,
    CONSTRAINT pk_drawer_notification PRIMARY KEY (id),
    CONSTRAINT uq_drawer_notification UNIQUE (org_id, user_id, event_id)
);

-- public.drawer_notifications foreign keys
ALTER TABLE public.drawer_notification ADD CONSTRAINT fk_drawer_notification_event_id FOREIGN KEY (event_id) REFERENCES public.event(id) ON DELETE CASCADE;
