# Google Play — Data Safety form answers

Fill the **Data safety** section (App content → Data safety) exactly as below.
Accuracy is enforced by Google; these answers match the app's actual behaviour
(minimal permissions, self-hosted server, Sentry crash-only reporting with PII
off).

## Overview questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS/TLS to your instance and to Sentry) |
| Do you provide a way for users to request that their data be deleted? | **Yes** — via the instance operator / account (describe in the notes) |

## Data types — declare these

### Personal info → **Email address**
- Collected: **Yes** · Shared: **No** (sent only to the user's own instance)
- Processed ephemerally: No
- Required or optional: **Required** (to sign in)
- Purpose: **App functionality** (account management)

### Personal info → **Name**
- Collected: **Yes** · Shared: **No**
- Required or optional: Optional
- Purpose: **App functionality**

### Personal info → **User IDs** (device/session identifier)
- Collected: **Yes** · Shared: **No**
- Purpose: **App functionality** (session / device management)

### App activity → **Other user-generated content** (loans, reservations, reviews, wishlist)
- Collected: **Yes** · Shared: **No** (stored on the user's instance)
- Purpose: **App functionality**

### App info and performance → **Crash logs**
- Collected: **Yes** · Shared: **Yes** (with Sentry, the crash-reporting provider)
- Purpose: **App functionality** (stability/diagnostics)

### App info and performance → **Diagnostics** (app version, device model, OS version)
- Collected: **Yes** · Shared: **Yes** (with Sentry)
- Purpose: **App functionality**

## Do NOT declare (the app does not collect these)
- Location, Financial info, Health, Messages, Photos/Videos, Files, Contacts,
  Calendar, Web browsing, Advertising ID, Precise identifiers for ads.

## Notes to paste in the "data deletion" explanation
> Account and library data are stored on the self-hosted Pinakes instance the
> user connects to, not on a developer-operated backend. Users delete their
> data by contacting the operator of that instance (library staff or the
> instance administrator). On-device data is cleared on logout or uninstall.
> Crash reports sent to Sentry contain no personal identifiers (IP off) and are
> deleted per Sentry's retention window.

## Password handling note (if asked)
The password is a **credential** used only to authenticate against the user's
instance; it is not stored on the device in plain text and is not shared with
any third party. Google's Data safety form does not have a "password" data type
— it is covered as an authentication credential, not declared as collected user
data.
