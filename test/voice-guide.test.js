import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('server voice-guide catalog matches the browser narration scripts', async () => {
  const [html, catalogText] = await Promise.all([
    readFile(new URL('../dist/index.html', import.meta.url), 'utf8'),
    readFile(new URL('../dist/voice-guide-scripts.json', import.meta.url), 'utf8')
  ]);
  const match = html.match(/const voiceGuideScripts = (\{[\s\S]*?\n    \});/);
  assert.ok(match, 'embedded voice-guide scripts should exist');
  const embedded = Function(`"use strict"; return (${match[1]})`)();
  const catalog = JSON.parse(catalogText);
  assert.deepEqual(catalog, embedded);
  assert.deepEqual(Object.keys(catalog).sort(), ['English', 'Hindi']);
  assert.equal(Object.keys(catalog.English).length, Object.keys(catalog.Hindi).length);
});
