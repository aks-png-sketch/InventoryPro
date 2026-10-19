import {test} from 'node:test';
import assert from 'node:assert/strict';
import {stockStatus,productPayload,localDate,money} from './format.js';
test('stock statuses separate zero, reorder threshold and healthy balances',()=>{
 assert.equal(stockStatus({quantity:0,reorderLevel:10}),'Out of stock');
 assert.equal(stockStatus({quantity:10,reorderLevel:10}),'Low stock');
 assert.equal(stockStatus({quantity:11,reorderLevel:10}),'In stock');
});
test('product submission preserves price decimal string and optional supplier',()=>{
 const p=productPayload({sku:' X-1 ',name:' Cable ',unitPrice:'0.10',categoryId:'2',supplierId:'',quantity:'0',reorderLevel:'5'});
 assert.equal(p.unitPrice,'0.10');assert.equal(p.supplierId,null);assert.equal(p.quantity,0);assert.equal(p.categoryId,2);assert.equal(p.sku,'X-1');
});
test('date filters use local calendar day',()=>assert.equal(localDate(new Date(2026,9,19,0,5)),'2026-10-19'));
test('currency formats actual values including zero',()=>{assert.match(money(0),/0\.00/);assert.match(money('1250.50'),/1,250\.50/)});
