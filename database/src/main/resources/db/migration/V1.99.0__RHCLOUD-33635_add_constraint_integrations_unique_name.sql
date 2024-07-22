ALTER TABLE endpoints ADD CONSTRAINT endpoints_name_org_id_key UNIQUE ("name", org_id);
