import {test} from 'node:test';
import assert from 'node:assert/strict';
import {api,resetCsrf} from './api.js';
const response=(status,data)=>({status,ok:status>=200&&status<300,json:async()=>data});
test('CSV export can read text through the authenticated API client',async()=>{globalThis.fetch=async()=>({...response(200,null),text:async()=>'sku,quantity\r\nTEST,5\r\n'});assert.match(await api('/reports/valuation/csv',{responseType:'text'}),/TEST,5/)});
test('mutating requests carry CSRF and credential cookies',async()=>{
 resetCsrf();const calls=[];
 globalThis.fetch=async(url,options)=>{calls.push({url,options});return response(200,url.endsWith('/csrf')?{headerName:'X-CSRF-TOKEN',token:'fresh'}:{saved:true})};
 assert.deepEqual(await api('/products',{method:'POST',body:'{}'}),{saved:true});
 assert.equal(calls[1].options.headers['X-CSRF-TOKEN'],'fresh');assert.equal(calls[1].options.credentials,'include');
});
test('forbidden response invalidates cached CSRF without retrying mutation',async()=>{
 resetCsrf();let tokenRequests=0,mutations=0;
 globalThis.fetch=async(url)=>{if(url.endsWith('/csrf')){tokenRequests++;return response(200,{headerName:'X-CSRF-TOKEN',token:'fresh'})}mutations++;return response(mutations===1?403:200,{message:'Expired'})};
 await assert.rejects(api('/products',{method:'POST',body:'{}'}),/Expired/);
 await api('/products',{method:'POST',body:'{}'});assert.equal(tokenRequests,2);assert.equal(mutations,2);
});
test('HTML or invalid JSON success does not become fake API success',async()=>{
 globalThis.fetch=async()=>({...response(200,null),json:async()=>{throw Error('HTML')}});
 await assert.rejects(api('/dashboard'),/invalid response/);
});
test('network failures are explained as connectivity errors',async()=>{
 globalThis.fetch=async()=>{throw Error('offline')};await assert.rejects(api('/dashboard'),/Cannot reach the backend/);
});
