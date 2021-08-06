UPDATE public.applications
    SET name = 'policies'
    WHERE name = 'Policies';

UPDATE public.event_type
    SET name = 'policy-triggered', display_name = 'Policy triggered' 
    WHERE name = 'All';