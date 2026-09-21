import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:net';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

async function withBackend(extraEnvironment, check) {
  const probe = createServer();
  await new Promise(resolve => probe.listen(0, '127.0.0.1', resolve));
  const port = probe.address().port;
  await new Promise(resolve => probe.close(resolve));
  const dataDir = await mkdtemp(join(tmpdir(), 'aarogyam-mobile-otp-test-'));
  const backend = spawn(process.execPath, ['server.js'], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      PORT: String(port),
      HOST: '127.0.0.1',
      DATA_DIR: dataDir,
      NODE_ENV: 'development',
      OTP_DEMO_MODE: '0',
      MSG91_AUTH_KEY: 'test-auth-key',
      MSG91_WIDGET_ID: 'web-widget',
      MSG91_WIDGET_TOKEN: 'web-token',
      MSG91_MOBILE_WIDGET_ID: '',
      MSG91_MOBILE_WIDGET_TOKEN: '',
      ...extraEnvironment,
    },
    stdio: 'ignore',
  });
  const origin = `http://127.0.0.1:${port}`;
  try {
    let ready = false;
    for (let attempt = 0; attempt < 60; attempt += 1) {
      try {
        if ((await fetch(`${origin}/api/signup-otp/config`)).ok) { ready = true; break; }
      } catch { /* Wait for the test server. */ }
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    assert.equal(ready, true, 'Backend did not start');
    await check(origin);
  } finally {
    if (backend.exitCode === null) {
      backend.kill();
      await new Promise(resolve => backend.once('exit', resolve));
    }
    await rm(dataDir, { recursive: true, force: true });
  }
}

test('Android receives its own widget and the website keeps its existing widget', async () => {
  await withBackend({ MSG91_MOBILE_WIDGET_ID: 'mobile-widget', MSG91_MOBILE_WIDGET_TOKEN: 'mobile-token' }, async origin => {
    const web = await (await fetch(`${origin}/api/signup-otp/config`)).json();
    const android = await (await fetch(`${origin}/api/signup-otp/config?client=android`)).json();
    assert.deepEqual(web, { provider: 'msg91', widgetId: 'web-widget', tokenAuth: 'web-token' });
    assert.deepEqual(android, { client: 'android', provider: 'msg91', widgetId: 'mobile-widget', tokenAuth: 'mobile-token' });
  });
});

test('Android fails closed when its mobile widget is not configured', async () => {
  await withBackend({}, async origin => {
    const android = await (await fetch(`${origin}/api/signup-otp/config?client=android`)).json();
    assert.deepEqual(android, { client: 'android', provider: 'unconfigured' });
  });
});
