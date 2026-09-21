const { initializeApp, cert } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const path = require('path');
const fs = require('fs');

function resolveCredentials() {
  const env = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (env && fs.existsSync(env)) return env;
  const fallback = path.join(__dirname, 'credentials', 'alkilapp-seed-sa.json');
  if (fs.existsSync(fallback)) return fallback;
  throw new Error('No credentials found');
}

initializeApp({
  credential: cert(resolveCredentials()),
  projectId: 'gen-lang-client-0040505884'
});

const auth = getAuth();

async function createTestUser() {
  const email = 'test.reviewer@alkilapp.com';
  const password = 'TestPass123!';
  const displayName = 'Play Console Reviewer';

  try {
    // Check if user already exists
    try {
      const existing = await auth.getUserByEmail(email);
      console.log(`User already exists: ${existing.uid}`);
      return existing.uid;
    } catch (e) {
      // User doesn't exist, create it
    }

    const userRecord = await auth.createUser({
      email,
      password,
      displayName,
      emailVerified: true
    });

    console.log('Test user created:', userRecord.uid);
    console.log('Email:', email);
    console.log('Password:', password);
    console.log('Display name:', displayName);
    console.log('');
    console.log('Agrega estas credenciales en Play Console → Detalles de acceso:');
    console.log(`  Email: ${email}`);
    console.log(`  Password: ${password}`);

  } catch (error) {
    console.error('Error:', error.message);
  }
}

createTestUser().then(() => process.exit(0));