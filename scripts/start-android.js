import { spawnSync } from 'node:child_process';
import { join } from 'node:path';

const port = String(process.env.PORT || 4173);
const sdkRoot = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME ||
  (process.env.LOCALAPPDATA ? join(process.env.LOCALAPPDATA, 'Android', 'Sdk') : '');
const adb = sdkRoot ? join(sdkRoot, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb') : 'adb';
const reverse = spawnSync(adb, ['reverse', `tcp:${port}`, `tcp:${port}`], { encoding: 'utf8' });
if (reverse.status === 0) console.log(`Android port reverse active: device localhost:${port} -> backend localhost:${port}`);
else console.warn('No Android device was available for adb reverse. Start an emulator, then run this command again.');

process.env.HOST ||= '127.0.0.1';
await import('../server.js');
