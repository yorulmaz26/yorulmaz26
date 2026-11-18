import { createHmac } from 'node:crypto';

/**
 * coturn "use-auth-secret" (TURN REST API) kısa ömürlü kimlik bilgisi.
 * Paylaşılan gizli anahtar yalnızca sunucuda (Secret Manager) durur;
 * uygulamaya gömülmez. Kimlik bilgisi [ttlSeconds] sonra geçersizleşir.
 */
export function turnCredentials(args: {
  uid: string;
  secret: string;
  urls: string[];
  nowSeconds: number;
  ttlSeconds?: number;
}): { urls: string[]; username: string; credential: string; ttl: number } {
  const ttl = args.ttlSeconds ?? 3600;
  const username = `${args.nowSeconds + ttl}:${args.uid}`;
  const credential = createHmac('sha1', args.secret).update(username).digest('base64');
  return { urls: args.urls, username, credential, ttl };
}

export function parseUrls(raw: string | undefined): string[] {
  return (raw ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => /^(turns?|stun):/.test(s));
}
