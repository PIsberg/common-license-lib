# License Gating Guide

This guide covers how to set up, manage, and obtain licenses for software integrated with `common-license-lib`.

## 1. How to Obtain a License (End-User Flow)

If you are a commercial user of a software product using this library, you will need a valid license key.

1.  **Get the Checkout URL**: The application will typically provide a link or button to its store (e.g., `https://my-store.lemonsqueezy.com/checkout/...`).
2.  **Complete Purchase**: Follow the Lemon Squeezy checkout process.
3.  **Receive License Key**: Upon successful payment, a unique license key will be:
    - Displayed on the **Order Confirmation** page.
    - Sent to your **Email** address.
    - Available in your **Lemon Squeezy Customer Portal** under "My Orders".
4.  **Activate**: Provide the license key to the application when prompted, or configure it via the relevant environment variable (e.g., `LICENSE_KEY`).

---

## 2. Admin Setup: Finding Credentials

To integrate the library, you need credentials from your **Keygen.sh** and **Lemon Squeezy** dashboards.

### Keygen.sh
| Field | Location in Dashboard |
| :--- | :--- |
| **Account ID** | Open **Account Settings** (profile menu). Found under the **Current Account** section. |
| **API Key** | Go to **Settings > Tokens**. Generate an **Environment** or **Admin** token. |
| **Product ID** | Go to **Products** in the sidebar. Select your product; the ID is on the details page. |

### Lemon Squeezy
| Field | Location in Dashboard |
| :--- | :--- |
| **Store Subdomain** | The slug for your store (e.g., `my-store` if URL is `my-store.lemonsqueezy.com`). |
| **Signing Secret** | Go to **Settings > Webhooks**. You define this string when creating/editing a webhook. |
| **Variant ID** | Go to **Products**. Select your product, then the **Variants** tab to "Copy ID". |

---

## 3. Configuration Mapping

Regardless of the integration method, these are the core fields used by the `LicenseGate`:

| Logical Field | Common System Property | Usage |
| :--- | :--- | :--- |
| **Keygen Account ID** | `keygen.account.id` | Identification for discovery. |
| **Keygen API Key** | `keygen.api.key` | Primary authentication for the gate. |
| **Keygen Product ID** | `keygen.product.id` | (Optional) Scope checks to this product. |
| **License Key** | `license.key` | The key provided by the end-user. |
| **LS Store Subdomain** | `ls.store.subdomain` | Used for generating checkout URLs. |
| **LS Signing Secret** | `ls.webhook.secret` | Used to verify incoming purchase webhooks. |
