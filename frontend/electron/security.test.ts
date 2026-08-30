import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isTrustedSender } from './security.js';

const options = {
  packaged: false,
  devServerUrl: 'http://127.0.0.1:5173',
  packagedEntryPath: '/opt/FlowMesh/dist/index.html',
};

test('accepts the exact development server origin', () => {
  assert.equal(isTrustedSender('http://127.0.0.1:5173/', options), true);
});

test('rejects a URL with a forged prefix or different port', () => {
  assert.equal(isTrustedSender('http://127.0.0.1:5173@attacker.example/', options), false);
  assert.equal(isTrustedSender('http://127.0.0.1:5174/', options), false);
});

test('accepts only the packaged entry file', () => {
  const packagedOptions = { ...options, packaged: true };
  assert.equal(isTrustedSender('file:///opt/FlowMesh/dist/index.html', packagedOptions), true);
  assert.equal(isTrustedSender('file:///opt/FlowMesh/dist/other.html', packagedOptions), false);
  assert.equal(isTrustedSender('https://attacker.example/', packagedOptions), false);
});
