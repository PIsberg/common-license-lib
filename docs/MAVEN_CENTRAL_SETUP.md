# Maven Central Setup Guide

Scaling your project to Maven Central (via the Sonatype Central Portal) requires three main parts: Namespace Verification, GPG Key setup, and GitHub Secrets configuration.

## Step 1: Sonatype Central Portal Setup

1.  **Register**: Go to [central.sonatype.com](https://central.sonatype.com/) and sign up.
2.  **Verify Namespace**:
    - Click on your profile icon -> **Namespaces**.
    - Add `se.deversity.common` (or your specific package namespace).
    - If using the `io.github.PIsberg` pattern, Sonatype will ask you to create a temporary public repository with a specific name to prove ownership.
3.  **Generate User Token**:
    - Navigate to **View User Token**.
    - Click **Generate Token**.
    - You will get a `Username` and `Password`. These are **NOT** your login credentials. These are the values for `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`.

## Step 2: GPG Key Generation

Maven Central requires all artifacts to be signed by a trusted GPG key.

1.  **Generate a Key**:
    ```bash
    gpg --full-generate-key
    ```
    - Select **(1) RSA and RSA**
    - Keysize: **4096**
    - Expiration: **0** (or whatever you prefer)
    - Set your Name, Email, and a strong **Passphrase**.

2.  **Identify your Key ID**:
    ```bash
    gpg --list-keys --keyid-format LONG
    ```
    Find the line starting with `pub`. The ID is the long string of characters (e.g., `ABC12345DEF67890`).

3.  **Publish your Public Key**:
    Push your public key to the servers so Maven Central can verify your signatures:
    ```bash
    gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
    gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
    ```

4.  **Export Private Key**:
    You need the ASCII-armored private key for GitHub Secrets:
    ```bash
    gpg --export-secret-keys --armor YOUR_KEY_ID > private-key.asc
    ```
    Open `private-key.asc` and copy the **entire** content (including the BEGIN/END headers).

## Step 3: Configure GitHub Secrets

Go to your repository on GitHub: **Settings > Secrets and variables > Actions > New repository secret**.

| Secret Name | Source / Value |
| :--- | :--- |
| `MAVEN_CENTRAL_USERNAME` | The `Username` from Sonatype User Token |
| `MAVEN_CENTRAL_PASSWORD` | The `Password` from Sonatype User Token |
| `MAVEN_GPG_PRIVATE_KEY` | The content of your `private-key.asc` |
| `MAVEN_GPG_PASSPHRASE` | The passphrase you set for your GPG key |

## Step 4: Trigger the Release

Once the secrets are set:
1.  Ensure `pom.xml` has the correct version (e.g., `0.2.0`).
2.  Commit the change.
3.  Tag with `v0.2.0`.
4.  Push everything: `git push origin v0.2.0`

> [!CAUTION]
> Never share your `MAVEN_CENTRAL_PASSWORD` or `MAVEN_GPG_PRIVATE_KEY` in plain text or commit them to the repository.
