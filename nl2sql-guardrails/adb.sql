-- run this on your database where SELECT AI is enabled

BEGIN
    DBMS_CLOUD.CREATE_CREDENTIAL(
            credential_name => 'GENAI_CRED',
            user_ocid       => 'Your User OCID',
            tenancy_ocid    => 'Your Tenancy OCID',
            private_key     => 'Your Private Key in PEM format',
            fingerprint     => 'Your Private Key Fingerprint'
    );
END;
/

BEGIN
    DBMS_CLOUD_AI.CREATE_PROFILE(
            profile_name =>'GENAI',
            attributes   =>'{"provider": "oci",
            "credential_name": "GENAI_CRED",
            "object_list": [{"owner": "ADMIN", "name": "CUSTOMERS"},
                            {"owner": "ADMIN", "name": "PRODUCTS"},
                            {"owner": "ADMIN", "name": "SALES_ORDERS"},
                            {"owner": "ADMIN", "name": "SALES_ORDER_ITEMS"},
                            {"owner": "ADMIN", "name": "SUBSCRIPTIONS"},
                            {"owner": "ADMIN", "name": "SUPPORT_INTERACTIONS"},
                            {"owner": "ADMIN", "name": "CUSTOMER_CHURN_SCORES"}]
        }');
END;
/
