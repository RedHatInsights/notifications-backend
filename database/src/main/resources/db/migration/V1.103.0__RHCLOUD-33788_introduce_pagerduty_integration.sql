--
-- Name: pagerduty_properties; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE pagerduty_properties (
    id uuid PRIMARY KEY
        CONSTRAINT fk_pagerduty_properties_endpoint_id
            REFERENCES endpoints(id)
            ON DELETE CASCADE,
    severity varchar(10) NOT NULL,
    secret_token_id bigint
);

COMMENT ON COLUMN pagerduty_properties.secret_token_id IS 'the ID of the secret token data that is stored in sources';