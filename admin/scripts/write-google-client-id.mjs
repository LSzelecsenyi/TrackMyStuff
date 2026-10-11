import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const clientId = process.env.STRICT_ADMIN_GOOGLE_CLIENT_ID ?? '';
if (!/^[0-9][0-9A-Za-z-]*\.apps\.googleusercontent\.com$/.test(clientId)) {
  console.error(
    'STRICT_ADMIN_GOOGLE_CLIENT_ID must be a Google web client id (*.apps.googleusercontent.com).',
  );
  process.exit(1);
}

const file = join(
  dirname(fileURLToPath(import.meta.url)),
  '..',
  'src',
  'environments',
  'environment.production.ts',
);
const source = readFileSync(file, 'utf8');
const updated = source.replace("googleClientId: ''", `googleClientId: '${clientId}'`);
if (updated === source || !updated.includes(clientId)) {
  console.error('environment.production.ts no longer has an empty googleClientId assignment.');
  process.exit(1);
}
writeFileSync(file, updated);
console.log('Wrote the public Google web client id into the production environment file.');
