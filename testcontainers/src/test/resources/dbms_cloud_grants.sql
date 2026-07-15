-- Set as appropriate for your database. "freepdb1" is the default PDB in Oracle AI Database Free
alter session set container = freepdb1;

-- add grants for DMBS_CLOUD family packages
grant execute on dbms_cloud to testuser;
grant execute on dbms_cloud_ai to testuser;
BEGIN
    DBMS_CLOUD.CREATE_CREDENTIAL(
        credential_name => 'GENAI_CRED',
        user_ocid       => 'Your User OCID',
        tenancy_ocid    => 'Your Tenancy OCID',
        private_key     => 'Your Private Key in PEM format',
        fingerprint     => 'Your Private Key Fingerprint'
    );
    DBMS_CLOUD_AI.CREATE_PROFILE(
        profile_name =>'GENAI',
        attributes   =>'{"provider": "oci",
            "credential_name": "GENAI_CRED",
            "object_list": [{"owner": "TESTUSER", "name": "students"},
                            {"owner": "TESTUSER", "name": "lecture_halls"},
                            {"owner": "TESTUSER", "name": "courses"},
                            {"owner": "TESTUSER", "name": "enrollments"}]
        }');
END;
/