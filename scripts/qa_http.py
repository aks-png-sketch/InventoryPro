"""HTTP QA against ONLY the temporary schema/server owned by Verify-OracleLab.ps1.
Credentials are inherited from that harness, never saved in this report.
"""
import csv
import io
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request
import http.cookiejar

BASE = 'http://localhost:8081/api'
results = []
def check(name, condition):
    results.append({'name': name, 'passed': bool(condition)})
    print(('PASS: ' if condition else 'FAIL: ') + name)
    if not condition:
        raise AssertionError(name)

class Client:
    def __init__(self):
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.token = None
    def call(self, path, method='GET', body=None, expected=200, csrf=True, form=False, headers=None, raw=False):
        h = dict(headers or {})
        if method not in ('GET','HEAD','OPTIONS') and csrf:
            if self.token is None:
                self.token = self.call('/auth/csrf')
            h[self.token['headerName']] = self.token['token']
        data = None
        if body is not None:
            data = (urllib.parse.urlencode(body) if form else json.dumps(body)).encode()
            h['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
        request = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
        try:
            response = self.opener.open(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        content = response.read().decode('utf-8-sig')
        check(f'{method} {path.split("?")[0]} returns {expected}', response.code == expected)
        if raw:
            return content, response.headers
        try:
            return json.loads(content) if content else None
        except json.JSONDecodeError:
            return {'message': content}
    def login(self, username, password, expected=200):
        self.token = self.call('/auth/csrf')
        self.call('/auth/login','POST',{'username':username,'password':password},expected,form=True)
        self.token = None

def product_payload(sku, category, supplier=None, price='12.50', quantity=0):
    return dict(sku=sku,name=sku,description='Isolated HTTP QA',categoryId=category,supplierId=supplier,unitPrice=price,quantity=quantity,reorderLevel=5)

def exercise():
    page=urllib.request.urlopen('http://localhost:8081/',timeout=10)
    html=page.read().decode()
    check('Production JAR serves public React index',page.status==200 and 'InventoryPro' in html)
    anonymous = Client()
    anonymous.call('/products',expected=401)
    anonymous.call('/auth/login','POST',{'username':'nobody','password':'invalid-password'},403,csrf=False,form=True)
    anonymous.login("' OR '1'='1",'invalid-password',401)
    admin = Client()
    admin.login(os.environ['DEV_ADMIN_USERNAME'],os.environ['DEV_ADMIN_PASSWORD'])
    check('Admin identity stored in authenticated session',admin.call('/auth/me')['role']=='ADMIN')
    check('Session cookie is HttpOnly',any('HttpOnly' in cookie._rest for cookie in admin.cookies))
    check('Session cookie SameSite is Lax',any(str(cookie._rest.get('SameSite','')).lower()=='lax' for cookie in admin.cookies))
    admin.call('/products','POST',{},403,csrf=False)
    admin.call('/products',headers={'Origin':'https://untrusted.invalid'},expected=403)
    dashboard = admin.call('/dashboard')
    check('New isolated schema has truthful zero metrics',dashboard['totalProducts']==0 and dashboard['inventoryValue']==0 and dashboard['lowStock']==0 and dashboard['outOfStock']==0)
    check('New isolated schema has empty stock chart',dashboard['chart']==[])
    cat = admin.call('/categories','POST',{'name':'QA Category','description':'Isolated'},201)
    cat['name']='QA Category Updated'
    admin.call('/categories/'+str(cat['id']),'PUT',{'name':cat['name'],'description':'Updated'})
    supplier = admin.call('/suppliers','POST',{'name':'QA Supplier','contactPerson':'QA','email':'qa@example.invalid','phone':'123','address':'Test'},201)
    sid = supplier['id']
    admin.call(f'/suppliers/{sid}','PUT',{'name':'QA Supplier Updated','contactPerson':'QA','email':'qa@example.invalid','phone':'456','address':'Test'})
    admin.call('/suppliers','POST',{'name':'Bad Email','email':'invalid'},400)
    admin.call('/categories','POST',{'name':''},400)
    payload=product_payload('QA-PERSIST',cat['id'],sid)
    p=admin.call('/products','POST',payload,201); pid=p['id']
    check('Oracle join names serialized correctly',p['categoryName']==cat['name'] and p['supplierName']=='QA Supplier Updated')
    check('Oracle timestamps are JSON strings',isinstance(p['createdAt'],str) and isinstance(p['updatedAt'],str))
    payload['name']='QA Product Updated'
    admin.call(f'/products/{pid}','PUT',payload)
    admin.call('/products','POST',payload,409)
    admin.call('/products','POST',dict(payload,sku='BAD-PRICE',unitPrice=-1),400)
    admin.call('/products','POST',dict(payload,sku='BAD-FK',categoryId=999999999),409)
    admin.call('/products','POST',dict(payload,sku='BAD-DECIMAL',unitPrice='0.001'),400)
    admin.call('/products','POST',dict(payload,sku='BAD-QUANTITY',quantity=1.5),400)
    admin.call(f'/suppliers/{sid}','DELETE',expected=409)
    admin.call('/categories/'+str(cat['id']),'DELETE',expected=409)
    check('Literal SQL injection search returns zero records',admin.call('/products?'+urllib.parse.urlencode({'search':"' OR 1=1 --"}))['total']==0)
    check('LIKE wildcard is escaped',admin.call('/products?search=%25')['total']==0)
    admin.call('/products?sort=name%3BDROP%20TABLE%20products',expected=400)
    admin.call('/products?direction=desc%3BDELETE',expected=400)
    admin.call('/products?page=0',expected=400)
    check('Category filtering returns the linked product',admin.call('/products?category='+str(cat['id']))['total']==1)
    check('SKU search returns expected product',admin.call('/products?search=QA-PERSIST')['total']==1)
    for field in ('name','price','quantity'):
        for direction in ('asc','desc'):
            check(f'{field} {direction} sort works',admin.call(f'/products?sort={field}&direction={direction}')['total']==1)
    def move(qty, kind, expected=201):
        return admin.call('/movements','POST',dict(productId=pid,quantity=qty,type=kind,reason='HTTP QA'),expected)
    move(0,'IN',400);move(1,'ADJUST',400)
    check('Stock receipt commits balance',move(8,'IN')['quantity']==8)
    check('Stock dispatch commits balance',move(3,'OUT')['quantity']==5)
    move(6,'OUT',409)
    check('Insufficient stock leaves balance unchanged',admin.call(f'/products/{pid}')['quantity']==5)
    admin.call(f'/products/{pid}','PUT',dict(payload,quantity=9),409)
    d=admin.call('/dashboard')
    check('Valuation and low-stock metrics match Oracle data',d['totalProducts']==1 and d['inventoryValue']==62.5 and d['lowStock']==1 and d['outOfStock']==0)
    check('Dashboard chart aggregates real IN/OUT quantities',sum(x['stockIn'] for x in d['chart'])==8 and sum(x['stockOut'] for x in d['chart'])==3)
    check('Category chart counts products',sum(x['productCount'] for x in d['categories'])==1)
    check('Low-stock filter catches reorder threshold',admin.call('/products?status=low')['total']==1)
    move(5,'OUT')
    check('Out-of-stock filter catches zero',admin.call('/products?status=out')['total']==1)
    check('Low-stock filter excludes zero',admin.call('/products?status=low')['total']==0)
    move(5,'IN')
    dates='?from=2020-01-01&to=2030-01-01'
    history=admin.call('/movements'+dates)
    check('Only successful operations create audit entries',history['total']==4)
    check('Date filter excludes later records',admin.call('/movements?from=2000-01-01&to=2000-01-02')['total']==0)
    admin.call('/movements?from=2030-01-01&to=2020-01-01',expected=400)
    admin.call('/reports/unknown'+dates,expected=400)
    for kind in ('valuation','low-stock','out-of-stock','movements','categories','suppliers','summary'):
        records=admin.call('/reports/'+kind+dates)
        check(f'{kind} report returns JSON array',isinstance(records,list))
        content,headers=admin.call('/reports/'+kind+'/csv'+dates,raw=True)
        check(f'{kind} CSV download has attachment header','attachment' in headers.get('Content-Disposition',''))
        if records:
            parsed=list(csv.reader(io.StringIO(content)))
            check(f'{kind} CSV contains all rows',len(parsed)==len(records)+1)
    temp=admin.call('/products','POST',product_payload('QA-DELETE',cat['id']),201)
    admin.call('/products/'+str(temp['id']),'DELETE',expected=204)
    admin.call('/products/'+str(temp['id']),expected=404)
    admin.call(f'/products/{pid}','DELETE',expected=409)
    temp_cat=admin.call('/categories','POST',{'name':'QA Delete Category'},201)
    admin.call('/categories/'+str(temp_cat['id']),'DELETE',expected=204)
    temp_supplier=admin.call('/suppliers','POST',{'name':'QA Delete Supplier'},201)
    admin.call('/suppliers/'+str(temp_supplier['id']),'DELETE',expected=204)
    staff_password='Test'+os.urandom(16).hex()
    admin.call('/users','POST',{'username':'qa_staff','password':staff_password,'role':'STAFF'},201)
    staff=Client();staff.login('qa_staff',staff_password)
    check('Staff identity is STAFF',staff.call('/auth/me')['role']=='STAFF')
    staff.call('/dashboard');staff.call('/products');staff.call('/reports/summary'+dates)
    for path,method,body in [('/products','POST',payload),(f'/products/{pid}','PUT',dict(payload,quantity=5)),(f'/products/{pid}','DELETE',None),('/categories','POST',{'name':'Denied'}),('/suppliers','POST',{'name':'Denied'}),('/users','POST',{'username':'denied_user','password':staff_password,'role':'ADMIN'})]:
        staff.call(path,method,body,403)
    staff.call('/movements','POST',dict(productId=pid,quantity=1,type='IN',reason='Staff receipt'),201)
    check('Staff stock operation updates quantity',staff.call(f'/products/{pid}')['quantity']==6)
    staff.call('/auth/logout','POST',expected=204)
    staff.call('/auth/me',expected=401)
    admin.call('/auth/logout','POST',expected=204)
    admin.call('/dashboard',expected=401)

def restart_check():
    client=Client()
    client.login(os.environ['DEV_ADMIN_USERNAME'],os.environ['DEV_ADMIN_PASSWORD'])
    p=client.call('/products?search=QA-PERSIST')['items']
    check('Committed Oracle records persist after backend restart',len(p)==1 and p[0]['quantity']==6 and p[0]['name']=='QA Product Updated')
    client.call('/auth/logout','POST',expected=204)

if __name__ == '__main__':
    target=Path('.tools/qa-http-restart.json' if '--restart-check' in sys.argv else '.tools/qa-http.json')
    try:
        restart_check() if '--restart-check' in sys.argv else exercise()
    finally:
        target.write_text(json.dumps({'passed':sum(x['passed'] for x in results),'failed':sum(not x['passed'] for x in results),'checks':results},indent=2),encoding='utf-8')
