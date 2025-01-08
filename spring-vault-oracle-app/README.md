# spring-vault-oracle-app

The Spring Vault Oracle App sample demonstrates dynamically loading secrets from [OCI Vault](https://docs.oracle.com/en-us/iaas/Content/KeyManagement/home.htm) into the Spring Boot context during startup. All secrets loaded this way are made available by name as application properties, accessible using `@Value` annotations.

## Prerequisites

- Access to an OCI Account. You can use [Free Tier](https://signup.oraclecloud.com/).
- Java 21+, Maven
- [OCI Config file](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm) for authentication

## Setup

1. Create an [OCI Vault](https://docs.oracle.com/en-us/iaas/Content/KeyManagement/home.htm), and add two secrets to the vault named `s1` and `s2`. 

2. Set the following environment variables, using values for your OCI Compartment, Region, and Vault.

```bash
OCI_COMPARTMENT_ID=<OCI Compartment ID>
OCI_REGION=<OCI Region>
OCI_VAULT_ID=<OCI Vault OCID>
```

## Run the app

Using Maven, start the app:

`mvn spring-boot:run`

Now, query the app to see if the Vault secrets were successfully loaded:

```bash
curl localhost:9001/values
```