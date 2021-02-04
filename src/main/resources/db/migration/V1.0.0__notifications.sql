--
-- PostgreSQL database dump
--

-- Dumped from database version 11.1
-- Dumped by pg_dump version 12.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

-- COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


SET default_tablespace = '';


--
-- Name: applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.applications (
    id uuid DEFAULT public.gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    description character varying,
    created timestamp with time zone DEFAULT now() NOT NULL,
    updated timestamp with time zone
);


--
-- Name: email_aggregation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_aggregation (
    id integer NOT NULL,
    account_id character varying(50) NOT NULL,
    insight_id character varying(50) NOT NULL,
    created timestamp with time zone DEFAULT now() NOT NULL,
    payload jsonb NOT NULL
);


--
-- Name: email_aggregation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_aggregation_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_aggregation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_aggregation_id_seq OWNED BY public.email_aggregation.id;


--
-- Name: endpoint_email_subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.endpoint_email_subscriptions (
    account_id character varying(50) NOT NULL,
    user_id character varying(50) NOT NULL,
    event_type character varying(50) NOT NULL
);


--
-- Name: endpoint_webhooks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.endpoint_webhooks (
    id integer NOT NULL,
    endpoint_id uuid NOT NULL,
    url character varying NOT NULL,
    method character varying(10) NOT NULL,
    disable_ssl_verification boolean NOT NULL,
    secret_token character varying(255)
);


--
-- Name: endpoint_webhooks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.endpoint_webhooks_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: endpoint_webhooks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.endpoint_webhooks_id_seq OWNED BY public.endpoint_webhooks.id;


--
-- Name: endpoints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.endpoints (
    id uuid DEFAULT public.gen_random_uuid() NOT NULL,
    account_id character varying(50) NOT NULL,
    endpoint_type integer NOT NULL,
    enabled boolean NOT NULL,
    name character varying(255) NOT NULL,
    description character varying,
    created timestamp with time zone DEFAULT now() NOT NULL,
    updated timestamp with time zone
);


--
-- Name: notification_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_history (
    id integer NOT NULL,
    account_id character varying(50) NOT NULL,
    endpoint_id uuid NOT NULL,
    created timestamp with time zone DEFAULT now() NOT NULL,
    invocation_time integer NOT NULL,
    invocation_result boolean NOT NULL,
    details jsonb
);


--
-- Name: notification_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_history_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_history_id_seq OWNED BY public.notification_history.id;


--
-- Name: email_aggregation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_aggregation ALTER COLUMN id SET DEFAULT nextval('public.email_aggregation_id_seq'::regclass);


--
-- Name: endpoint_webhooks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.endpoint_webhooks ALTER COLUMN id SET DEFAULT nextval('public.endpoint_webhooks_id_seq'::regclass);


--
-- Name: notification_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_history ALTER COLUMN id SET DEFAULT nextval('public.notification_history_id_seq'::regclass);


--
-- Name: applications applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.applications
    ADD CONSTRAINT applications_pkey PRIMARY KEY (id);


--
-- Name: email_aggregation email_aggregation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_aggregation
    ADD CONSTRAINT email_aggregation_pkey PRIMARY KEY (id);


--
-- Name: endpoint_email_subscriptions endpoint_email_subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.endpoint_email_subscriptions
    ADD CONSTRAINT endpoint_email_subscriptions_pkey PRIMARY KEY (account_id, user_id, event_type);


--
-- Name: endpoint_webhooks endpoint_webhooks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.endpoint_webhooks
    ADD CONSTRAINT endpoint_webhooks_pkey PRIMARY KEY (id);


--
-- Name: endpoints endpoints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.endpoints
    ADD CONSTRAINT endpoints_pkey PRIMARY KEY (id);


--
-- Name: notification_history notification_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_history
    ADD CONSTRAINT notification_history_pkey PRIMARY KEY (id);


--
-- Name: IX_account_endpoint_search; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IX_account_endpoint_search" ON public.notification_history USING btree (account_id, endpoint_id);


--
-- Name: IX_time_search_account_mails; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "IX_time_search_account_mails" ON public.email_aggregation USING btree (account_id, created);


--
-- Name: ix_endpoint_webhooks_endpoint_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_endpoint_webhooks_endpoint_id ON public.endpoint_webhooks USING btree (endpoint_id);


--
-- Name: ix_endpoints_account_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_endpoints_account_id ON public.endpoints USING btree (account_id);


--
-- Name: endpoint_webhooks endpoint_webhooks_endpoint_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.endpoint_webhooks
    ADD CONSTRAINT endpoint_webhooks_endpoint_id_fkey FOREIGN KEY (endpoint_id) REFERENCES public.endpoints(id)
        ON UPDATE CASCADE
        ON DELETE CASCADE;


--
-- Name: notification_history notification_history_endpoint_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_history
    ADD CONSTRAINT notification_history_endpoint_id_fkey FOREIGN KEY (endpoint_id) REFERENCES public.endpoints(id)
        ON UPDATE CASCADE
        ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

