WHENEVER SQLERROR EXIT FAILURE
WHENEVER OSERROR EXIT FAILURE
SET VERIFY OFF

CREATE OR REPLACE VIEW LAB_APP.LAB_READINGS_REST AS
    SELECT id, sensor, value FROM LAB_APP.LAB_READINGS
    WITH READ ONLY;

-- Configure REST metadata as the schema owner, rather than inheriting SYS privileges.
CONNECT &1

BEGIN
    ORDS.ENABLE_SCHEMA(
        p_enabled => TRUE,
        p_schema => 'LAB_APP',
        p_url_mapping_type => 'BASE_PATH',
        p_url_mapping_pattern => 'lab',
        p_auto_rest_auth => FALSE);
    ORDS.ENABLE_OBJECT(
        p_enabled => TRUE,
        p_schema => 'LAB_APP',
        p_object => 'LAB_READINGS_REST',
        p_object_type => 'VIEW',
        p_object_alias => 'readings',
        p_auto_rest_auth => FALSE);
    COMMIT;
END;
/
EXIT SUCCESS
