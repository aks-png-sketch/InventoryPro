import {test} from 'node:test';
import assert from 'node:assert/strict';
import {resolveApiBase} from './api-url.js';
test('API base supports local proxy and remote HTTPS',()=>{assert.equal(resolveApiBase(),'/api');assert.equal(resolveApiBase(' https://example.com/api/ '),'https://example.com/api');assert.equal(resolveApiBase('/inventory/api/'),'/inventory/api')});
test('API base rejects ambiguous or unsafe URL forms',()=>{for(const value of ['//evil.example/api','javascript:alert(1)','relative/api','https://example.com/api?x=1'])assert.throws(()=>resolveApiBase(value))});
