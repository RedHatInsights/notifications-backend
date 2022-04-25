-- The following field can be safely wiped on stage and prod.
-- Prod does not contain any 'basic_authentication' value at the time when this script is written.
-- Stage has two records with 'basic_authentication' data but these records where created during tests from the REST API.
-- The frontend does not allow (yet) to change the basic authentication settings.

UPDATE camel_properties SET basic_authentication = null WHERE basic_authentication IS NOT NULL;
