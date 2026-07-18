/**
 * Thin wrapper around the browser WebAuthn API for passkey registration.
 *
 * Uses the native JSON conversion methods
 * (PublicKeyCredential.parseCreationOptionsFromJSON and
 * PublicKeyCredential.prototype.toJSON), which produce/consume exactly the
 * base64url JSON format emitted and expected by the server-side Yubico
 * WebAuthn library. This is real WebAuthn — the browser prompts the platform
 * authenticator (Touch ID / Windows Hello / security key); nothing is mocked.
 */

// The DOM lib types for these newer static/instance methods are not present in
// all TypeScript versions yet, so we declare the minimal shape we rely on.
interface PublicKeyCredentialStaticJson {
  parseCreationOptionsFromJSON(
    options: unknown,
  ): PublicKeyCredentialCreationOptions
}

interface CredentialWithToJson extends Credential {
  toJSON(): unknown
}

/** True if this browser supports WebAuthn and the JSON conversion helpers. */
export function isWebAuthnSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.PublicKeyCredential !== 'undefined' &&
    typeof (
      window.PublicKeyCredential as unknown as PublicKeyCredentialStaticJson
    ).parseCreationOptionsFromJSON === 'function'
  )
}

/**
 * Run navigator.credentials.create() for the given server-issued creation
 * options JSON and return the credential as a JSON-serializable object ready
 * to POST back to /passkey/register.
 *
 * @param creationOptions the `{ publicKey: {...} }` object from the start step
 */
export async function createPasskey(creationOptions: {
  publicKey: unknown
}): Promise<unknown> {
  if (!isWebAuthnSupported()) {
    throw new Error(
      'This browser does not support passkeys. Please use an up-to-date browser.',
    )
  }

  const staticApi =
    window.PublicKeyCredential as unknown as PublicKeyCredentialStaticJson
  const publicKey = staticApi.parseCreationOptionsFromJSON(
    creationOptions.publicKey,
  )

  const credential = (await navigator.credentials.create({
    publicKey,
  })) as CredentialWithToJson | null

  if (!credential) {
    throw new Error('Passkey creation was cancelled.')
  }

  return credential.toJSON()
}
