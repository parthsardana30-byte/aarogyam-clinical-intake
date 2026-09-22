import assert from 'node:assert/strict';
import { readFile, stat } from 'node:fs/promises';
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


test('prerecorded voice-guide manifest points to valid narration audio', async () => {
  const [catalogText, manifestText] = await Promise.all([
    readFile(new URL('../dist/voice-guide-scripts.json', import.meta.url), 'utf8'),
    readFile(new URL('../dist/voice-guides/manifest.json', import.meta.url), 'utf8')
  ]);
  const catalog = JSON.parse(catalogText);
  const manifest = JSON.parse(manifestText);

  for (const [language, recordings] of Object.entries(manifest)) {
    for (const [key, relativePath] of Object.entries(recordings)) {
      assert.ok(catalog[language]?.[key], `${language}:${key} must have a narration script`);
      assert.match(relativePath, /^[a-z]{2}\/[a-z0-9_]+\.mp3$/);
      const audioUrl = new URL(`../dist/voice-guides/${relativePath}`, import.meta.url);
      const [info, header] = await Promise.all([stat(audioUrl), readFile(audioUrl).then(audio => audio.subarray(0, 3))]);
      assert.ok(info.size > 1_000, `${relativePath} must contain audio`);
      assert.ok(header.toString('ascii') === 'ID3' || (header[0] === 0xff && (header[1] & 0xe0) === 0xe0), `${relativePath} must be an MP3`);
    }
  }
});
