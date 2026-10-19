export const money = value => new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:2}).format(Number(value || 0));
export const number = value => new Intl.NumberFormat('en-IN').format(Number(value || 0));
export const stockStatus = p => Number(p.quantity)===0 ? 'Out of stock' : Number(p.quantity)<=Number(p.reorderLevel) ? 'Low stock' : 'In stock';
export const localDate = (date=new Date()) => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
export function productPayload(form) {
  return {sku:form.sku.trim(),name:form.name.trim(),description:form.description||'',categoryId:Number(form.categoryId),supplierId:form.supplierId ? Number(form.supplierId):null,unitPrice:form.unitPrice,quantity:Number(form.quantity),reorderLevel:Number(form.reorderLevel)};
}
