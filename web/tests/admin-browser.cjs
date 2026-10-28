// Offline admin workflow against the production build; no real login or publication.
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict')
const puppeteer = require('puppeteer-core')
const dist = path.resolve(__dirname,'../dist')
const mime={'.js':'application/javascript','.css':'text/css','.html':'text/html','.png':'image/png','.svg':'image/svg+xml'}
const server=http.createServer((request,response)=>{
  const url=new URL(request.url,'http://localhost')
  const file=path.resolve(dist,url.pathname==='/admin'?'index.html':url.pathname.slice(1))
  if(!file.startsWith(dist+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){response.writeHead(404).end();return}
  response.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream'});response.end(fs.readFileSync(file))
})
async function main(){
  const executablePath=process.argv[2]||process.env.BROWSER_EXECUTABLE
  assert.ok(executablePath,'Supply a Chromium executable')
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
  const origin=`http://127.0.0.1:${server.address().port}`
  const browser=await puppeteer.launch({executablePath,headless:'new',args:['--no-proxy-server']})
  let stage='boot'
  try{
    const page=await browser.newPage(),errors=[],data=[],activity=[]
    let active=null,conflict=false
    page.setDefaultTimeout(15000)
    await page.setViewport({width:1440,height:1100});await page.setBypassServiceWorker(true)
    await page.evaluateOnNewDocument(()=>localStorage.setItem('locale','zh-CN'))
    page.on('pageerror',error=>errors.push(error.message))
    await page.setRequestInterception(true)
    page.on('request',request=>{
      const url=new URL(request.url())
      if(url.origin===origin){request.continue();return}
      if(!url.pathname.startsWith('/api/')){request.abort();return}
      const p=url.pathname.slice(4),method=request.method(),body=JSON.parse(request.postData()||'{}')
      const reply=(value,status=200,message='OK')=>request.respond({status,contentType:'application/json',headers:{'Access-Control-Allow-Origin':'*','Cache-Control':'no-store'},body:JSON.stringify({code:status===200?0:status,message,data:value})})
      if(method==='OPTIONS'){request.respond({status:204,headers:{'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization,content-type','Access-Control-Allow-Methods':'GET,POST,PUT,OPTIONS'}});return}
      if(p==='/auth/login'){reply({accessToken:body.account==='42'?'fixture-admin':'fixture-ordinary',refreshToken:'fixture-refresh'});return}
      if(request.headers().authorization!=='Bearer fixture-admin'){reply(null,403,'Forbidden');return}
      if(p==='/admin/me'){reply({userId:42});return}
      if(p==='/admin/releases'&&method==='GET'){reply({items:data.filter(r=>r.platform===url.searchParams.get('platform')),hasMore:false,active});return}
      if(p==='/admin/releases'&&method==='POST'){
        const row={...body,id:'fixture-release',state:'DRAFT',revision:1,createdBy:42,updatedBy:42,createdAt:'2026-10-28T10:00:00',updatedAt:'2026-10-28T10:00:00',publishedAt:null};data.push(row)
        activity.push({id:'a1',releaseId:row.id,actorId:42,action:'CREATE',revision:1,createdAt:row.createdAt});reply({...row});return
      }
      const row=data.find(r=>p.includes(r.id))
      if(row&&p.endsWith('/audits')){reply(activity);return}
      if(row&&method==='PUT'){
        if(conflict){conflict=false;reply(null,409,'版本已被修改，请刷新后重试');return}
        Object.assign(row,body,{revision:row.revision+1});reply({...row});return
      }
      if(row&&p.endsWith('/publish')){row.state='PUBLISHED';row.revision++;active={...row};activity.push({id:'a2',releaseId:row.id,actorId:42,action:'PUBLISH',revision:row.revision,createdAt:row.createdAt});reply({...row});return}
      if(row&&p.endsWith('/withdraw')){row.state='WITHDRAWN';row.revision++;active=null;reply({...row});return}
      reply(null,404,'Missing fixture')
    })
    const click=async text=>{await page.waitForFunction(text=>Array.from(document.querySelectorAll('button')).some(b=>b.textContent.trim()===text&&!b.disabled),{},text);await page.evaluate(text=>Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()===text&&!b.disabled).click(),text)}
    const type=async(selector,value)=>{await page.$eval(selector,node=>{node.value='';node.dispatchEvent(new Event('input',{bubbles:true}))});await page.type(selector,value)}
    await page.goto(`${origin}/admin`,{waitUntil:'networkidle0'})
    stage='ordinary access denied'
    await type('input[autocomplete=username]','7');await type('input[type=password]','fixture-password');await click('登录管理后台')
    await page.waitForFunction(()=>document.querySelector('[role=alert]')?.textContent.includes('没有管理权限'))
    assert.equal(await page.evaluate(()=>sessionStorage.getItem('charroom_admin_access')),null)
    stage='admin login'
    await type('input[autocomplete=username]','42');await type('input[type=password]','fixture-password');await click('登录管理后台')
    await page.waitForSelector('.admin-editor')
    stage='create draft'
    await type('.admin-fields label:first-child input','1.3.0');await type('.admin-fields label:nth-child(2) input','1.1.0');await type('input[type=url]','https://example.com/Qingliao.apk');await type('textarea','改进消息体验，修复历史记录问题。')
    await type('input[name=fileSize]','1024');await type('input[name=versionCode]','2');await type('input[name=sha256]','a'.repeat(64))
    await click('保存草稿');await page.waitForFunction(()=>document.querySelector('.admin-notice')?.textContent.includes('草稿已保存'))
    assert.equal(data.length,1);assert.equal(data[0].sha256,'a'.repeat(64));assert.equal(data[0].fileSize,1024)
    stage='edit conflict'
    conflict=true;await type('textarea','改进消息体验，修复历史记录问题。\n提升稳定性。');await click('保存草稿')
    await page.waitForFunction(()=>document.querySelector('[role=alert]')?.textContent.includes('版本已被修改'))
    await click('保存草稿');await page.waitForFunction(()=>document.querySelector('.admin-revision')?.textContent==='r2')
    stage='review and publish'
    await click('发布版本');await page.waitForSelector('[role=dialog]');await page.keyboard.press('Tab');await page.keyboard.press('Tab')
    assert.equal(await page.evaluate(()=>!!document.activeElement.closest('[role=dialog]')),true)
    await click('取消');assert.equal(data[0].state,'DRAFT')
    await click('发布版本');await click('确认操作');await page.waitForFunction(()=>document.querySelector('.admin-current strong')?.textContent==='v1.3.0')
    assert.equal(data[0].state,'PUBLISHED')
    assert.equal(await page.$eval('.admin-editor textarea',node=>node.matches(':disabled')),true)
    await page.waitForFunction(()=>document.querySelector('.admin-audit')?.textContent.includes('发布版本'))
    await page.screenshot({path:path.resolve(__dirname,'../../.gradle/admin-desktop.png'),fullPage:true})
    stage='dark and mobile'
    await page.evaluate(()=>document.documentElement.setAttribute('data-theme','dark'))
    assert.equal(await page.$eval('.admin-shell',node=>getComputedStyle(node).backgroundColor),'rgb(33, 29, 26)')
    await page.setViewport({width:390,height:844})
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true)
    await page.screenshot({path:path.resolve(__dirname,'../../.gradle/admin-mobile-dark.png'),fullPage:true})
    stage='withdraw'
    await click('撤回版本');await click('确认操作');await page.waitForFunction(()=>document.querySelector('.admin-current strong')?.textContent==='尚未发布')
    assert.equal(data[0].state,'WITHDRAWN')
    stage='logout'
    await click('退出管理');await page.waitForSelector('input[type=password]')
    assert.equal(await page.evaluate(()=>sessionStorage.getItem('charroom_admin_access')),null)
    assert.deepEqual(errors,[])
    console.log('PASS: admin access denial/login, draft creation/edit conflict, publish review, read-only publication, audit, withdrawal, logout, keyboard modal, dark and mobile layout.')
  }catch(error){throw new Error(`Admin browser stage: ${stage}`,{cause:error})}
  finally{await browser.close();await new Promise(resolve=>server.close(resolve))}
}
main().catch(error=>{console.error(error,error.cause);process.exitCode=1})
