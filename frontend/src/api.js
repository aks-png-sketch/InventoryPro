import {resolveApiBase} from './api-url.js';
const base=resolveApiBase(import.meta.env?.VITE_API_BASE_URL);
export const apiUrl = path => base + path;
let csrf;
export async function api(path, options={}) {
  const {responseType,...requestOptions}=options;
  const method=options.method || 'GET';
  const headers={...options.headers};
  if(!['GET','HEAD'].includes(method)) {
    if(!csrf) csrf=await api('/auth/csrf');
    headers[csrf.headerName]=csrf.token;
    if(!(options.body instanceof URLSearchParams)) headers['Content-Type']='application/json';
  }
  let response;
  try { response=await fetch(apiUrl(path),{...requestOptions,headers,credentials:'include'}); }
  catch { throw new Error('Cannot reach the backend. Start Spring Boot on port 8080 and check Oracle connectivity.'); }
  const data=response.status===204 ? null : (response.ok && responseType==='text' ? await response.text() : await response.json().catch(()=>null));
  if(!response.ok) {
    if(response.status===401 || response.status===403) resetCsrf();
    if(response.status===401 && !path.startsWith('/auth/')) window.dispatchEvent(new Event('session-expired'));
    const error=new Error(data?.message || (response.status===503 || response.status===500 ? 'Backend or Oracle connection unavailable. Check the server and database configuration.' : `Request failed (${response.status}).`));
    error.fields=data?.fields; error.status=response.status; throw error;
  }
  if(response.status!==204 && data===null) throw new Error('The API returned an invalid response. Check the backend URL and reverse proxy configuration.');
  return data;
}
export function resetCsrf() { csrf=undefined; }
export const json = (method,body) => ({method,body:JSON.stringify(body)});
